package com.andone.memorip.domain.model

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class TimeBlockTest {
    private fun block(id: String, start: Int, duration: Int = 60, day: Int = 1, column: Int = 0) =
        TimeBlock(id = id, startMinute = start, durationMinute = duration, day = day, column = column)

    @Test(expected = IllegalArgumentException::class)
    fun `소요 시간이 0이면 생성할 수 없다`() {
        block("a", start = 0, duration = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `소요 시간이 음수이면 생성할 수 없다`() {
        block("a", start = 0, duration = -10)
    }

    @Test
    fun `endMinute은 시작 분에 소요 시간을 더한 값이다`() {
        assertEquals(90, block("a", start = 30, duration = 60).endMinute)
    }

    @Test
    fun `다른 블록이 없으면 이동할 수 있다`() {
        val a = block("a", start = 0)

        assertTrue(a.canMoveTo(newStartMinute = 100, blocks = listOf(a), totalMinutes = 1440))
    }

    @Test
    fun `같은 날 같은 열의 다른 블록과 겹치면 이동할 수 없다`() {
        val a = block("a", start = 0)
        val b = block("b", start = 120)

        assertFalse(a.canMoveTo(100, listOf(a, b), 1440))
    }

    @Test
    fun `다른 블록의 끝과 맞닿는 위치는 겹치지 않는다`() {
        val a = block("a", start = 0)
        val b = block("b", start = 120)

        assertTrue(a.canMoveTo(60, listOf(a, b), 1440))
    }

    @Test
    fun `다른 날이나 다른 열의 블록과는 겹쳐도 상관없다`() {
        val a = block("a", start = 0)
        val otherDay = block("b", start = 100, day = 2)
        val otherColumn = block("c", start = 100, column = 1)

        assertTrue(a.canMoveTo(100, listOf(a, otherDay, otherColumn), 1440))
    }

    @Test
    fun `범위를 넘는 위치는 마지막 가능한 위치로 보정해서 겹침을 판단한다`() {
        val a = block("a", start = 0, duration = 60)
        val last = block("last", start = 1380, duration = 60)

        assertFalse(a.canMoveTo(5000, listOf(a, last), totalMinutes = 1440))
    }

    @Test
    fun `이동하면 시작 분이 바뀌고 id와 열은 유지된다`() {
        val moved = block("a", start = 0, column = 2).movedTo(newStartMinute = 300, totalMinutes = 1440)

        assertEquals(300, moved.startMinute)
        assertEquals(1, moved.day)
        assertEquals("a", moved.id)
        assertEquals(2, moved.column)
    }

    @Test
    fun `하루를 넘는 위치로 이동하면 날짜가 바뀌고 시작 분은 그날 0시 기준이다`() {
        val moved = block("a", start = 0).movedTo(newStartMinute = 1500, totalMinutes = 1440 * 3)

        assertEquals(2, moved.day)
        assertEquals(60, moved.startMinute)
    }

    @Test
    fun `음수 위치로 이동하면 0으로 보정된다`() {
        val moved = block("a", start = 100).movedTo(newStartMinute = -50, totalMinutes = 1440)

        assertEquals(1, moved.day)
        assertEquals(0, moved.startMinute)
    }

    @Test
    fun `범위를 넘는 위치로 이동하면 마지막 가능한 위치로 보정된다`() {
        val moved = block("a", start = 0, duration = 60).movedTo(newStartMinute = 2000, totalMinutes = 1440)

        assertEquals(1, moved.day)
        assertEquals(1380, moved.startMinute)
    }
}