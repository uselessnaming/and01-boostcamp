package com.andone.memorip.data.place.datasource.remote

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator.MediatorResult
import com.andone.memorip.data.common.ApiResult
import com.andone.memorip.data.place.datasource.local.PlaceDatabase
import com.andone.memorip.data.place.datasource.local.dao.PlaceKeyDao
import com.andone.memorip.data.place.datasource.local.model.PlaceEntity
import com.andone.memorip.data.place.datasource.local.model.PlaceKeyEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalPagingApi::class)
class PlaceRemoteMediatorTest {

    private val remote: PlaceRemoteDataSource = mockk()
    private val database: PlaceDatabase = mockk()
    private val placeKeyDao: PlaceKeyDao = mockk()
    private val mediator = PlaceRemoteMediator(
        remoteDataSource = remote,
        database = database,
        query = "카페",
        tagIds = listOf("tag1"),
        region1Depth = "서울",
        region2Depth = listOf("강남구")
    )
    private val entity = PlaceEntity("p1", "장소", 37.5, 127.0, "주소", "", 1.5f, true)

    private fun emptyState() = PagingState<Int, PlaceEntity>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = 20),
        leadingPlaceholderCount = 0
    )

    private fun stateWithLastItem() = PagingState<Int, PlaceEntity>(
        pages = listOf(
            PagingSource.LoadResult.Page(
                data = listOf(entity),
                prevKey = null,
                nextKey = null
            )
        ),
        anchorPosition = null,
        config = PagingConfig(pageSize = 20),
        leadingPlaceholderCount = 0
    )

    @Before
    fun setUp() {
        every { database.placeKeyDao() } returns placeKeyDao
    }

    @Test
    fun `이전 페이지 요청은 서버를 호출하지 않고 끝으로 처리한다`() = runTest {
        val result = mediator.load(LoadType.PREPEND, emptyState())

        assertTrue((result as MediatorResult.Success).endOfPaginationReached)
        coVerify(exactly = 0) {
            remote.getPlaceList(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `새로고침은 0페이지를 필터 조건과 함께 요청하고 error가 있으면 에러를 반환한다`() = runTest {
        coEvery { remote.getPlaceList(any(), any(), any(), any(), any(), any(), any()) } returns
                ApiResult(error = ApiResult.ErrorDetail(code = "E1", message = "실패"))

        val result = mediator.load(LoadType.REFRESH, emptyState()) as MediatorResult.Error

        assertTrue(result.throwable is IllegalStateException)
        assertEquals("실패", result.throwable.message)
        coVerify {
            remote.getPlaceList(
                query = "카페",
                tagIds = listOf("tag1"),
                region1Depth = "서울",
                region2Depth = listOf("강남구"),
                page = 0,
                size = 20,
                sort = any()
            )
        }
    }

    @Test
    fun `서버 호출이 예외를 던지면 그 예외를 담은 에러를 반환한다`() = runTest {
        val exception = RuntimeException("오류")
        coEvery {
            remote.getPlaceList(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } throws exception

        val result = mediator.load(LoadType.REFRESH, emptyState()) as MediatorResult.Error

        assertSame(exception, result.throwable)
    }

    @Test
    fun `이어붙이기에서 마지막 아이템의 키를 찾을 수 없으면 끝이 아닌 것으로 처리한다`() = runTest {
        val result = mediator.load(LoadType.APPEND, emptyState()) as MediatorResult.Success

        assertFalse(result.endOfPaginationReached)
    }

    @Test
    fun `이어붙이기에서 마지막 아이템의 다음 키가 없으면 끝으로 처리한다`() = runTest {
        coEvery { placeKeyDao.getKeyByPlaceId("p1") } returns PlaceKeyEntity(
            "p1",
            prevKey = null,
            nextKey = null
        )

        val result = mediator.load(LoadType.APPEND, stateWithLastItem()) as MediatorResult.Success

        assertTrue(result.endOfPaginationReached)
    }

    @Test
    fun `이어붙이기는 마지막 아이템의 다음 키 페이지를 요청한다`() = runTest {
        coEvery { placeKeyDao.getKeyByPlaceId("p1") } returns PlaceKeyEntity(
            "p1",
            prevKey = 0,
            nextKey = 2
        )
        coEvery { remote.getPlaceList(any(), any(), any(), any(), any(), any(), any()) } returns
                ApiResult(error = ApiResult.ErrorDetail(code = "E1", message = "실패"))

        mediator.load(LoadType.APPEND, stateWithLastItem())

        coEvery {
            remote.getPlaceList(
                query = any(),
                tagIds = any(),
                region1Depth = any(),
                region2Depth = any(),
                page = 2,
                size = 20,
                sort = any()
            )
        }
    }
}