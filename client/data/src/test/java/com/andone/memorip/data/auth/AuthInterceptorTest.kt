package com.andone.memorip.data.auth

import com.andone.memorip.domain.auth.TokenProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class AuthInterceptorTest {

    private val tokenProvider: TokenProvider = mockk()
    private val chain: Interceptor.Chain = mockk()
    private val response: Response = mockk()
    private val interceptor = AuthInterceptor(tokenProvider)
    private val original = Request.Builder().url("https://example.com/api").build()
    private val proceeded = slot<Request>()

    private fun stubChain() {
        every { chain.request() } returns original
        every { chain.proceed(capture(proceeded)) } returns response
    }

    @Test
    fun `토큰이 있으면 Authorization Bearer 헤더를 추가한다`() {
        stubChain()
        every { tokenProvider.getAccessToken() } returns "abc"

        val result = interceptor.intercept(chain)

        assertSame(response, result)
        assertEquals("Bearer abc", proceeded.captured.header("Authorization"))
        assertEquals(original.url, proceeded.captured.url)
    }

    @Test
    fun `토큰이 없으면 원본 요청을 그대로 보낸다`() {
        stubChain()
        every { tokenProvider.getAccessToken() } returns null

        interceptor.intercept(chain)

        assertSame(original, proceeded.captured)
        assertNull(proceeded.captured.header("Authorization"))
    }
}