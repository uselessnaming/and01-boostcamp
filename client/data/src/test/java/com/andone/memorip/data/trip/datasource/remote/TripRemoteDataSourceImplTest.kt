package com.andone.memorip.data.trip.datasource.remote

import com.andone.memorip.data.common.ApiResult
import com.andone.memorip.data.trip.datasource.TripService
import com.andone.memorip.data.trip.model.AddPlaceToTripRequest
import com.andone.memorip.data.trip.model.GroupVisibility
import com.andone.memorip.data.trip.model.SimpleTripItem
import com.andone.memorip.data.trip.model.TripListResponse
import com.andone.memorip.data.trip.model.TripPlaceItem
import com.andone.memorip.domain.model.NetworkError
import com.andone.memorip.domain.model.TripListItem
import com.andone.memorip.domain.model.TripPlace
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class TripRemoteDataSourceImplTest {

    private val service: TripService = mockk()
    private val dataSource = TripRemoteDataSourceImpl(service)

    private val listResponse = TripListResponse(
        id = "t1",
        title = "제주",
        visibility = GroupVisibility.PUBLIC,
        relatedPlaceImages = emptyList(),
        placeCount = 0,
        createdAt = "c",
        updatedAt = "u"
    )

    @Test
    fun `내 여행 조회 시 페이지 크기 장소 검색어를 서비스에 전달한다`() = runTest {
        coEvery {
            service.getMyTrips(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns ApiResult(data = listOf(listResponse))

        val result = dataSource.getMyTrips(page = 1, size = 20, placeId = "p1", query = "제주")

        assertEquals(listOf(listResponse), result.getOrNull())
        coVerify {
            service.getMyTrips(
                page = 1,
                size = 20,
                sort = any(),
                placeId = "p1",
                query = "제주"
            )
        }
    }

    @Test
    fun `간단한 여행 목록을 도메인 모델로 변환한다`() = runTest {
        coEvery { service.getSimpleTrips() } returns ApiResult(
            data = listOf(
                SimpleTripItem(
                    "t1",
                    "제주",
                    "2026-01-01",
                    "2026-01-03"
                )
            )
        )

        assertEquals(
            listOf(TripListItem("t1", "제주", "2026-01-01", "2026-01-03")),
            dataSource.getSimpleTrips().getOrNull()
        )
    }

    @Test
    fun `여행 장소 목록을 도메인 모델로 변환한다`() = runTest {
        coEvery { service.getPlaceByTripId("t1") } returns ApiResult(
            data = listOf(
                TripPlaceItem(
                    groupPlaceId = "tp1",
                    placeId = "p1",
                    title = "장소",
                    thumbnailUrl = "thumb",
                    address = "주소",
                    latitude = 37.5,
                    longitude = 127.0,
                    startAt = "2026-01-01T09:00",
                    endAt = "2026-01-01T10:00"
                )
            )
        )

        assertEquals(
            listOf(
                TripPlace(
                    tripPlaceId = "tp1",
                    placeId = "p1",
                    title = "장소",
                    thumbnail = "thumb",
                    address = "주소",
                    latitude = 37.5,
                    longitude = 127.0,
                    startAt = "2026-01-01T09:00",
                    endAt = "2026-01-01T10:00"
                )
            ),
            dataSource.getPlaceByTripId("t1").getOrNull()
        )
    }

    @Test
    fun `장소 추가 시 placeId로 요청 모델을 만든다`() = runTest {
        val request = slot<AddPlaceToTripRequest>()
        coEvery { service.addPlaceToTrip("t1", capture(request)) } returns ApiResult(data = Unit)

        val result = dataSource.addPlaceToTrip("t1", "p1")

        assertTrue(result.isSuccess)
        assertEquals("p1", request.captured.placeId)
    }

    @Test
    fun `삭제에 성공하면 성공 결과를 반환한다`() = runTest {
        coEvery { service.deleteTrip("t1") } returns ApiResult(data = Unit)

        assertTrue(dataSource.deleteTrip("t1").isSuccess)
    }

    @Test
    fun `서버가 404를 반환하면 NotFoundError로 실패한다`() = runTest {
        coEvery { service.clearPlaceTime(any()) } throws HttpException(
            Response.error<Any>(404, "".toResponseBody("application/json".toMediaType()))
        )

        val error = dataSource.clearPlaceTime("tp1").exceptionOrNull()

        assertTrue(error is NetworkError.NotFoundError)

    }
}