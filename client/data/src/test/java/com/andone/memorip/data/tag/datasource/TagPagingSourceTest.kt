package com.andone.memorip.data.tag.datasource

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import com.andone.memorip.data.common.ApiResult
import com.andone.memorip.data.tag.model.TagResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test

class TagPagingSourceTest {
    private val service: TagService = mockk()
    private val source = TagPagingSource(service = service, pageSize = 20)
    private val tags = listOf(TagResponse(id = "1", name = "맛집", colorHex = "#FF0000"))

    private fun pagination(hasNext: Boolean) = ApiResult.PaginationInfo(
        currentPage = 0, totalPages = 2, totalCount = 30, hasNext = hasNext
    )

    private fun refresh(key: Int? = null) =
        PagingSource.LoadParams.Refresh(key = key, loadSize = 20, placeholdersEnabled = false)

    private fun stateWith(prevKey: Int?, nextKey: Int?, anchor: Int?) = PagingState(
        pages = listOf(LoadResult.Page(data = tags, prevKey = prevKey, nextKey = nextKey)),
        anchorPosition = anchor,
        config = PagingConfig(pageSize = 20),
        leadingPlaceholderCount = 0
    )

    @Test
    fun `키가 없으면 0페이지를 불러오고 다음 페이지가 있으면 nextKey는 1이다`() = runTest {
        coEvery { service.getTags(0, 20) } returns ApiResult(data = tags, pagination = pagination(true))

        val result = source.load(refresh(key = null))

        assertEquals(PagingSource.LoadResult.Page(data = tags, prevKey = null, nextKey = 1), result)
        coVerify { service.getTags(page = 0, size = 20) }
    }

    @Test
    fun `중간 페이지는 이전 키와 다음 키를 모두 가진다`() = runTest {
        coEvery { service.getTags(2, 20) } returns ApiResult(data = tags, pagination = pagination(true))

        val result = source.load(refresh(key = 2))

        assertEquals(LoadResult.Page(data = tags, prevKey = 1, nextKey = 3), result)
    }

    @Test
    fun `다음 페이지가 없으면 nextKey는 null이다`() = runTest {
        coEvery { service.getTags(1, 20) } returns ApiResult(data = tags, pagination = pagination(false))

        val result = source.load(refresh(key = 1))

        assertEquals(LoadResult.Page(data = tags, prevKey = 0, nextKey = null), result)
    }

    @Test
    fun `data가 null이면 빈 목록으로 처리한다`() = runTest {
        coEvery { service.getTags(0, 20) } returns ApiResult(data = null, pagination = pagination(false))

        val result = source.load(refresh())  as LoadResult.Page

        assertTrue(result.data.isEmpty())
    }

    @Test
    fun `응답에 error가 있으면 메시지를 담은 에러를 반환한다`() = runTest {
        coEvery { service.getTags(0, 20) } returns ApiResult(error = ApiResult.ErrorDetail(code = "E1", message = "실패"))

        val result = source.load(refresh()) as LoadResult.Error

        assertTrue(result.throwable is IllegalStateException)
        assertEquals("실패", result.throwable.message)
    }

    @Test
    fun `서비스 호출이 예외를 던지면 그 예외를 담은 에러를 반환한다`() = runTest {
        val exception = RuntimeException("네트워크 오류")
        coEvery { service.getTags(any(), any()) } throws exception

        val result = source.load(refresh()) as LoadResult.Error

        assertSame(exception, result.throwable)
    }

    @Test
    fun `이전 키가 있으면 이전 키 더하기 1이 새로고침 키다`() {
        assertEquals(2, source.getRefreshKey(stateWith(prevKey = 1, nextKey = 3, anchor = 0)))
    }

    @Test
    fun `이전 키가 없으면 다음 키 빼기 1이 새로고침 키다`() {
        assertEquals(0, source.getRefreshKey(stateWith(prevKey = null, nextKey = 1, anchor = 0)))
    }

    @Test
    fun `이전 키와 다음 키가 모두 없거나 anchor가 없으면 새로고침 키는 null이다`() {
        assertNull(source.getRefreshKey(stateWith(prevKey = null, nextKey = null, anchor = 0)))
        assertNull(source.getRefreshKey(stateWith(prevKey = 1, nextKey = 3, anchor = null)))
    }
}