package com.han1997.vipledger.core.data.importexport.yimu

import androidx.room.withTransaction
import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.dao.AccountDao
import com.han1997.vipledger.core.database.dao.CategoryDao
import com.han1997.vipledger.core.database.dao.TagDao
import com.han1997.vipledger.core.database.dao.TxnDao
import com.han1997.vipledger.core.database.entity.AccountEntity
import com.han1997.vipledger.core.database.entity.CategoryEntity
import com.han1997.vipledger.core.database.entity.TagEntity
import com.han1997.vipledger.core.database.entity.TxnEntity
import com.han1997.vipledger.core.database.entity.TxnTagEntity
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.BuiltinCategorySource
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.usecase.importbill.BillImportPreview
import com.han1997.vipledger.core.domain.usecase.importbill.BillImportReport
import com.han1997.vipledger.core.domain.usecase.importbill.BillImportRow
import com.han1997.vipledger.core.domain.usecase.importbill.BillImporter
import com.han1997.vipledger.core.domain.usecase.importbill.ImportTarget
import com.han1997.vipledger.core.domain.usecase.importbill.PendingBillAccount
import com.han1997.vipledger.core.domain.usecase.importbill.PendingBillCategory
import com.han1997.vipledger.core.domain.usecase.ledger.CreateLedgerUseCase
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource
import java.math.BigDecimal
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * 一木记账账单 `.xls` / `.zip` 的 [BillImporter] 实现(`.trellis/spec/android/import-export.md` 一木一节)。
 *
 * 接口在 `:core:domain`(`BillImporter`),本类是 `:core:data` 侧实现,Hilt 绑定留在本模块。
 * 源文件 23 列 / 表头列名 → 领域预览模型([BillImportPreview] / [BillImportRow])的映射全部吸收在这里;
 * 上层(含 `feature:*`)只见领域模型,不感知 xls 列。
 *
 * - [inspect] 只解析 + 映射(读目标账本已有分类 / 账户判重),不落库;
 * - [commit] 分类 / 账户 / 标签 / 流水在**同一个** `database.withTransaction {}` 内写入,
 *   任意一行失败整体回滚,最后 `balanceSyncer.syncBalances` 收全所有被写入行涉及的 accountId。
 *
 * 关键口径(见 prd.md R4–R8 与三个陷阱):
 * - 目标 kind **只由「收支类型」列决定**(陷阱 1),不从一级类别推方向;
 * - 退款 / 优惠列**不入金额**,只拼进 remark(陷阱 2、3);金额符号由「收支类型」决定,取绝对值;
 * - 0 元行照常写 0,直接构造 [TxnEntity] + [TxnDao.upsert],**不走** `TxnValidator`(它要求金额 > 0);
 * - importHash = `sha1("${occurredAt / 60_000}|$amountCents|$accountId|$title")`,分钟粒度;
 *   文件内第 2 次出现的同键加 `|#n` 后缀(D7),既全量保留又保证重复导入不产生双份。
 */
@Singleton
@OptIn(ExperimentalTime::class)
class YimuLedgerImporter @Inject constructor(
    private val database: LedgerDatabase,
    private val txnDao: TxnDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
    private val balanceSyncer: AccountBalanceSyncer,
    private val createLedger: CreateLedgerUseCase,
    private val builtinCategorySource: BuiltinCategorySource,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) : BillImporter {

    override suspend fun inspect(source: ByteArray, target: ImportTarget): AppResult<BillImportPreview> =
        dataResult { buildPreview(source, target) }

    override suspend fun commit(
        preview: BillImportPreview,
        target: ImportTarget,
        onProgress: (done: Int, total: Int) -> Unit,
    ): AppResult<BillImportReport> {
        val ledgerId = when (val resolved = ensureLedger(target)) {
            is AppResult.Success -> resolved.value
            is AppResult.Failure -> return resolved
        }
        return dataResult { persist(preview, ledgerId, onProgress) }
    }

    // ---- inspect:解析 + 映射预览 ----

    /** 列名 → 列下标;按表头动态定位,避免源文件列序漂移。 */
    private fun headerIndex(header: List<BiffCell?>): Map<String, Int> {
        val result = HashMap<String, Int>()
        header.forEachIndexed { index, cell ->
            val name = stringValue(cell).trim()
            if (name.isNotEmpty()) result.putIfAbsent(name, index)
        }
        return result
    }

    private suspend fun buildPreview(source: ByteArray, target: ImportTarget): BillImportPreview {
        val sheet = YimuBillParser.parse(source)
        val columns = headerIndex(sheet.rows.firstOrNull().orEmpty())
        val dateCol = columns.required("日期")
        val typeCol = columns.required("收支类型")
        val amountCol = columns.required("金额")
        val primaryCol = columns.required("类别")
        val secondaryCol = columns["二级分类"]
        val accountCol = columns["账户"]
        val refundCol = columns["退款"]
        val discountCol = columns["优惠"]
        val remarkCol = columns["备注"]
        val tagCol = columns["标签"]
        val addressCol = columns["地址"]
        val otherCol = columns["其他"]

        val zone = timeProvider.zone
        val rows = ArrayList<BillImportRow>()
        val skipped = ArrayList<Int>()
        val failed = ArrayList<Int>()
        var expenseCents = 0L
        var incomeCents = 0L
        var startAt: Long? = null
        var endAt: Long? = null

        sheet.rows.drop(1).forEachIndexed { index, raw ->
            val sheetRow = index + 2
            if (raw.all { it == null || it == BiffCell.Blank }) {
                skipped += sheetRow
                return@forEachIndexed
            }
            val parsed = runCatching {
                parseRow(
                    raw = raw,
                    sheetRow = sheetRow,
                    setup = ColumnSetup(
                        dateCol = dateCol,
                        typeCol = typeCol,
                        amountCol = amountCol,
                        primaryCol = primaryCol,
                        secondaryCol = secondaryCol,
                        accountCol = accountCol,
                        refundCol = refundCol,
                        discountCol = discountCol,
                        remarkCol = remarkCol,
                        tagCol = tagCol,
                        addressCol = addressCol,
                        otherCol = otherCol,
                    ),
                    zone = zone,
                )
            }.getOrNull()
            if (parsed == null) {
                failed += sheetRow
                return@forEachIndexed
            }
            rows += parsed
            when (parsed.type) {
                TransactionType.EXPENSE -> expenseCents += parsed.amountCents
                TransactionType.INCOME -> incomeCents += parsed.amountCents
                else -> Unit
            }
            if (startAt == null || parsed.occurredAt < startAt) startAt = parsed.occurredAt
            if (endAt == null || parsed.occurredAt > endAt) endAt = parsed.occurredAt
        }

        val (newCategories, newAccounts) = pendingAdditions(rows, target)
        return BillImportPreview(
            dataRowCount = rows.size,
            startAt = startAt,
            endAt = endAt,
            expenseCents = expenseCents,
            incomeCents = incomeCents,
            skippedRows = skipped,
            failedRows = failed,
            newCategories = newCategories,
            newAccounts = newAccounts,
            rows = rows,
        )
    }

    private data class ColumnSetup(
        val dateCol: Int,
        val typeCol: Int,
        val amountCol: Int,
        val primaryCol: Int,
        val secondaryCol: Int?,
        val accountCol: Int?,
        val refundCol: Int?,
        val discountCol: Int?,
        val remarkCol: Int?,
        val tagCol: Int?,
        val addressCol: Int?,
        val otherCol: Int?,
    )

    private fun parseRow(raw: List<BiffCell?>, sheetRow: Int, setup: ColumnSetup, zone: TimeZone): BillImportRow {
        val occurredAt = parseOccurredAt(stringValue(raw.getOrNull(setup.dateCol)).trim(), zone)
            ?: throw IllegalArgumentException("Invalid date")

        val type = when (stringValue(raw.getOrNull(setup.typeCol)).trim()) {
            "支出" -> TransactionType.EXPENSE
            "收入" -> TransactionType.INCOME
            else -> throw IllegalArgumentException("Unknown 收支类型")
        }

        val amount = numericValue(raw.getOrNull(setup.amountCol))
            ?: throw IllegalArgumentException("Invalid amount")
        // 符号由「收支类型」决定 → 一律取绝对值;0 元行原样写 0(D6)。
        val amountCents = BigDecimal.valueOf(amount).abs().movePointRight(2).longValueExact()

        val primary = stringValue(raw.getOrNull(setup.primaryCol)).trim()
        val secondary = setup.secondaryCol?.let { stringValue(raw.getOrNull(it)).trim() }.orEmpty()
        val category = YimuCategoryMapper.map(primary, secondary, type)

        val accountName = setup.accountCol?.let { stringValue(raw.getOrNull(it)).trim() }.orEmpty()
            .ifEmpty { YimuAccountMapper.DEFAULT_ACCOUNT_NAME }

        val title = setup.remarkCol?.let { stringValue(raw.getOrNull(it)) }.orEmpty()

        val refund = setup.refundCol?.let { raw.getOrNull(it) }
        val discount = setup.discountCol?.let { raw.getOrNull(it) }
        val address = setup.addressCol?.let { stringValue(raw.getOrNull(it)).trim() }.orEmpty()
        val other = setup.otherCol?.let { stringValue(raw.getOrNull(it)).trim() }.orEmpty()

        return BillImportRow(
            sourceRow = sheetRow,
            occurredAt = occurredAt,
            type = type,
            amountCents = amountCents,
            categoryParentName = category.parentName,
            categoryChildName = category.childName,
            accountName = accountName,
            title = title,
            remark = buildRemark(refund, discount, address, other),
            excludeFromStats = other.contains("不计入收支"),
            tagName = setup.tagCol?.let { stringValue(raw.getOrNull(it)).trim() }.orEmpty().ifEmpty { null },
        )
    }

    /** `退款 29.17 · 优惠 0.02 · 地址:...`;全空则 `""`。退款 / 优惠只入备注、不入金额(D5)。 */
    private fun buildRemark(refund: BiffCell?, discount: BiffCell?, address: String, other: String): String {
        val parts = ArrayList<String>(4)
        moneyDisplay(refund)?.let { parts += "退款 $it" }
        moneyDisplay(discount)?.let { parts += "优惠 $it" }
        if (address.isNotEmpty()) parts += "地址:$address"
        if (other.isNotEmpty()) parts += other
        return parts.joinToString(" · ")
    }

    /** 退款 / 优惠单元格展示:Number 去尾零、Text 原样;空白与 0 视为无值。 */
    private fun moneyDisplay(cell: BiffCell?): String? = when (cell) {
        is BiffCell.Number ->
            if (cell.value == 0.0) null else BigDecimal.valueOf(cell.value).stripTrailingZeros().toPlainString()
        is BiffCell.Text -> cell.value.trim().ifEmpty { null }
        else -> null
    }

    private fun parseOccurredAt(raw: String, zone: TimeZone): Long? {
        if (raw.isEmpty()) return null
        return runCatching { LocalDateTime.parse(raw.replace(' ', 'T')) }
            .getOrNull()
            ?.toInstant(zone)
            ?.toEpochMilliseconds()
    }

    // ---- inspect:将新建的分类 / 账户 ----

    private suspend fun pendingAdditions(
        rows: List<BillImportRow>,
        target: ImportTarget,
    ): Pair<List<PendingBillCategory>, List<PendingBillAccount>> {
        val existingCategories = existingCategoryKeys(target)
        val existingAccounts = existingAccountNames(target)

        val newCategories = LinkedHashMap<Pair<String, String>, PendingBillCategory>()
        val newAccounts = LinkedHashMap<String, PendingBillAccount>()
        rows.forEach { row ->
            val child = row.categoryChildName
            if (child != null && (row.categoryParentName to child) !in existingCategories) {
                newCategories.putIfAbsent(
                    row.categoryParentName to child,
                    PendingBillCategory(parentName = row.categoryParentName, name = child, kind = kindOf(row.type)),
                )
            }
            val norm = YimuAccountMapper.normalize(row.accountName)
            if (norm !in existingAccounts) {
                newAccounts.putIfAbsent(norm, PendingBillAccount(row.accountName.trim(), YimuAccountMapper.inferType(norm)))
            }
        }
        return newCategories.values.toList() to newAccounts.values.toList()
    }

    /** 目标账本已有的 `(父名 | null, 名称)` 集合。新建账本按内置树预判,已有账本读库。 */
    private suspend fun existingCategoryKeys(target: ImportTarget): Set<Pair<String?, String>> {
        val ledgerId = target.ledgerId
        if (ledgerId == null) {
            val result = HashSet<Pair<String?, String>>()
            builtinCategorySource.categories().forEach { parent ->
                result += null to parent.name
                parent.children.forEach { child -> result += parent.name to child.name }
            }
            return result
        }
        val all = categoryDao.listByLedger(ledgerId, includeArchived = true)
        val nameById = all.associate { it.id to it.name }
        return all.mapNotNull { category ->
            if (category.parentId == null) {
                null to category.name
            } else {
                (nameById[category.parentId] ?: return@mapNotNull null) to category.name
            }
        }.toSet()
    }

    private suspend fun existingAccountNames(target: ImportTarget): Set<String> {
        val ledgerId = target.ledgerId
            ?: return setOf(YimuAccountMapper.normalize(YimuAccountMapper.DEFAULT_ACCOUNT_NAME))
        return accountDao.listByLedger(ledgerId, includeArchived = true)
            .map { YimuAccountMapper.normalize(it.name) }
            .toSet()
    }

    // ---- commit:落库 ----

    private suspend fun ensureLedger(target: ImportTarget): AppResult<String> {
        target.ledgerId?.let { return AppResult.success(it) }
        val name = target.createLedgerNamed
            ?: return AppResult.failure(AppError.Validation("导入落点缺失"))
        return when (val created = createLedger(name)) {
            is AppResult.Success -> AppResult.success(created.value.id)
            is AppResult.Failure -> created
        }
    }

    private suspend fun persist(
        preview: BillImportPreview,
        ledgerId: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): BillImportReport {
        var inserted = 0
        var duplicates = 0
        var newCategoryCount = 0
        var newAccountCount = 0
        val changedAccounts = LinkedHashSet<String>()

        database.withTransaction {
            val now = timeProvider.nowMillis()

            // ---- 账户:归一化名先匹配已有(不改名),未命中才新建;initialBalance 恒 0 ----
            val existingAccounts = accountDao.listByLedger(ledgerId, includeArchived = true)
            val accountIdByNorm = HashMap<String, String>()
            existingAccounts.forEach { accountIdByNorm[YimuAccountMapper.normalize(it.name)] = it.id }
            var accountSort = existingAccounts.maxOfOrNull { it.sortOrder } ?: -1
            val neededAccounts = LinkedHashMap<String, String>()
            preview.rows.forEach { row ->
                val norm = YimuAccountMapper.normalize(row.accountName)
                neededAccounts.putIfAbsent(norm, row.accountName.trim())
            }
            neededAccounts.forEach { (norm, originalName) ->
                if (norm !in accountIdByNorm) {
                    val entity = AccountEntity(
                        id = idGenerator.newId(),
                        ledgerId = ledgerId,
                        name = originalName,
                        type = YimuAccountMapper.inferType(norm),
                        initialBalanceCents = 0L,
                        currentBalanceCents = 0L,
                        sortOrder = ++accountSort,
                        createdAt = now,
                        updatedAt = now,
                    )
                    accountDao.upsert(entity)
                    accountIdByNorm[norm] = entity.id
                    newAccountCount++
                }
            }

            // ---- 分类:按 (parentId, kind, name) 查重,命中复用,缺失新建 ----
            val existingCategories = categoryDao.listByLedger(ledgerId, includeArchived = true)
            val parentIdByNameKind = HashMap<Pair<String, CategoryKind>, String>()
            val childIdByParentName = HashMap<Pair<String, String>, String>()
            val childSortByParentId = HashMap<String, Int>()
            var topSort = -1
            existingCategories.forEach { category ->
                val parentId = category.parentId
                if (parentId == null) {
                    parentIdByNameKind[category.name to category.kind] = category.id
                    if (category.sortOrder > topSort) topSort = category.sortOrder
                } else {
                    childIdByParentName[parentId to category.name] = category.id
                    if (category.sortOrder > (childSortByParentId[parentId] ?: -1)) {
                        childSortByParentId[parentId] = category.sortOrder
                    }
                }
            }

            val categoryIdByKey = HashMap<Triple<String, String?, CategoryKind>, String>()
            preview.rows.forEach { row ->
                val kind = kindOf(row.type)
                val parentName = row.categoryParentName
                val childName = row.categoryChildName
                val key = Triple(parentName, childName, kind)
                if (key in categoryIdByKey) return@forEach
                val parentId = parentIdByNameKind.getOrPut(parentName to kind) {
                    val entity = CategoryEntity(
                        id = idGenerator.newId(),
                        ledgerId = ledgerId,
                        parentId = null,
                        name = parentName,
                        kind = kind,
                        iconKey = null,
                        sortOrder = ++topSort,
                        isSystem = false,
                        createdAt = now,
                        updatedAt = now,
                    )
                    categoryDao.upsert(entity)
                    newCategoryCount++
                    entity.id
                }
                val targetId = if (childName == null) {
                    parentId
                } else {
                    childIdByParentName.getOrPut(parentId to childName) {
                        val nextSort = (childSortByParentId[parentId] ?: -1) + 1
                        childSortByParentId[parentId] = nextSort
                        val entity = CategoryEntity(
                            id = idGenerator.newId(),
                            ledgerId = ledgerId,
                            parentId = parentId,
                            name = childName,
                            kind = kind,
                            iconKey = null,
                            sortOrder = nextSort,
                            isSystem = false,
                            createdAt = now,
                            updatedAt = now,
                        )
                        categoryDao.upsert(entity)
                        newCategoryCount++
                        entity.id
                    }
                }
                categoryIdByKey[key] = targetId
            }

            // ---- 标签:按名复用,缺失新建 ----
            val tagIdByName = HashMap<String, String>()
            tagDao.listByLedger(ledgerId).forEach { tagIdByName[it.name] = it.id }
            var tagSort = tagIdByName.size
            preview.rows.forEach { row ->
                val name = row.tagName ?: return@forEach
                if (name !in tagIdByName) {
                    val entity = TagEntity(
                        id = idGenerator.newId(),
                        ledgerId = ledgerId,
                        name = name,
                        sortOrder = tagSort++,
                        createdAt = now,
                        updatedAt = now,
                    )
                    tagDao.upsert(entity)
                    tagIdByName[name] = entity.id
                }
            }

            // ---- 流水:逐行 upsert;import_hash 全库判重(D7 后缀保证文件内重复不撞唯一索引) ----
            val occurrences = HashMap<String, Int>()
            preview.rows.forEachIndexed { index, row ->
                val accountId = accountIdByNorm.getValue(YimuAccountMapper.normalize(row.accountName))
                val kind = kindOf(row.type)
                val categoryId = categoryIdByKey.getValue(
                    Triple(row.categoryParentName, row.categoryChildName, kind),
                )

                val key = "${row.occurredAt / 60_000}|${row.amountCents}|$accountId|${row.title}"
                val seen = (occurrences[key] ?: 0) + 1
                occurrences[key] = seen
                val hash = sha1(if (seen >= 2) "$key|#$seen" else key)

                if (txnDao.existsImportHash(hash)) {
                    duplicates++
                } else {
                    val txnId = idGenerator.newId()
                    txnDao.upsert(
                        TxnEntity(
                            id = txnId,
                            ledgerId = ledgerId,
                            type = row.type,
                            amountCents = row.amountCents,
                            accountId = accountId,
                            toAccountId = null,
                            categoryId = categoryId,
                            occurredAt = row.occurredAt,
                            title = row.title,
                            remark = row.remark,
                            source = TxnSource.IMPORT,
                            draftStatus = DraftStatus.CONFIRMED,
                            excludeFromStats = row.excludeFromStats,
                            importHash = hash,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null,
                        ),
                    )
                    inserted++
                    changedAccounts += accountId
                    row.tagName?.let { name ->
                        tagIdByName[name]?.let { tagId ->
                            txnDao.insertTagLinks(listOf(TxnTagEntity(txnId = txnId, tagId = tagId)))
                        }
                    }
                }
                onProgress(index + 1, preview.rows.size)
            }

            balanceSyncer.syncBalances(changedAccounts, now)
        }

        return BillImportReport(
            ledgerId = ledgerId,
            inserted = inserted,
            duplicates = duplicates,
            failedRows = preview.failedRows,
            skippedRows = preview.skippedRows,
            newCategoryCount = newCategoryCount,
            newAccountCount = newAccountCount,
        )
    }

    // ---- 工具 ----

    private fun kindOf(type: TransactionType): CategoryKind = when (type) {
        TransactionType.EXPENSE, TransactionType.REFUND -> CategoryKind.EXPENSE
        TransactionType.INCOME -> CategoryKind.INCOME
        else -> CategoryKind.TRANSFER
    }

    private fun Map<String, Int>.required(name: String): Int =
        this[name] ?: throw YimuFormatException("缺少「$name」列")

    private fun stringValue(cell: BiffCell?): String = when (cell) {
        is BiffCell.Text -> cell.value
        is BiffCell.Number -> BigDecimal.valueOf(cell.value).stripTrailingZeros().toPlainString()
        else -> ""
    }

    private fun numericValue(cell: BiffCell?): Double? = when (cell) {
        is BiffCell.Number -> cell.value
        is BiffCell.Text ->
            cell.value.trim().replace(",", "").ifEmpty { null }
                ?.let { runCatching { BigDecimal(it).toDouble() }.getOrNull() }
        else -> null
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}