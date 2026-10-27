package com.andone.memorip.data.trip.repositoryImpl

import androidx.paging.PagingData
import com.andone.memorip.data.trip.datasource.remote.TripRemoteDataSource
import com.andone.memorip.data.trip.model.GroupVisibility
import com.andone.memorip.data.trip.model.TripCreateRequest
import com.andone.memorip.data.trip.model.TripListResponse
import com.andone.memorip.data.trip.model.TripUpdateRequest
import com.andone.memorip.data.trip.model.UpdatePlaceTimeRequest
import com.andone.memorip.data.trip.repositoryimpl.TripRepositoryImpl
import com.andone.memorip.domain.model.NetworkError
import com.andone.memorip.domain.model.PlaceListItem
import com.andone.memorip.domain.model.Trip
import com.andone.memorip.domain.model.TripListItem
import com.andone.memorip.domain.model.TripPlace
import com.andone.memorip.domain.model.TripWithPlaceAdded
import com.andone.memorip.domain.model.User
import com.andone.memorip.domain.model.Visibility
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame

class TripRepositoryImplTest {

    private val dataSource: TripRemoteDataSource = mockk()
    private lateinit var repository: TripRepositoryImpl

    private fun response(id: String, added: Boolean = false) = TripListResponse(
        id = id,
        title = "여행$id",
        visibility = GroupVisibility.PRIVATE,
        relatedPlaceImages = listOf("img-$id"),
        placeCount = 1,
        createdAt = "c",
        updatedAt = "u",
        isPlaceAdded = added
    )

    private fun trip(id: String) = Trip(
        id = id,
        owner = User.EMPTY,
        title = "여행$id",
        visibility = Visibility.PRIVATE,
        createdAt = "c",
        updatedAt = "u",
        images = listOf("img-$id")
    )

    @Before
    fun setUp() {
        coEvery { dataSource.getMyTrips(any(), any(), any(), any()) } returns Result.success(emptyList())
        repository = TripRepositoryImpl(dataSource)
    }

    @Test
    fun `내 여행 조회에 성공하면 도메인 모델로 변환해 myTrips에 반영한다`() = runTest {
        coEvery { dataSource.getMyTrips(any(), any(), any(), any()) } returns Result.success(listOf(response("1"), response("2")))

        val result = repository.fetchMyTrips(page = 0, size = 20, query = null)

        assertTrue(result.isSuccess)
        assertEquals(listOf(trip("1"), trip("2")), repository.myTrips.first())
    }

    @Test
    fun `내 여행 조회 시 페이지 크기 검색어를 데이터 소스에 전달한다`() = runTest {
        repository.fetchMyTrips(page = 2, size = 5, query = "제주")

        coVerify(exactly = 1) { dataSource.getMyTrips(2, 5, null, "제주") }
    }

    @Test
    fun `내 여행 조회에 실패하면 같은 예외 실패하고 목록은 비어 있다`() = runTest {
        val error = NetworkError.UnknownError("실패")
        coEvery { dataSource.getMyTrips(any(), any(), any(), any()) } returns Result.failure(error)

        val result = repository.fetchMyTrips(page = 0, size = 20, query = null)

        assertSame(error, result.exceptionOrNull())
        assertTrue(repository.myTrips.first().isEmpty())
    }

    @Test
    fun `장소 포함 여부와 함께 조회하면 placeId를 전달하고 포함 여부를 담아 변환한다`() = runTest {
        coEvery { dataSource.getMyTrips(0, 20, "p1", null) } returns
                Result.success(listOf(response("1", added = true), response("2", added = false)))

        val result = repository.fetchMyTripsWithPlaceStatus(page = 0, size = 20, placeId = "p1")

        assertEquals(
            listOf(TripWithPlaceAdded(trip("1"), true), TripWithPlaceAdded(trip("2"), false)),
            result.getOrNull()
        )
    }

    @Test
    fun `공개 여행 조회 결과를 도메인 모델로 변환한다`() = runTest {
        coEvery { dataSource.getPublicTrips(0, 10) } returns Result.success(listOf(response("1")))

        assertEquals(listOf(trip("1")), repository.getPublicTrips(0, 10).getOrNull())
    }

    @Test
    fun `여행 생성 시 제목과 공개 범위 이름으로 요청을 만든다`() = runTest {
        val request = slot<TripCreateRequest>()
        coEvery { dataSource.createTrip(capture(request)) } returns Result.success(trip("n"))

        val result = repository.createTrip("제주", Visibility.PUBLIC)

        assertEquals(trip("n"), result.getOrNull())
        assertEquals("제주", request.captured.title)
        assertEquals("PUBLIC", request.captured.visibility)
    }

    @Test
    fun `여행 생성에 성공하면 myTrips 맨 앞에 추가된다`() = runTest {
        val emissions = mutableListOf<List<Trip>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.myTrips.toList(emissions)
        }
        coEvery { dataSource.createTrip(any()) } returnsMany
            listOf(Result.success(trip("a")), Result.success(trip("b")))

        repository.createTrip("a", Visibility.PRIVATE)
        repository.createTrip("b", Visibility.PRIVATE)

        assertEquals(listOf(trip("b"), trip("a")), emissions.last())
    }

    @Test
    fun `여행 생성에 실패하면 목록은 그대로이고 같은 예외로 실패한다`() = runTest {
        val emissions = mutableListOf<List<Trip>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.myTrips.toList(emissions)
        }
        val error = NetworkError.InternalServerError("실패")
        coEvery { dataSource.createTrip(any()) } returns Result.failure(error)

        val result = repository.createTrip("a", Visibility.PRIVATE)

        assertSame(error, result.exceptionOrNull())
        assertTrue(emissions.last().isEmpty())
    }

    @Test
    fun `여행 수정 시 요청 모델을 만들어 전달한다`() = runTest {
        val request = slot<TripUpdateRequest>()
        coEvery { dataSource.updateTrip("t1", capture(request)) } returns Result.success(Unit)

        val result = repository.updateTrip("t1", "제주", Visibility.PRIVATE, "2026-01-01", "2026-01-03")

        assertTrue(result.isSuccess)
        with(request.captured) {
            assertEquals("제주", title)
            assertEquals("PRIVATE", visibility)
            assertEquals("2026-01-01", startDate)
            assertEquals("2026-01-03", endDate)
        }
    }

    @Test
    fun `장소 시간 수정 시 시작과 종료 시간으로 요청 모델을 만든다`() = runTest {
        val request = slot<UpdatePlaceTimeRequest>()
        coEvery { dataSource.updatePlaceTime("tp1", capture(request)) } returns Result.success(Unit)

        repository.updatePlaceTime("tp1", "2026-01-01T09:00", "2026-01-01T10:00")

        assertEquals("2026-01-01T09:00", request.captured.startAt)
        assertEquals("2026-01-01T10:00", request.captured.endAt)
    }

    @Test
    fun `삭제와 추가 계열은 인자 그대로 데이터 소스에 전달한다`() = runTest {
        coEvery { dataSource.deleteTrip(any()) } returns Result.success(Unit)
        coEvery { dataSource.addPlaceToTrip(any(), any()) } returns Result.success(Unit)
        coEvery { dataSource.deleteGroupPlace(any()) } returns Result.success(Unit)
        coEvery { dataSource.clearPlaceTime(any()) } returns Result.success(Unit)

        assertTrue(repository.deleteTrip("t1").isSuccess)
        assertTrue(repository.addPlaceToTrip("t1", "p1").isSuccess)
        assertTrue(repository.deleteGroupPlace("tp1").isSuccess)
        assertTrue(repository.clearPlaceTime("tp1").isSuccess)

        coVerify { dataSource.deleteTrip("t1") }
        coVerify { dataSource.addPlaceToTrip("t1", "p1") }
        coVerify { dataSource.deleteGroupPlace("tp1") }
        coVerify { dataSource.clearPlaceTime("tp1") }
    }

    @Test
    fun `조회 계열은 데이터 소스 결과를 그대로 반환한다`() = runTest {
        val simple = Result.success(listOf(TripListItem("t1", "제주")))
        val places = Result.success(listOf(TripPlace("tp1", "p1", "장소", null, "주소", 37.5, 127.0, null, null)))
        val single = Result.success(trip("t1"))
        coEvery { dataSource.getSimpleTrips() } returns simple
        coEvery { dataSource.getPlaceByTripId("t1") } returns places
        coEvery { dataSource.getTripById("t1") } returns single

        assertEquals(simple, repository.getSimpleTrips())
        assertEquals(places, repository.getPlaceByTripId("t1"))
        assertEquals(single, repository.getTripById("t1"))
    }

    @Test
    fun `여행 장소 페이징은 데이터소스의 flow를 그대로 반환한다`() {
        val flow = flowOf(PagingData.empty<PlaceListItem>())
        every { dataSource.getTripPlaces("t1") } returns flow

        assertSame(flow, repository.getTripPlaces("t1"))
    }
}