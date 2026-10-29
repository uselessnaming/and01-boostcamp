package com.andone.memorip.data.kakaosearch.datasource

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import com.andone.memorip.data.kakaosearch.model.KakaoLocationResponse
import com.andone.memorip.data.kakaosearch.model.KakaoMeta
import com.andone.memorip.data.kakaosearch.model.KakaoSearchLocation
import com.andone.memorip.domain.model.response.KakaoLocation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class kakaSearchPagingSourceTest {

    private val service: KakaoSearchService = mockk()
    private val source = KakaoSearchPagingSource(apiService = service, query = "카페")
    private val item = KakaoLocationResponse(
        id = "1",
        title = "스타벅스",
        category = "카페",
        address = "서울 강남구",
        roadAddress = "서울 강남대로",
        longitude = "127.0",
        latitude = "37.5"
    )
    private val domain = KakaoLocation(
        id = "1",
        title = "스타벅스",
        category = "카페",
        address = "서울 강남구",
        roadAddress = "서울 강남대로",
        longitude = "127.0",
        latitude = "37.5"
    )

    private fun result(isEnd: Boolean, items: List<KakaoLocationResponse> = listOf(item)) =
        KakaoSearchLocation(
            locations = items,
            meta = KakaoMeta(totalCount = items.size, pageableCount = items.size, isEnd = isEnd)
        )

    private fun refresh(key: Int? = null, loadSize: Int = 15) =
        PagingSource.LoadParams.Refresh(key = key, loadSize = loadSize, placeholdersEnabled = false)

    @Test
    fun `키가 없으면 1페이지부터 시작하고 loadSize를 크기로 요청한다`() = runTest {
        coEvery { service.searchLocations("카페", 1, 15) } returns result(isEnd = false)

        val page = source.load(refresh(key = null, loadSize = 15))

        assertEquals(LoadResult.Page(data = listOf(domain), prevKey = null, nextKey = 2), page)
        coVerify { service.searchLocations(query = "카페", page = 1, size = 15) }
    }

    @Test
    fun `마지막 페이지이면 nextKey는 null이다`() = runTest {
        coEvery { service.searchLocations("카페", 3, 15) } returns result(isEnd = true)

        val page = source.load(refresh(key = 3))

        assertEquals(LoadResult.Page(data = listOf(domain), prevKey = 2, nextKey = null), page)
    }

    @Test
    fun `검색 결과가 없으면 빈 목록의 페이지를 반환한다`() = runTest {
        coEvery { service.searchLocations(any(), any(), any()) } returns
            result(isEnd = true, items = emptyList())

        val page = source.load(refresh()) as LoadResult.Page

        assertTrue(page.data.isEmpty())
        assertNull(page.nextKey)
    }

    @Test
    fun `서비스가 예외를 던지면 그 예외를 담은 에러를 반환한다`() = runTest {
        val exception = RuntimeException("오류")
        coEvery { service.searchLocations(any(), any(), any()) } throws exception

        val error = source.load(refresh()) as LoadResult.Error

        assertSame(exception, error.throwable)
    }

    @Test
    fun `새로고침 키는 이전 키 더하기 1 아니면 다음 키 빼기 1이다`() {
        fun stateWith(prev: Int?, next: Int?) = PagingState(
            pages = listOf(LoadResult.Page(data = listOf(domain), prevKey = prev, nextKey = next)),
            anchorPosition = 0,
            config = PagingConfig(pageSize = 15),
            leadingPlaceholderCount = 0
        )

        assertEquals(3, source.getRefreshKey(stateWith(prev = 2, next = 4)))
        assertEquals(1, source.getRefreshKey(stateWith(prev = null, next = 2)))
        assertNull(source.getRefreshKey(stateWith(prev = null, next = null)))
    }
}