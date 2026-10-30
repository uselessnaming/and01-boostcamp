package com.andone.memorip.data.mapper

import com.andone.memorip.data.place.datasource.local.model.PlaceEntity
import com.andone.memorip.data.place.datasource.local.model.toDomainModel
import com.andone.memorip.data.place.datasource.local.model.toEntity
import com.andone.memorip.data.place.model.GroupCompactResponse
import com.andone.memorip.data.place.model.PlaceCreateResponse
import com.andone.memorip.data.place.model.PlaceCreateUpdateRequest
import com.andone.memorip.data.place.model.PlaceDetailResponse
import com.andone.memorip.data.place.model.PlaceListItemResponse
import com.andone.memorip.data.place.model.toDomain
import com.andone.memorip.domain.model.PlaceListItem
import com.andone.memorip.domain.model.request.Address
import com.andone.memorip.domain.model.request.PlaceCreateUpdate
import com.andone.memorip.domain.model.response.PlaceCreated
import com.andone.memorip.domain.model.response.TripCompact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceMappingTest {

    private val address = Address(fullAddress = "서울 강남구", region1Depth = "서울", region2Depth = "강남구")
    private val createUpdate = PlaceCreateUpdate(
        tripIds = listOf("t1"),
        title = "장소",
        content = "내용",
        tags = listOf("tag1"),
        latitude = 37.5,
        longitude = 127.0,
        address = address,
        imageUrls = listOf("img1", "img2"),
        thumbnailImageRatio = 1.5f,
        isPublic = true
    )

    @Test
    fun `도메인 요청을 서버 요청으로 변환할 때 tripIds는 groupIdds가 된다`() {
        val request = createUpdate.toDomain()

        assertEquals(listOf("t1"), request.groupIds)
        assertEquals(listOf("img1", "img2"), request.imageUrls)
    }

    @Test
    fun `도메인 요청을 서버 요청으로 바꿨다가 되돌려도 같다`() {
        val roundTrip: PlaceCreateUpdate = createUpdate.toDomain().toDomain()

        assertEquals(createUpdate, roundTrip)
    }

    @Test
    fun `서버 요청 모델의 기본값이 유지된다`() {
        val request = PlaceCreateUpdateRequest(
            groupIds = emptyList(),
            title = "t",
            latitude = 0.0,
            longitude = 0.0,
            address = address,
            imageUrls = emptyList(),
            isPublic = false,
            thumbnailImageRatio = 1f
        )

        assertEquals(null, request.content)
        assertTrue(request.tags.isEmpty())
    }

    @Test
    fun `PlaceCreateResponse를 도메인으로 변환한다`() {
        assertEquals(PlaceCreated("p1"), PlaceCreateResponse("p1").toDomain())
    }

    @Test
    fun `GroupCompactResponse를 TripCompact로 변환한다`() {
        assertEquals(TripCompact("p1", "제주"), GroupCompactResponse("p1", "제주").toDomain())
    }

    @Test
    fun `생성 요청을 엔티티로 변환할 때 첫 이미지와 전체 주소를 사용한다`() {
        assertEquals(
            PlaceEntity("p1", "장소", 37.5, 127.0, "서울 강남구", "img1", 1.5f, true),
            createUpdate.toEntity("p1")
        )
    }

    @Test
    fun `상세 응답을 엔티티로 변환할 때 첫 이미지와 전체 주소를 사용한다`() {
        val response = PlaceDetailResponse(
            placeId = "p1",
            writerId = "w",
            title = "장소",
            content = null,
            latitude = 37.5,
            longitude = 127.0,
            tags = emptyList(),
            images = listOf("a", "b"),
            thumbnailImageRatio = 1.5f,
            groups = emptyList(),
            address = address,
            isMine = true,
            isInMyGroup = false,
            isPublic = true
        )

        assertEquals("a", response.toEntity().imageUrl)
        assertEquals("서울 강남구", response.toEntity().address)
    }

    @Test
    fun `목록 응답을 엔티티로 변환할 때 이미지 URL이 null이면 빈 문자열이다`() {
        val response = PlaceListItemResponse(
            id = "p1",
            title = "장소",
            latitude = 37.5,
            longitude = 127.0,
            address = "주소",
            imageUrl = null,
            thumbnailImageRatio = 1.5f,
            scrapCount = 3,
            isPublic = true
        )

        assertEquals(PlaceEntity("p1", "장소", 37.5, 127.0, "주소", "", 1.5f, true), response.toEntity())
    }

    @Test
    fun `엔티티를 도메인 목록 아이템으로 변환한다`() {
        val entity = PlaceEntity("p1", "장소", 37.5, 127.0, "주소", "img", 1.5f, false)

        assertEquals(
            PlaceListItem("p1", "장소", 37.5, 127.0, "주소", "img", 1.5f, 0, false),
            entity.toDomainModel()
        )
    }
}