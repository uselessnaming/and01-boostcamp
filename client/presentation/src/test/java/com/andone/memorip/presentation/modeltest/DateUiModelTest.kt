package com.andone.memorip.presentation.modeltest

import com.andone.memorip.presentation.screen.plan.model.DateUiModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateUiModelTest {

    private val d1 = LocalDate.of(2026, 1, 1)
    private val d2 = LocalDate.of(2026, 1, 2)
    private val d3 = LocalDate.of(2026, 1, 3)
    private val threeDays = DateUiModel(startDay = d1, endDay = d3, currentDay = d1)

    @Test
    fun `기간이 정해지지 않으면 totalDays는 0이다`() {
        assertEquals(0, DateUiModel().totalDays)
        assertEquals(0, DateUiModel(startDay = d1).totalDays)
    }

    @Test
    fun `시작일과 종료일이 같으면 하루다`() {
        assertEquals(1, DateUiModel(startDay = d1, endDay = d1).totalDays)
    }

    @Test
    fun `totalDays는 양 끝을 포함해 계산한다`() {
        assertEquals(3, threeDays.totalDays)
    }

    @Test
    fun `selectedDay는 시작일 기준 1부터 시작하는 일차다`() {
        assertEquals(1, threeDays.copy(currentDay = d1).selectedDay)
        assertEquals(3, threeDays.copy(currentDay = d3).selectedDay)
    }

    @Test
    fun `시작일이나 현재 날짜가 없으면 selectedDay는 null이다`() {
        assertNull(threeDays.copy(currentDay = null).selectedDay)
        assertNull(DateUiModel(currentDay = d1).selectedDay)
    }

    @Test
    fun `일차 번호로 날짜를 계산한다`() {
        assertEquals(d1, threeDays.currentDayFromSelectedDay(1))
        assertEquals(d3, threeDays.currentDayFromSelectedDay(3))
    }

    @Test
    fun `0 이하의 일차나 시작일이 없으면 날짜는 null이다`() {
        assertNull(threeDays.currentDayFromSelectedDay(0))
        assertNull(threeDays.currentDayFromSelectedDay(-1))
        assertNull(DateUiModel().currentDayFromSelectedDay(1))
    }

    @Test
    fun `기간이 없으면 삭제해도 그대로다`() {
        assertEquals(null to null, DateUiModel().deleteDay(1))
    }

    @Test
    fun `범위를 벗어난 일차는 삭제해도 그대로다`() {
        assertEquals(d1 to d3, threeDays.deleteDay(0))
        assertEquals(d1 to d3, threeDays.deleteDay(4))
    }

    @Test
    fun `하루짜리 일정에서 그 날을 삭제하면 기간이 사라진다`() {
        assertEquals(null to null, DateUiModel(startDay = d1, endDay = d1).deleteDay(1))
    }

    @Test
    fun `첫 날을 삭제하면 시작월이 하루 뒤로 밀린다`() {
        assertEquals(d2 to d3, threeDays.deleteDay(1))
    }

    @Test
    fun `첫 날이 아닌 날을 삭제하면 종료일이 하루 앞당겨진다`() {
        assertEquals(d1 to d2, threeDays.deleteDay(2))
        assertEquals(d1 to d2, threeDays.deleteDay(3))
    }
}