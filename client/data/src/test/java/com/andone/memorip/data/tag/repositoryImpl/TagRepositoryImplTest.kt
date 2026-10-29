package com.andone.memorip.data.tag.repositoryImpl

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.andone.memorip.data.tag.datasource.remote.TagRemoteDataSource
import com.andone.memorip.data.tag.model.TagRequest
import com.andone.memorip.data.tag.model.TagResponse
import com.andone.memorip.data.tag.repositoryimpl.TagRepositoryImpl
import com.andone.memorip.domain.model.NetworkError
import com.andone.memorip.domain.model.Tag
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TagRepositoryImplTest {

    private val dataSource: TagRemoteDataSource = mockk()
    private val repository = TagRepositoryImpl(dataSource)

    @Test
    fun `태그 목록을 도메인 모델로 변환해 반환한다`() = runTest {
        every { dataSource.loadTags() } returns flowOf(
            PagingData.from(
                listOf(
                    TagResponse("1", "맛집", "#FF0000"),
                    TagResponse("2", "카페", "#00FF00")
                )
            )
        )

        val tags = repository.loadTags().asSnapshot()

        assertEquals(
            listOf(Tag("1", "맛집", "#FF0000"), Tag("2", "카페", "#00FF00")),
            tags
        )
    }

    @Test
    fun `태그를 추가할 때 id를 제외한 요청 모델로 변환해 보내고 결과를 도메인 모델로 변환한다`() = runTest {
        val request = slot<TagRequest>()
        coEvery { dataSource.addTags(capture(request)) } returns
                Result.success(TagResponse("9", "맛집", "#FF0000"))

        val result = repository.addTags(Tag(id = "ignored", name = "맛집", color = "#FF0000"))

        assertEquals(TagRequest(name = "맛집", colorHex = "#FF0000"), request.captured)
        assertEquals(Tag("9", "맛집", "#FF0000"), result.getOrNull())
    }

    @Test
    fun `태그 추가 실패는 같은 예외로 전달한다`() = runTest {
        val error = NetworkError.UnknownError("실패")
        coEvery { dataSource.addTags(any()) } returns Result.failure(error)

        val result = repository.addTags(Tag("1", "맛집", "#FF0000"))

        assertSame(error, result.exceptionOrNull())
    }
}