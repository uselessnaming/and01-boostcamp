package com.andone.memorip.presentation.modeltest

import com.andone.memorip.domain.model.TimeBlock
import com.andone.memorip.domain.model.Trip
import com.andone.memorip.domain.model.TripListItem
import com.andone.memorip.domain.model.TripPlace
import com.andone.memorip.domain.model.Visibility
import com.andone.memorip.presentation.model.TripUiModel
import com.andone.memorip.presentation.model.toTimeBlock
import com.andone.memorip.presentation.model.toUiModel
import com.andone.memorip.presentation.screen.plan.model.TripListUiModel
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ModelMappingTest {

    private val dayStart = LocalDateTime.of(2026, 1, 1, 0, 0)

    private fun tripPlace(start: String?, end: String?) = TripPlace(
        tripPlaceId = "tp1",
        placeId = "p1",
        title = "제목",
        thumbnail = null,
        address = "주소",
        latitude = 37.5,
        longitude = 127.0,
        startAt = start,
        endAt = end
    )

    @Test
    fun `여행 목록 아이템의 날짜 문자열을 LocalDate로 변환한다`() {
        val model = TripListUiModel.from(TripListItem("t1", "제주", "2026-01-01", "2026-01-03"))

        assertEquals(
            TripListUiModel(
                "t1",
                "제주",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 3)
            ), model
        )
    }

    @Test
    fun `날짜가 없는 여행 목록 아이템은 null 날짜로 변환한다`() {
        val model = TripListUiModel.from(TripListItem("t1", "제주"))

        assertNull(model.startDate)
        assertNull(model.endDate)
    }

    @Test
    fun `Trip을 TripUiModel로 변환한다`() {
        val trip = Trip(
            id = "t1",
            owner = mockk(relaxed = true),
            title = "제주",
            visibility = Visibility.PRIVATE,
            createdAt = "",
            updatedAt = "",
            images = listOf("a", "b")
        )

        assertEquals(TripUiModel(id = "t1", name = "제주", images = listOf("a", "b")), TripUiModel.from(trip))
    }

    @Test
    fun `TripPlace를 Place로 변환할 때 시작과 종료 시간을 파싱한다`() {
        val place = tripPlace("2026-01-01T09:00", "2026-01-01T10:30").toUiModel()

        assertEquals("tp1", place.id)
        assertEquals("p1", place.placeId)
        assertEquals(LocalDateTime.of(2026, 1, 1, 9, 0), place.startDateTime)
        assertEquals(90L, place.durationMinutes)
    }

    @Test
    fun `시간이 없는 TripPlace는 시간이 null이고 기본 소요 시간은 60분이다`() {
        val place = tripPlace(null, null).toUiModel()

        assertNull(place.startDateTime)
        assertEquals(60L, place.durationMinutes)
    }

    @Test
    fun `첫째 날 장소는 TimeBlock 1일차로 변환된다`() {
        val block = tripPlace("2026-01-01T09:00", "2026-01-01T10:00").toUiModel()

        assertEquals(
            TimeBlock(
                id = "tp1",
                startMinute = 540,
                durationMinute = 60,
                day = 1,
                column = 0
            ), block
        )
    }

    @Test
    fun `둘째 날 장소는 TimeBlock 2일차이고 시작 분은 그날 0시 기준이다`() {
        val block =
            tripPlace("2026-01-02T09:00", "2026-01-02T10:00").toUiModel().toTimeBlock(dayStart)

        assertEquals(2, block!!.day)
        assertEquals(540, block.startMinute)
    }

    @Test
    fun `시간이 없는 장소는 TimeBlock으로 변환되지 않는다`() {
        assertNull(tripPlace(null, null).toUiModel().toTimeBlock(dayStart))
    }
}