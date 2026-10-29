package com.andone.memorip.presentation.modeltest

import com.andone.memorip.presentation.util.splitSentences
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class SplitSentencesTest {

    @Test
    fun `마침표 느낌표 물음표로 문장을 나눈다`() {
        assertEquals(listOf("안녕", "반가워", "잘가"), splitSentences("안녕. 반가워! 잘가?"))
    }

    @Test
    fun `줄바꿈과 전각 구두점으로도 나눈다`() {
        assertEquals(listOf("첫째", "둘째", "셋째"), splitSentences("첫째\n둘째. 셋째 ! "))
    }

    @Test
    fun `각 문장의 앞뒤 공백은 제거한다`() {
        assertEquals(listOf("가", "나"), splitSentences("  가  .  나  "))
    }

    @Test
    fun `연속된 구두점이 만드는 빈 문장은 버린다`() {
        assertEquals(listOf("아", "네"), splitSentences("아!!! 네"))
    }

    @Test
    fun `구두점이 없음녀 문장 하나다`() {
        assertEquals(listOf("제목만 있음"), splitSentences("제목만 있음"))
    }

    @Test
    fun `빈 문자열이나 공백뿐인 문자열은 빈 목록이다`() {
        assertTrue(splitSentences("").isEmpty())
        assertTrue(splitSentences("    ").isEmpty())
    }

}