package com.andone.memorip.data.tag.datasource.remote

import com.andone.memorip.data.common.ApiResult
import com.andone.memorip.data.tag.datasource.TagService
import com.andone.memorip.data.tag.model.TagRequest
import com.andone.memorip.data.tag.model.TagResponse
import com.andone.memorip.domain.model.NetworkError
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class TagRemoteDataSourceImplTest {
    private val service: TagService = mockk()
    private val dataSource = TagRemoteDataSourceImpl(service)
    private val request = TagRequest(name = "맛집", colorHex = "#FF0000")

    @Test
    fun `태그 추가에 성공하면 응답 data를 반환한다`() = runTest {
        val response = TagResponse("1", "맛집", "#FF0000")
        coEvery { service.addTag(request) } returns ApiResult(data = response)

        assertEquals(response, dataSource.addTags(request).getOrNull())
    }

    @Test
    fun `서버가 404를 반환하면 NotFoundError로 실패한다`() = runTest {
        val http = HttpException(
            Response.error<Any>(404, "".toResponseBody("application/json".toMediaType()))
        )
        coEvery { service.addTag(any()) } throws http

        val error = dataSource.addTags(request).exceptionOrNull()

        assertTrue(error is NetworkError.NotFoundError)
    }
}