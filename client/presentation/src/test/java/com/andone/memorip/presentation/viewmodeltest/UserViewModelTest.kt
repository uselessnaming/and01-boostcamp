package com.andone.memorip.presentation.viewmodeltest

import app.cash.turbine.test
import com.andone.memorip.domain.auth.AuthError
import com.andone.memorip.domain.auth.TokenRefresher
import com.andone.memorip.domain.model.User
import com.andone.memorip.domain.repository.AuthRepository
import com.andone.memorip.domain.repository.UserRepository
import com.andone.memorip.presentation.MainDispatcherRule
import com.andone.memorip.presentation.screen.user.UserViewModel
import com.andone.memorip.presentation.screen.user.model.LoginMethod
import com.andone.memorip.presentation.screen.user.model.UserAction
import com.andone.memorip.presentation.screen.user.model.UserEvent
import com.andone.memorip.presentation.screen.user.model.UserUiModel
import com.andone.memorip.presentation.util.snackbar.SnackBarEvent
import com.andone.memorip.presentation.util.snackbar.SnackBarManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class UserViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()
    private val tokenRefresher: TokenRefresher = mockk()
    private val userRepository: UserRepository = mockk()
    private val snackBarManager: SnackBarManager = mockk(relaxed = true)

    private val me = User(id = "1", nickname = "닉네임", profileImg = "img")

    private fun createViewModel(vararg loggedIn: Boolean): UserViewModel {
        every { authRepository.isLoggedIn() } returnsMany loggedIn.toList()
        coEvery { tokenRefresher.refreshToken(any()) } returns null
        coEvery { userRepository.getMe() } returns Result.success(me)
        coEvery { authRepository.getEmail() } returns Result.success("a@b.c")
        coEvery { authRepository.signOut() } just runs
        return UserViewModel(authRepository, tokenRefresher, userRepository, snackBarManager)
    }

    @Test
    fun `초기 로그인 여부는 AuthRepository 값을 따른다`() {
        assertTrue(createViewModel(true).uiState.value.isLoggedIn)
        assertFalse(createViewModel(false).uiState.value.isLoggedIn)
    }

    @Test
    fun `입력 값과 로그인 모드 변경이 상태에 반영된다`() {
        val viewModel = createViewModel(false)

        viewModel.onAction(UserAction.UpdateEmail("a@b.c"))
        viewModel.onAction(UserAction.UpdatePassword("pw"))
        viewModel.onAction(UserAction.UpdatePasswordConfirm("pw2"))
        viewModel.onAction(UserAction.ToggleLoginMode)

        val state = viewModel.uiState.value
        assertEquals("a@b.c", state.email)
        assertEquals("pw", state.password)
        assertEquals("pw2", state.passwordConfirm)
        assertTrue(state.isNewAccount)

        viewModel.onAction(UserAction.ToggleLoginMode)
        assertFalse(viewModel.uiState.value.isNewAccount)
    }

    @Test
    fun `위치 권한 결과가 상태에 반영된다`() {
        val viewModel = createViewModel(false)

        viewModel.onAction(UserAction.OnLocationPermissionResult(granted = true))

        assertTrue(viewModel.uiState.value.permissionUiState.locationPermission)
    }

    @Test
    fun `구글 방식을 선택하면 구글 로그인 요청 이벤트가 발생한다`() = runTest {
        val viewModel = createViewModel(false)

        viewModel.event.test {
            viewModel.onAction(UserAction.OnMethodClick(LoginMethod.GOOGLE))

            assertEquals(UserEvent.RequestGoogleLogin, awaitItem())
        }
    }

    @Test
    fun `이메일 방식을 선택하면 로그인 다이얼로그가 열리고 닫을 수 있다`() {
        val viewModel = createViewModel(false)

        viewModel.onAction(UserAction.OnMethodClick(LoginMethod.EMAIL))
        assertTrue(viewModel.uiState.value.showLoginDialog)

        viewModel.onAction(UserAction.CloseLoginDialog)
        assertFalse(viewModel.uiState.value.showLoginDialog)
    }

    @Test
    fun `전화 방식을 선택하면 아무 일도 일어나지 않는다`() = runTest {
        val viewModel = createViewModel(false)
        val before = viewModel.uiState.value

        viewModel.event.test {
            viewModel.onAction(UserAction.OnMethodClick(LoginMethod.PHONE))

            expectNoEvents()
        }

        assertEquals(before, viewModel.uiState.value)
    }

    @Test
    fun `구글 로그인에 성공하면 로그인 상태가 되고 토큰 갱신 후 사용자 정보를 불러온다`() {
        coEvery { authRepository.signInWithGoogle("token") } returns Result.success(Unit)
        val viewModel = createViewModel(false)

        viewModel.onAction(UserAction.GoogleLoginSuccess("token"))

        val state = viewModel.uiState.value
        assertTrue(state.isLoggedIn)
        assertFalse(state.isLoading)
        assertEquals(
            UserUiModel(id = "1", name = "닉네임", profileImgUrl = "img", email = "a@b.c"),
            state.user
        )
        coVerify { tokenRefresher.refreshToken(true) }
    }

    @Test
    fun `구글 로그인에 실패하면 에러 스낵바를 보여주고 로그인되지 않는다`() {
        coEvery { authRepository.signInWithGoogle(any()) } returns Result.failure(AuthError.Network())
        val viewModel = createViewModel(false)

        viewModel.onAction(UserAction.GoogleLoginSuccess("token"))

        verify { snackBarManager.show(SnackBarEvent.NETWORK_ERROR) }
        assertFalse(viewModel.uiState.value.isLoggedIn)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `기존 계정 모드에서는 이메일 로그인을 호출하고 다이얼로그를 닫는다`() {
        coEvery { authRepository.signInWithEmail("a@b.c", "pw") } returns Result.success(Unit)
        val viewModel = createViewModel(false)
        viewModel.onAction(UserAction.OnMethodClick(LoginMethod.EMAIL))

        viewModel.onAction(UserAction.EmailLoginSubmit("a@b.c", "pw"))

        coVerify(exactly = 1) { authRepository.signInWithEmail("a@b.c", "pw") }
        coVerify(exactly = 0) { authRepository.signUpWithEmail(any(), any()) }
    }

    @Test
    fun `이메일 로그인 실패 시 에러에 맞는 스낵바를 보여주고 로딩을 끝낸다`() {
        coEvery {
            authRepository.signInWithEmail(
                any(),
                any()
            )
        } returns Result.failure(AuthError.WrongPassword())
        val viewModel = createViewModel(false)

        viewModel.onAction(UserAction.EmailLoginSubmit("a@b.c", "pw"))

        verify { snackBarManager.show(SnackBarEvent.WRONG_PASSWORD) }
        assertFalse(viewModel.uiState.value.isLoggedIn)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `인증 상태 갱신 시 로그인 상태면 사용자 정보를 불러온다`() {
        val viewModel = createViewModel(false, true)

        viewModel.onAction(UserAction.RefreshAuthState)

        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("닉네임", viewModel.uiState.value.user.name)
        coVerify { tokenRefresher.refreshToken(true) }
    }

    @Test
    fun `인증 상태 갱신 시 로그인 상태가 아니면 사용자 정보를 불러오지 않는다`() {
        val viewModel = createViewModel(false, false)

        viewModel.onAction(UserAction.RefreshAuthState)

        assertFalse(viewModel.uiState.value.isLoggedIn)
        coVerify(exactly = 0) { userRepository.getMe() }
    }

    @Test
    fun `로그아웃하면 signOut 후 로그아웃 사앹로 갱신된다`() {
        val viewModel = createViewModel(true, false)

        viewModel.onAction(UserAction.SignOut)

        coVerify(exactly = 1) { authRepository.signOut() }
        assertFalse(viewModel.uiState.value.isLoggedIn)
    }

    @Test
    fun `계정을 삭제하면 deleteAccount 후 상태를 갱신한다`() {
        coEvery { authRepository.deleteAccount() } returns Result.success(Unit)
        val viewModel = createViewModel(true, false)

        viewModel.onAction(UserAction.DeleteAccount)

        coVerify(exactly = 1) { authRepository.deleteAccount() }
        assertFalse(viewModel.uiState.value.isLoggedIn)
    }

    @Test
    fun `사용자 정보 조회에 실패해도 로그인 상태는 유지되고 사용자 정보는 비어 있다`() {
        coEvery { authRepository.signInWithGoogle(any()) } returns Result.success(Unit)
        val viewModel = createViewModel(false)
        coEvery { userRepository.getMe() } returns Result.failure(RuntimeException())

        viewModel.onAction(UserAction.GoogleLoginSuccess("token"))

        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertEquals(UserUiModel.empty(), viewModel.uiState.value.user)
    }

    @Test
    fun `이메일 조회에 실패하면 사용자 이메일은 null로 남는다`() {
        coEvery { authRepository.signInWithGoogle(any()) } returns Result.success(Unit)
        val viewModel = createViewModel(false)
        coEvery { authRepository.getEmail() } returns Result.failure(RuntimeException())

        viewModel.onAction(UserAction.GoogleLoginSuccess("token"))

        assertEquals(null, viewModel.uiState.value.user.email)
    }
}