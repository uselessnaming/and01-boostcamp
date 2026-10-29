package com.andone.memorip.data.ai

import BertTokenizer
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BertTokenizerTest {

    private val vocab = mapOf(
        "[PAD]" to 0, "[UNK]" to 1, "[CLS]" to 2, "[SEP]" to 3,
        "hello" to 4, "world" to 5, "play" to 6, "##ing" to 7
    )

    private fun tokenizer(maxLen: Int = 8) = BertTokenizer(vocab, maxLen)

    @Test
    fun `CLS와 SEP로 감싸고 나머지는 PAD로 채운다`() {
        val encoded = tokenizer().encode("hello world")

        assertArrayEquals(intArrayOf(2, 4, 5, 3, 0, 0, 0, 0), encoded.inputIds)
        assertArrayEquals(intArrayOf(1, 1, 1, 1, 0, 0, 0, 0), encoded.attentionMask)
        assertArrayEquals(IntArray(8), encoded.tokenTypeIds)
    }

    @Test
    fun `사전에 없는 단어는 wordpiece로 쪼갠다`() {
        val encoded = tokenizer().encode("playing")

        assertArrayEquals(intArrayOf(2, 6, 7, 3, 0, 0, 0, 0), encoded.inputIds)
    }

    @Test
    fun `쪼갤 수 없는 단어는 UNK이다`() {
        val encoded = tokenizer().encode("hello xyz")

        assertArrayEquals(intArrayOf(2, 4, 1, 3, 0, 0, 0, 0), encoded.inputIds)
    }

    @Test
    fun `앞뒤 공백과 연속 공백은 무시한다`() {
        val encoded = tokenizer().encode("  hello  world  ")

        assertArrayEquals(intArrayOf(2, 4, 5, 3, 0, 0, 0, 0), encoded.inputIds)
    }

    @Test
    fun `빈 문자열은 CLS와 SEP만 남는다`() {
        val encoded = tokenizer().encode("")

        assertArrayEquals(intArrayOf(2, 3, 0, 0, 0, 0, 0, 0), encoded.inputIds)
        assertArrayEquals(intArrayOf(1, 1, 0, 0, 0, 0, 0, 0), encoded.attentionMask)
    }

    @Test
    fun `최대 길이를 넘으면 잘라내고 길이는 maxLen으로 유지한다`() {
        val encoded = tokenizer(maxLen = 3).encode("hello world hello")

        assertArrayEquals(intArrayOf(2, 4, 5), encoded.inputIds)
        assertArrayEquals(intArrayOf(1, 1, 1), encoded.attentionMask)
    }
}