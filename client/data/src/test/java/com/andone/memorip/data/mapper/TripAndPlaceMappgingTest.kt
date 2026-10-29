package com.andone.memorip.data.mapper

import com.andone.memorip.data.kakaosearch.model.KakaoLocationResponse
import com.andone.memorip.data.kakaosearch.model.toDomain
import com.andone.memorip.data.place.model.PlaceListItemResponse
import com.andone.memorip.data.place.model.toDomain
import com.andone.memorip.data.trip.model.GroupVisibility
import com.andone.memorip.data.trip.model.TripListResponse
import com.andone.memorip.domain.model.PlaceListItem
import com.andone.memorip.domain.model.User
import com.andone.memorip.domain.model.Visibility
import com.andone.memorip.domain.model.response.KakaoLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripAndPlaceMappgingTest {

    private fun tripResponse(visibility: GroupVisibility, added: Boolean = false) =
        TripListResponse(
            id = "t1",
            title = "제주",
            visibility = visibility,
            relatedPlaceImages = listOf("a", "b"),
            placeCount = 2,
            createdAt = "c",
            updatedAt = "u",
            isPlaceAdded = added
        )

    @Test
    fun `TripListResponse를 Trip으로 변환할 때 소유자는 빈 사용자이고 이미지는 연관 이미지다`() {
        val trip = tripResponse(GroupVisibility.PUBLIC).toDomain()

        assertEquals(User.EMPTY, trip.owner)
        assertEquals(listOf("a", "b"), trip.images)
        assertEquals(Visibility.PUBLIC, trip.visibility)
        assertEquals("c", trip.createdAt)
    }

    @Test
    fun `공개 범위가 도메인 값으로 변환된다`() {
        assertEquals(Visibility.PUBLIC, GroupVisibility.PUBLIC.toDomain())
        assertEquals(Visibility.PRIVATE, GroupVisibility.PRIVATE.toDomain())
    }

    @Test
    fun `장소 포함 여부가 함께 변환된다`() {
        assertTrue(tripResponse(GroupVisibility.PRIVATE, added = true).toDomainWithPlaceAdded().isPlaceAdded)
    }

    @Test
    fun `PlaceListItemResponse를 변환할 때 이미지 URL이 null이면 빈 분자열이다`() {
        val response = PlaceListItemResponse(
            id = "p1",
            title = "장소",
            latitude = 37.5,
            longitude = 127.0,
            address = "주소",
            imageUrl = null,
            thumbnailImageRatio = 1.5f,
            scrapCount = 7,
            isPublic = false
        )

        assertEquals(
            PlaceListItem("p1", "장소", 37.5, 127.0, "주소", "", 1.5f, 0, false),
            response.toDomain()
        )
    }

    @Test
    fun `KakaoLocationResponse를 도메인 모델로 변환한다`() {
        val response = KakaoLocationResponse(
            id = "1",
            title = "스타벅스",
            category = "카페",
            address = "서울 강남구",
            roadAddress = "서울 강남대로",
            longitude = "127.0",
            latitude = "37.5"
        )

        assertEquals(
            KakaoLocation("1", "스타벅스", "카페", "서울 강남구", "서울 강남대로", "127.0", "37.5"),
            response.toDomain()
        )
    }
}