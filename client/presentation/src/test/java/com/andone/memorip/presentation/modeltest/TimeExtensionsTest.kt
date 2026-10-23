package com.andone.memorip.presentation.modeltest

import com.andone.memorip.presentation.util.getDayDiff
import com.andone.memorip.presentation.util.getHourDiff
import com.andone.memorip.presentation.util.getMinuteDiff
import com.andone.memorip.presentation.util.millisToLocalDate
import com.andone.memorip.presentation.util.toRemoteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TimeExtensionsTest {

    @Test
    fun `LocalDateTime을 서버 전송용 문자열로 바꾼다`() {
        assertEquals("2026-01-01T09:00", LocalDateTime.of(2026, 1, 1, 9, 0).toRemoteString())
        assertEquals("2026-01-01T09:00:30", LocalDateTime.of(2026, 1, 1, 9, 0, 30).toRemoteString())
    }

    @Test
    fun `null LocalDateTime은 null로 바뀐다`() {
        val none: LocalDateTime? = null

        assertNull(none.toRemoteString())
    }

    @Test
    fun `LocalDate를 서버 전송용 문자열로 바꾼다`() {
        assertEquals("2026-01-01", LocalDate.of(2026, 1, 1).toRemoteString())
    }

    @Test
    fun `null LocalDate는 null로 바뀐다`() {
        val none: LocalDate? = null

        assertNull(none.toRemoteString())
    }

    @Test
    fun `밀리초를 기기 시간대 기준 날짜로 변환한다`() {
        val date = LocalDate.of(2026, 3, 1)
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertEquals(date, millisToLocalDate(millis))
    }

    @Test
    fun `시간 차이는 절대값으로 계산한다`() {
        val a = LocalDateTime.of(2026, 1, 1, 9, 0)
        val b = LocalDateTime.of(2026, 1, 1, 12, 30)

        assertEquals(3L, getHourDiff(a, b))
        assertEquals(3L, getHourDiff(b, a))
    }

    @Test
    fun `일 차이를 계산하고 방향에 따라 부호가 바뀐다`() {
        val a = LocalDate.of(2026, 1, 1)
        val b = LocalDate.of(2026, 1, 4)

        assertEquals(3, getDayDiff(a, b))
        assertEquals(-3, getDayDiff(b, a))
        assertEquals(0, getDayDiff(a, a))
    }

    @Test
    fun `분 차이를 계산한다`() {
        val a = LocalDateTime.of(2026, 1, 1, 9, 0)
        val b = LocalDateTime.of(2026, 1, 1, 10, 30)

        assertEquals(90, getMinuteDiff(a, b))
        assertEquals(-90, getMinuteDiff(b, a))
    }

    @Test(expected = NullPointerException::class)
    fun `분 차이 계산에 null이 들어오면 예외가 발생한다`() {
        getMinuteDiff(LocalDateTime.of(2026, 1, 1, 9, 0), null)
    }

}