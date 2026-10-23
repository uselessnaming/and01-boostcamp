package com.andone.memorip.presentation.modeltest

import com.andone.memorip.domain.model.TimeBlock
import com.andone.memorip.presentation.model.Place
import com.andone.memorip.presentation.screen.plan.utill.MINUTES_PER_DAY
import com.andone.memorip.presentation.screen.plan.utill.TimeLayoutEngine
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.Assert.assertEquals
import java.time.LocalDate
import java.time.LocalDateTime

class TimeLayoutEngineTest {

    private val engine = TimeLayoutEngine(minuteHeightPx = 2f)

    private fun placeStartingAt(start: LocalDateTime): Place =
        mockk<Place>(relaxed = true).also { every { it.startDateTime } returns start }

    @Test
    fun `첫째 날 블록의 y 좌표는 시작 분에 분당 높이를 곱한 값이다`() {
        val block = TimeBlock(id = "a", startMinute = 60, durationMinute = 30, day = 1)

        assertEquals(120f, engine.blockStartYPx(block), 0.001f)
    }

    @Test
    fun `둘째 날 블록의 y 좌표는 하루 분을 더해 계산한다`() {
        val block = TimeBlock(id = "a", startMinute = 60, durationMinute = 30, day = 2)

        assertEquals((MINUTES_PER_DAY + 60) * 2f, engine.blockStartYPx(block), 0.001f)
    }

    @Test
    fun `시작일 기준으로 장소 블록의 y 좌표를 계산한다`() {
        val startDate = LocalDate.of(2026, 1, 1)
        val place = placeStartingAt(LocalDateTime.of(2026, 1, 1, 1, 0))

        assertEquals(120f, engine.blockStartYPx(startDate, place), 0.001f)
    }

    @Test
    fun `다음 날 0시의 장소 블록은 하루치 높이만큼 아래에 있다`() {
        val startDate = LocalDate.of(2026, 1, 1)
        val place = placeStartingAt(LocalDateTime.of(2026, 1, 2, 0, 0))

        assertEquals(MINUTES_PER_DAY * 2f, engine.blockStartYPx(startDate, place))
    }

    @Test
    fun `y 좌표를 시작 분으로 변환한다`() {
        assertEquals(60, engine.yPxToStartMinute(120f))
        assertEquals(0, engine.yPxToStartMinute(0f))
    }

    @Test
    fun `y 좌표를 시작 분으로 변환할 때 소수점은 버린다`() {
        assertEquals(2, engine.yPxToStartMinute(5f))
    }
}