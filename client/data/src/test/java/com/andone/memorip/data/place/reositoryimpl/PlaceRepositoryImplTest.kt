package com.andone.memorip.data.place.reositoryimpl

import com.andone.memorip.data.place.datasource.local.PlaceDatabase
import com.andone.memorip.data.place.datasource.local.dao.PlaceDao
import com.andone.memorip.data.place.datasource.local.model.PlaceEntity
import com.andone.memorip.data.place.datasource.remote.PlaceRemoteDataSource
import com.andone.memorip.data.place.model.GroupCompactResponse
import com.andone.memorip.data.place.model.PlaceCreateResponse
import com.andone.memorip.data.place.model.PlaceCreateUpdateRequest
import com.andone.memorip.data.place.model.PlaceDetailResponse
import com.andone.memorip.data.place.model.PlaceListItemResponse
import com.andone.memorip.data.place.repositoryimpl.PlaceRepositoryImpl
import com.andone.memorip.domain.model.NetworkError
import com.andone.memorip.domain.model.PlaceListItem
import com.andone.memorip.domain.model.Region
import com.andone.memorip.domain.model.request.Address
import com.andone.memorip.domain.model.request.PlaceCreateUpdate
import com.andone.memorip.domain.model.response.PlaceCreated
import com.andone.memorip.domain.model.response.PlaceImageUploadResponse
import com.andone.memorip.domain.model.response.TripCompact
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import java.io.File

class PlaceRepositoryImplTest {

    private val remote: PlaceRemoteDataSource = mockk()
    private val database: PlaceDatabase = mockk()
    private val placeDao: PlaceDao = mockk(relaxed = true)
    private val repository = PlaceRepositoryImpl(remote, database, placeDao)
    private val address = Address(fullAddress = "서울 강남구", region1Depth = "서울", region2Depth = "강남구")
    private val detailResponse = PlaceDetailResponse(
        placeId = "p1",
        writerId = "w1",
        title = "장소",
        content = "내용",
        latitude = 37.5,
        longitude = 127.0,
        tags = emptyList(),
        images = listOf("img1"),
        thumbnailImageRatio = 1.5f,
        groups = listOf(GroupCompactResponse("t1", "제주")),
        address = address,
        isMine = true,
        isInMyGroup = false,
        isPublic = true
    )
    private val entity = PlaceEntity(
        id = "p1",
        title = "장소",
        latitude = 37.5,
        longitude = 127.0,
        address = "서울 강남구",
        imageUrl = "img1",
        thumbnailImageRatio = 1.5f,
        isPublic = true
    )

    private fun createUpdate(isPublic: Boolean = true, images: List<String> = listOf("img1", "img2")) =
        PlaceCreateUpdate(
            tripIds = listOf("t1"),
            title = "장소",
            content = "내용",
            tags = listOf("tag1"),
            latitude = 37.5,
            longitude = 127.0,
            address = address,
            imageUrls = images,
            thumbnailImageRatio = 1.5f,
            isPublic = isPublic
        )

    @Test
    fun `장소 상세 응답을 도메인 모델로 변환한다`() = runTest {
        coEvery { remote.getPlaceDetail("p1") } returns Result.success(detailResponse)

        val detail = repository.getPlaceDetail("p1").getOrNull()!!

        assertEquals("p1", detail.placeId)
        assertEquals(listOf(TripCompact("t1", "제주")), detail.trips)
        assertEquals(address, detail.address)
    }

    @Test
    fun `여행의 장소 목록을 도메인 모델로 변환한다`() = runTest {
        val response = PlaceListItemResponse(
            id = "p1",
            title = "장소",
            latitude = 37.5,
            longitude = 127.0,
            address = "주소",
            imageUrl = "img",
            thumbnailImageRatio = 1.5f,
            scrapCount = 0,
            isPublic = true
        )
        coEvery { remote.getPlaceByTripId("t1", 0, 20) } returns Result.success(listOf(response))

        val items = repository.getPlaceByTripId("t1", 0, 20).getOrNull()

        assertEquals(
            listOf(PlaceListItem("p1", "장소", 37.5, 127.0, "주소", "img", 1.5f, 0, true)),
            items
        )
    }

    @Test
    fun `공개 장소를 생성하면 요청을 변환해 보내고 로컬 캐시에도 저장한다`() = runTest {
        val request = slot<PlaceCreateUpdateRequest>()
        coEvery { remote.createPlace(capture(request)) } returns Result.success(
            PlaceCreateResponse(
                "p1"
            )
        )

        val result = repository.createPlace(createUpdate(isPublic = true))

        assertEquals(PlaceCreated("p1"), result.getOrNull())
        assertEquals(listOf("t1"), request.captured.groupIds)
        assertEquals(listOf("img1", "img2"), request.captured.imageUrls)
        coVerify(exactly = 1) { placeDao.insertPlace(entity) }
    }

    @Test
    fun `비공개 장소를 생성하면 로컬 캐시에는 저장하지 않는다`() = runTest {
        coEvery { remote.createPlace(any()) } returns Result.success(PlaceCreateResponse("p1"))

        repository.createPlace(createUpdate(isPublic = false))

        coVerify(exactly = 0) { placeDao.insertPlace(any()) }
    }

    @Test
    fun `장소 생성에 실패하면 같은 예외로 실패하고 캐시에 저장하지 않는다`() = runTest {
        val error = NetworkError.InvalidRequestError("잘못된 요청")
        coEvery { remote.createPlace(any()) } returns Result.failure(error)

        val result = repository.createPlace(createUpdate())

        assertSame(error, result.exceptionOrNull())
        coVerify(exactly = 0) { placeDao.insertPlace(any()) }
    }

    @Test
    fun `장소를 수정하면 응답으로 로컬 캐시를 갱신하고 도메인 모델을 반환한다`() = runTest {
        coEvery { remote.updatePlace("p1", any()) } returns Result.success(detailResponse)

        val result = repository.updatePlace("p1", createUpdate())

        assertEquals("p1", result.getOrNull()!!.placeId)
        coVerify(exactly = 1) { placeDao.updatePlace(entity) }
    }

    @Test
    fun `장소 수정에 실패하면 캐시를 갱신하지 않는다`() = runTest {
        coEvery { remote.updatePlace(any(), any()) } returns Result.failure(RuntimeException())

        repository.updatePlace("p1", createUpdate())

        coVerify(exactly = 0) { placeDao.updatePlace(any()) }
    }

    @Test
    fun `장소를 삭제하면 로컬 캐시에서도 삭제한다`() = runTest {
        coEvery { remote.deletePlace("p1") } returns Result.success(Unit)

        repository.deletePlace("p1")

        coVerify(exactly = 1) { placeDao.deletePlaceById("p1") }
    }

    @Test
    fun `장소 삭제에 실패하면 로컬 캐시를 지우지 않는다`() = runTest {
        coEvery { remote.deletePlace("p1") } returns Result.failure(RuntimeException())

        repository.deletePlace("p1")

        coVerify(exactly = 0) { placeDao.deletePlaceById(any()) }
    }

    @Test
    fun `여행 변경 지역 목록 이미지 업로드는 데이터 소스에 그대로 위임한다`() = runTest {
        val regions = listOf(Region(name = "서울", parent = null, level = 1))
        val file = File("a.jpg")
        coEvery { remote.updatePlaceTrips("p1", listOf("a"), listOf("b")) } returns Result.success(Unit)
        coEvery { remote.uploadImage(file) } returns Result.success(PlaceImageUploadResponse("url"))
        every { remote.loadRegions() } returns regions

        assertTrue(repository.updatePlaceTrips("p1", listOf("a"), listOf("b")).isSuccess)
        assertEquals("url", repository.uploadImage(file).getOrNull()!!.imageUrl)
        assertSame(regions, repository.loadRegions())
    }
}