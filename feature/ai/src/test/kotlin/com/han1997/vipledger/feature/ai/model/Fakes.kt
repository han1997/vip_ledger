package com.han1997.vipledger.feature.ai.model

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.MlTrainingSample
import com.han1997.vipledger.core.domain.repository.MlSampleRepository
import com.han1997.vipledger.core.model.MlSampleSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** 一条测试样本；默认未训练。 */
fun trainingSample(id: String, ledgerId: String = "l1", categoryId: String = "c1", text: String = "午饭 $id"): MlTrainingSample =
    MlTrainingSample(
        id = id,
        ledgerId = ledgerId,
        text = text,
        categoryId = categoryId,
        source = MlSampleSource.USER_CORRECTION,
        trainedVersion = null,
        createdAt = 0L,
    )

/** 生成 [count] 条未训练样本。 */
fun untrainedSamples(count: Int, ledgerId: String = "l1"): List<MlTrainingSample> =
    (1..count).map { trainingSample("s$it", ledgerId) }

/**
 * [SampleTrainer] 的计数假实现：只记录被调用的次数与样本数，
 * 让「开关关闭 / 低于阈值 ⇒ 训练一次都不发生」可以用计数断言（M7 验收）。
 */
class CountingSampleTrainer(private val version: String? = "v1-1000") : SampleTrainer {
    var calls: Int = 0
        private set
    var lastSampleCount: Int = 0
        private set

    override suspend fun trainAndMark(samples: List<MlTrainingSample>): String? {
        calls++
        lastSampleCount = samples.size
        return version
    }
}

/** 内存版 [MlSampleRepository]，用于触发器测试读 `listUntrained` / 断言 `markTrained`。 */
class FakeMlSampleRepository(samples: List<MlTrainingSample> = emptyList()) : MlSampleRepository {

    private val store = samples.toMutableList()

    val marked: MutableList<Triple<List<String>, String, Boolean>> = mutableListOf()

    override fun observeByLedger(ledgerId: String): Flow<List<MlTrainingSample>> =
        MutableStateFlow(store.filter { it.ledgerId == ledgerId })

    override suspend fun listByLedger(ledgerId: String): List<MlTrainingSample> =
        store.filter { it.ledgerId == ledgerId }

    override suspend fun listUntrained(ledgerId: String): List<MlTrainingSample> =
        listByLedger(ledgerId).filter { it.trainedVersion == null }

    override suspend fun insertAll(samples: List<MlTrainingSample>): AppResult<Unit> {
        store += samples
        return AppResult.success(Unit)
    }

    override suspend fun markTrained(ids: Collection<String>, version: String): AppResult<Unit> {
        marked += Triple(ids.toList(), version, true)
        store.replaceAll { if (it.id in ids) it.copy(trainedVersion = version) else it }
        return AppResult.success(Unit)
    }

    override suspend fun countByLedger(ledgerId: String): Int = listByLedger(ledgerId).size
}
