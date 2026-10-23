package com.andone.memorip.presentation.modeltest

import com.andone.memorip.domain.auth.AuthError
import com.andone.memorip.presentation.util.snackbar.SnackBarEvent
import com.andone.memorip.presentation.util.toSnackBarEvent
import org.junit.Test
import org.junit.Assert.assertEquals

class ThrowableExtensionsTest {

    @Test
    fun `인증 에러는 대응되는 스낵바 이벤트로 변환된다`() {
        val expected = mapOf<Throwable, SnackBarEvent>(
            AuthError.EmailAlreadyExists() to SnackBarEvent.EMAIL_ALREADY_EXISTS,
            AuthError.InvalidEmail() to SnackBarEvent.INVALID_EMAIL,
            AuthError.WeakPassword() to SnackBarEvent.WEAK_PASSWORD,
            AuthError.UserNotFound() to SnackBarEvent.USER_NOT_FOUND,
            AuthError.WrongPassword() to SnackBarEvent.WRONG_PASSWORD,
            AuthError.Network() to SnackBarEvent.NETWORK_ERROR
        )

        expected.forEach { (error, event) ->
            assertEquals(event, error.toSnackBarEvent())
        }
    }

    @Test
    fun `알 수 없는 인증 에러와 일반 예외는 알 수 없는 에러 이벤트가 된다`() {
        assertEquals(SnackBarEvent.UNKNOWN_ERROR, AuthError.Unknown().toSnackBarEvent())
        assertEquals(SnackBarEvent.UNKNOWN_ERROR, RuntimeException().toSnackBarEvent())
    }
}