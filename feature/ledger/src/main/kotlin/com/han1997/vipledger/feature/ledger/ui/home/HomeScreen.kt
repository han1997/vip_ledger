package com.han1997.vipledger.feature.ledger.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.han1997.vipledger.core.domain.model.HomeCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.*
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.format.monthDayLabel
import com.han1997.vipledger.core.designsystem.format.relativeDayLabel
import com.han1997.vipledger.core.designsystem.theme.*
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.feature.ledger.R
import kotlinx.datetime.LocalDate

@Composable
fun HomeScreen(
    onAddTxn: (String) -> Unit,
    onOpenTxns: (String) -> Unit,
    onOpenLedgers: () -> Unit,
    onOpenAccounts: (String) -> Unit,
    onOpenLoanDetail: (String, String) -> Unit,
    onEditTxn: (String, String) -> Unit,
    onOpenBudget: (String) -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenRange: (String, DateRange) -> Unit,
    onOpenDrafts: (String) -> Unit,
    onOpenAuto: () -> Unit,
    onOpenSavings: (String) -> Unit,
    onHomeLayout: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state, viewModel::onAction, onAddTxn, onOpenTxns, onOpenLedgers, onOpenAccounts, onOpenLoanDetail, onEditTxn, onOpenBudget, onOpenSearch, onOpenRange, onOpenDrafts, onOpenAuto, onOpenSavings, onHomeLayout, modifier)
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onAddTxn: (String) -> Unit,
    onOpenTxns: (String) -> Unit,
    onOpenLedgers: () -> Unit,
    onOpenAccounts: (String) -> Unit,
    onOpenLoanDetail: (String, String) -> Unit,
    onEditTxn: (String, String) -> Unit,
    onOpenBudget: (String) -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenRange: (String, DateRange) -> Unit,
    onOpenDrafts: (String) -> Unit,
    onOpenAuto: () -> Unit,
    onOpenSavings: (String) -> Unit,
    onHomeLayout: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ledger = state.ledgerId
    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(title = stringResource(R.string.home_title), actions = {
                LedgerSwitcher(state, onAction, onOpenLedgers)
                IconButton(onClick = { ledger?.let(onOpenSearch) }, enabled = ledger != null) { Icon(Icons.Filled.Search, stringResource(R.string.ledger_home_search)) }
                IconButton(onClick = { ledger?.let(onHomeLayout) }, enabled = ledger != null) { Icon(Icons.Filled.Tune, stringResource(R.string.wealth_home_layout)) }
            })
        },
        floatingActionButton = {
            if (ledger != null) {
                VipLedgerFab(
                    onClick = { onAddTxn(ledger) },
                    contentDescription = stringResource(R.string.home_add_txn),
                )
            }
        },
    ) { padding ->
        val overview = state.overview
        when {
            state.failed -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { EmptyState(message = stringResource(R.string.ledger_load_failed), actionLabel = stringResource(R.string.ledger_retry), onAction = { onAction(HomeAction.Retry) }) }
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState() }
            ledger == null || overview == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { EmptyState(message = stringResource(R.string.home_no_ledger), actionLabel = stringResource(R.string.ledger_manage_add), onAction = onOpenLedgers) }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = VipLedgerTokens.Space4, end = VipLedgerTokens.Space4, top = VipLedgerTokens.Space2, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
            ) {
                item { HomeHero(overview, state.today, state.budget?.total?.fraction, onOpenAccounts = { onOpenAccounts(ledger) }) }
                item { SectionHeader(title = stringResource(R.string.home_overview_title), actionLabel = stringResource(R.string.wealth_home_layout), onActionClick = { onHomeLayout(ledger) }) }
                state.cards.filter { it.visible }.chunked(2).forEach { row ->
                    item(key = row.joinToString { it.card.name }) {
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
                            row.forEach { setting ->
                                key(setting.card) {
                                    val tileModifier = Modifier.weight(1f).fillMaxHeight()
                                    when (setting.card) {
                                        HomeCard.BUDGET -> MiniCard(stringResource(R.string.ledger_home_budget_entry), tileModifier, onClick = { onOpenBudget(ledger) }) {
                                            val budget = state.budget?.total
                                            if (budget != null) {
                                                MiniNumber(budget.remaining)
                                                Text(stringResource(R.string.ledger_home_budget_used, (budget.fraction * 100).toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                GoldProgressBar(budget.fraction)
                                            } else {
                                                Text(stringResource(R.string.ledger_budget_set_total), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                        HomeCard.TODAY -> MiniCard(stringResource(R.string.ledger_home_today), tileModifier, dotColor = ExpenseRed, onClick = {
                                            overview.lastSevenDays.lastOrNull()?.let { onOpenRange(ledger, DateRange(it.start, it.endInclusive)) }
                                        }) {
                                            MiniNumber(overview.todayExpense, color = ExpenseRed)
                                            Text(
                                                text = stringResource(R.string.ledger_home_today_sub, overview.todayIncome.toDisplayString()),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                        HomeCard.SAVINGS -> MiniCard(stringResource(R.string.wealth_savings), tileModifier, dotColor = vipLedgerAccentColors().accentAmber, onClick = { onOpenSavings(ledger) }) {
                                            val goal = state.savings.firstOrNull { !it.achieved } ?: state.savings.firstOrNull()
                                            if (goal == null) {
                                                Text(stringResource(R.string.wealth_goal_new), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                            } else {
                                                MiniNumber(goal.saved)
                                                Text(
                                                    text = stringResource(R.string.ledger_home_savings_sub, goal.goal.name, (goal.fraction * 100).toInt()),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                GoldProgressBar(goal.fraction)
                                            }
                                        }
                                        HomeCard.WEEK -> MiniCard(stringResource(R.string.ledger_home_week), tileModifier, dotColor = TransferBlue, onClick = {
                                            val days = overview.lastSevenDays
                                            if (days.isNotEmpty()) onOpenRange(ledger, DateRange(days.first().start, days.last().endInclusive))
                                        }) {
                                            SparkBars(overview.lastSevenDays.map { it.expense.cents })
                                        }
                                        HomeCard.PENDING -> MiniCard(stringResource(R.string.ledger_home_pending), tileModifier, dotColor = WarnGold, onClick = {                                            if (state.pendingAutoCount > 0) onOpenAuto() else onOpenDrafts(ledger)
                                        }) {
                                            MiniNumberText(stringResource(R.string.ledger_home_pending_count, state.pendingDraftCount))
                                            Text(
                                                text = stringResource(R.string.ledger_home_auto_count, state.pendingAutoCount),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (state.reminders.isNotEmpty()) {
                    item { SectionHeader(title = stringResource(R.string.home_pending_title), actionLabel = null, onActionClick = null) }
                    items(state.reminders.take(3), key = { it.planId ?: it.name }) { reminder ->
                        ReviewCard(
                            title = reminder.name,
                            subtitle = fullDateLabel(reminder.dueDate),
                            onClick = { reminder.planId?.let { onOpenLoanDetail(ledger, it) } ?: onOpenAccounts(ledger) },
                        )
                    }
                }
                item { SectionHeader(title = stringResource(R.string.home_recent_title), actionLabel = stringResource(R.string.home_see_all), onActionClick = { onOpenTxns(ledger) }) }
                if (overview.recentGroups.isEmpty()) item { EmptyState(stringResource(R.string.home_recent_empty)) }
                overview.recentGroups.forEach { group ->
                    item(key = "day-${group.date}") { DayHeader(date = group.date, expense = group.expense, today = state.today) }
                    items(group.txns, key = { it.id }) { txn ->
                        TxnLine(
                            txn = txn,
                            categoryName = txn.categoryId?.let(state.categoryNames::get),
                            accountName = state.accountNames[txn.accountId],
                            tagNames = txn.tagIds.mapNotNull(state.tagNames::get),
                            onClick = { onEditTxn(ledger, txn.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 首页 hero（原型 .hero）：年月导语（金色年份）+ 本月支出大额 + 预算胶囊 + 收入/结余分栏。 */
@Composable
private fun HomeHero(
    overview: com.han1997.vipledger.core.domain.model.HomeOverview,
    today: LocalDate?,
    usedFraction: Float?,
    onOpenAccounts: () -> Unit,
) {
    val colors = vipLedgerAccentColors()
    HeroCard(modifier = Modifier.clickable(role = Role.Button, onClick = onOpenAccounts)) {
        // 原型 .hero .lead：「2025 年 10 月 · 本月支出」，年份金色（--gold-2）。
        val lead = if (today != null) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.accentAmber, fontWeight = FontWeight.SemiBold)) {
                    append(today.year.toString())
                }
                append(stringResource(R.string.home_hero_lead_tail, today.monthNumber))
            }
        } else {
            AnnotatedString(stringResource(R.string.home_hero_lead))
        }
        Text(
            text = lead,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onHeroMuted,
        )
        Box(modifier = Modifier.padding(top = VipLedgerTokens.Space2)) {
            AmountText(overview.monthExpense, style = displayHero, color = colors.onHero)
        }
        if (usedFraction != null) {
            // 原型 .delta：胶囊底（象牙白 10%）+ 发丝描边（14%）。较上月对比暂无口径，先展示预算用量。
            Box(
                modifier = Modifier
                    .padding(top = VipLedgerTokens.Space3)
                    .clip(RoundedCornerShape(VipLedgerTokens.RadiusFull))
                    .background(colors.onHero.copy(alpha = 0.10f))
                    .border(
                        width = VipLedgerTokens.BorderThin,
                        color = colors.onHero.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(VipLedgerTokens.RadiusFull),
                    )
                    .padding(horizontal = 11.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_hero_budget_used, (usedFraction * 100).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onHeroMuted,
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(vertical = VipLedgerTokens.Space4)
                .fillMaxWidth()
                .height(VipLedgerTokens.BorderThin)
                .background(Brush.horizontalGradient(listOf(colors.accentAmber, colors.accentAmber.copy(alpha = 0.04f)))),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space6)) {
            HeroSplit(stringResource(R.string.ledger_home_income), overview.monthIncome, colors.onHero, colors.onHeroMuted, Modifier.weight(1f))
            HeroSplit(stringResource(R.string.home_month_balance), overview.monthBalance, colors.onHero, colors.onHeroMuted, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroSplit(label: String, amount: Money, onHero: androidx.compose.ui.graphics.Color, onHeroMuted: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space1)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = onHeroMuted)
        AmountText(amount = amount, style = displayAmount, color = onHero)
    }
}

/** 小卡片(原型 .mini):dot + 标签 + 衬线数值 + 说明 + 进度/柱。 */
@Composable
private fun MiniCard(
    title: String,
    modifier: Modifier = Modifier,
    dotColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VipLedgerTokens.RadiusCard),
        border = BorderStroke(VipLedgerTokens.BorderThin, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(VipLedgerTokens.Space3).heightIn(min = 108.dp),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space1),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(modifier = Modifier.size(7.dp).background(dotColor, RoundedCornerShape(2.dp)))
                Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(VipLedgerTokens.Space2))
            content()
        }
    }
}

@Composable
private fun MiniNumber(amount: Money, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    AmountText(amount = amount, style = displayAmount, color = color)
}

@Composable
private fun MiniNumberText(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

/** 金绿渐变进度条(原型 .mini .bar:墨绿→金)。 */
@Composable
private fun GoldProgressBar(fraction: Float) {
    val colors = vipLedgerAccentColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(VipLedgerTokens.RadiusFull)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, colors.accentAmber)),
                    RoundedCornerShape(VipLedgerTokens.RadiusFull),
                ),
        )
    }
}

/** 七日趋势柱(原型 .mini .spark)。 */
@Composable
private fun SparkBars(values: List<Long>) {
    if (values.isEmpty()) return
    val max = values.max().coerceAtLeast(1L)
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            val isLast = index == values.lastIndex
            val isSecondLast = index == values.lastIndex - 1
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight((value.toFloat() / max).coerceIn(0.12f, 1f))
                    .background(
                        when {
                            isLast -> MaterialTheme.colorScheme.primary
                            isSecondLast -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.outline
                        },
                        RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                    ),
            )
        }
    }
}

/** 待确认/提醒卡(原型 .review:金底 + 金色左边线)。 */
@Composable
private fun ReviewCard(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(VipLedgerTokens.BorderThin, MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** 日期分组头（原型 .daygroup .dh）：衬线墨绿日期 + 今天/昨天/周X + 右侧当日支出。 */
@Composable
private fun DayHeader(date: LocalDate, expense: Money, today: LocalDate?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VipLedgerTokens.Space1, vertical = VipLedgerTokens.Space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = monthDayLabel(date),
                style = serifTitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = relativeDayLabel(date, today),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(R.string.home_day_expense, expense.toDisplayString()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LedgerSwitcher(state: HomeUiState, onAction: (HomeAction) -> Unit, onOpenLedgers: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(state.ledgerName.ifBlank { stringResource(R.string.home_switch_ledger) })
            Icon(Icons.Filled.KeyboardArrowDown, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            state.ledgers.forEach { ledger -> DropdownMenuItem(text = { Text(ledger.name) }, onClick = { expanded = false; onAction(HomeAction.SelectLedger(ledger.id)) }) }
            DropdownMenuItem(text = { Text(stringResource(R.string.ledger_manage_title)) }, onClick = { expanded = false; onOpenLedgers() })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() { VipLedgerTheme { HomeContent(HomeUiState(isLoading = false), {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, { _, _ -> }, {}, {}, {}, {}) } }