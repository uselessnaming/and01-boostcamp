package com.andone.memorip.data.utiltest

import com.andone.memorip.data.common.ApiResult
import com.andone.memorip.data.util.apiCall
import com.andone.memorip.domain.model.NetworkError
import com.andone.memorip.domain.model.UnexpectedError
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ApiCallTest {

    private fun httpException(code: Int) = HttpException(
        Response.error<Any>(code, "".toResponseBody("application/json".toMediaType()))
    )

    @Test
    fun `성공하면 data를 담은 성공 결과를 반환한다`() = runTest {
        val result = apiCall { ApiResult(data = "ok") }

        assertEquals("ok", result.getOrNull())
    }

    @Test
    fun `HTTP 에러 코드에 따라 대응도는 NetworkError로 변환한다`() = runTest {
        val expected = listOf(
            400 to NetworkError.InvalidRequestError::class,
            404 to NetworkError.NotFoundError::class,
            500 to NetworkError.InternalServerError::class,
            401 to NetworkError.UnknownError::class,
            403 to NetworkError.UnknownError::class,
            503 to NetworkError.UnknownError::class,
        )

        expected.forEach { (code, type) ->
            val error = apiCall<String> { throw httpException(code) }.exceptionOrNull()

            assertEquals("HTTP $code", type, error!!::class)
        }
    }

    @Test
    fun `HTTP가 아닌 예외는 UnexpectedError로 변환하고 메시지를 유지한다`() = runTest {
        val error = apiCall<String> { throw IOException("timeout") }.exceptionOrNull()

        assertTrue(error is UnexpectedError)
        assertEquals("timeout", error!!.message)
    }

    @Test
    fun `응답 data가 null이면 UnexpectedError로 실패한다`() = runTest {
        val error = apiCall<String>{ ApiResult(data = null) }.exceptionOrNull()

        assertTrue(error is UnexpectedError)
    }

    @Test
    fun `mapper가 있으면 변환한 값을 반환한다`() = runTest {
        val result = apiCall(
            call = { ApiResult(data = 21) },
            mapper = { it * 2 }
        )

        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `mapper 버전도 HTTP 에러를 NetworkError로 변환한다`() = runTest {
        val error = apiCall<Int, Int>(
            call = { throw httpException(404) },
            mapper = { it }
        ).exceptionOrNull()

        assertTrue(error is NetworkError.NotFoundError)
    }

    @Test
    fun `mapper가 예외로 던지면 UnexpectedError로 실패한다`() = runTest {
        val error = apiCall(
            call = { ApiResult(data = 1) },
            mapper = { _: Int -> error("변환 실패") }
        ).exceptionOrNull()

        assertTrue(error is UnexpectedError)
        assertEquals("변환 실패", error!!.message)
    }

    @Test
    fun `mapper 버전도 data가 null이면 UnexpectedError로 실패한다`() = runTest {
        val error = apiCall<Int, Int>(
            call = { ApiResult(data = null) },
            mapper = { it }
        ).exceptionOrNull()

        assertTrue(error is UnexpectedError)
    }
}