package com.andone.memorip.data.trip.datasource

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import com.andone.memorip.data.common.ApiResult
import com.andone.memorip.data.place.model.PlaceListItemResponse
import com.andone.memorip.domain.model.PlaceListItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

class TripPlacePagingSourceTest {

    private val service: TripService = mockk()
    private val source = TripPlacesPagingSource(service = service, tripId = "t1", pageSize = 10)
    private val response = PlaceListItemResponse(
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
    private val domain = PlaceListItem(
        id = "p1",
        title = "장소",
        latitude = 37.5,
        longitude = 127.0,
        address = "주소",
        imageUrl = "",
        thumbnailImageRatio = 1.5f,
        isPublic = true
    )

    private fun pagination(hasNext: Boolean) = ApiResult.PaginationInfo(
        currentPage = 0,
        totalPages = 2,
        totalCount = 15,
        hasNext = hasNext
    )

    private fun refresh(key: Int? = null) =
        PagingSource.LoadParams.Refresh(key = key, loadSize = 20, placeholdersEnabled = false)

    @Test
    fun `키가 없으면 0페이지를 요청하고 도메인 모델로 변환한다`() = runTest {
        coEvery { service.getTripPlaces("t1", 0, 10, null) } returns
            ApiResult(data = listOf(response), pagination = pagination(true))

        val result = source.load(refresh())

        assertEquals(PagingSource.LoadResult.Page(data = listOf(domain), prevKey = null, nextKey = 1), result)
    }

    @Test
    fun `이미지 URL이 null이면 빈 문자열로 변환된다`() = runTest {
        coEvery { service.getTripPlaces(any(), any(), any(), any()) } returns
            ApiResult(data = listOf(response), pagination = pagination(false))

        val page = source.load(refresh()) as PagingSource.LoadResult.Page

        assertEquals("", page.data.single().imageUrl)
    }

    @Test
    fun `다음 페이지가 없으면 nextKey는 null이고 이전 키는 page 빼기 1이다`() = runTest {
        coEvery { service.getTripPlaces("t1", 3, 10, null) } returns
            ApiResult(data = listOf(response), pagination = pagination(false))

        val result = source.load(refresh(key = 3))

        assertEquals(PagingSource.LoadResult.Page(data = listOf(domain), prevKey = 2, nextKey = null), result)
    }

    @Test
    fun `pagination이 없거나 data가 null이면 빈 마지막 페이지로 처리한다`() = runTest {
        coEvery { service.getTripPlaces(any(), any(), any(), any()) } returns
            ApiResult(data = null, pagination = null)

        val page = source.load(refresh()) as PagingSource.LoadResult.Page

        assertTrue(page.data.isEmpty())
        assertNull(page.nextKey)
    }

    @Test
    fun `정렬 조건이 있으면 서비스에 그대로 전달한다`() = runTest {
        val sorted = TripPlacesPagingSource(service, "t1", 10, sort = listOf("id,desc"))
        coEvery { service.getTripPlaces(any(), any(), any(), any()) } returns ApiResult(data = emptyList())

        sorted.load(refresh())

        coVerify { service.getTripPlaces("t1", 0, 10, listOf("id,desc")) }
    }

    @Test
    fun `응답에 error가 있으면 메시지를 담은 에러를 반환한다`() = runTest {
        coEvery { service.getTripPlaces(any(), any(), any(), any()) } returns
            ApiResult(error = ApiResult.ErrorDetail(code = "E1", message = "실패"))

        val result = source.load(refresh()) as PagingSource.LoadResult.Error

        assertTrue(result.throwable is IllegalStateException)
        assertEquals("실패", result.throwable.message)
    }

    @Test
    fun `서비스가 예외를 던지면 그 예외를 담은 에러를 반환한다`() = runTest {
        val exception = RuntimeException("오류")
        coEvery { service.getTripPlaces(any(), any(), any(), any()) } throws exception

        val result = source.load(refresh()) as PagingSource.LoadResult.Error

        assertSame(exception, result.throwable)
    }

    @Test
    fun `새로고침 키는 이전 키 더하기 1 아니면 다음 키 빼기 1이다`() {
        fun stateWith(prev: Int?, next: Int?, anchor: Int? = 0) = PagingState(
            pages = listOf(LoadResult.Page(data = listOf(domain), prevKey = prev, nextKey = next)),
            anchorPosition = anchor,
            config = PagingConfig(pageSize = 10),
            leadingPlaceholderCount = 0
        )

        assertEquals(2, source.getRefreshKey(stateWith(prev = 1, next = 3)))
        assertEquals(0, source.getRefreshKey(stateWith(prev = null, next = 1)))
        assertNull(source.getRefreshKey(stateWith(prev = null, next = null)))
        assertNull(source.getRefreshKey(stateWith(prev = 1, next = 3, anchor = null)))
    }
}