package com.han1997.vipledger.ml.classifier.train

import kotlin.test.Test
import kotlin.test.assertEquals

class DeterministicRandomTest {

    @Test
    fun sameSeedProducesSamePermutation() {
        assertEquals(
            DeterministicRandom(42L).permutation(50).toList(),
            DeterministicRandom(42L).permutation(50).toList(),
        )
    }

    @Test
    fun permutationIsACompletePermutation() {
        val permuted = DeterministicRandom(7L).permutation(100)
        assertEquals((0 until 100).toList(), permuted.sorted())
    }
}
