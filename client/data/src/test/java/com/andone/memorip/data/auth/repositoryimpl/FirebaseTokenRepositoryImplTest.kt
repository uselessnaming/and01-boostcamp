package com.andone.memorip.data.auth.repositoryimpl

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FirebaseTokenRepositoryImplTest {

    private val firebaseAuth: FirebaseAuth = mockk()
    private val user: FirebaseUser = mockk()
    private val task: Task<GetTokenResult> = mockk()
    private val tokenResult: GetTokenResult = mockk()
    private val repository = FirebaseTokenRepositoryImpl(firebaseAuth)

    @Before
    fun setUp() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @After
    fun teardown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @Test
    fun `갱신 전에는 토큰이 없다`() {
        assertNull(repository.getAccessToken())
    }

    @Test
    fun `토큰을 갱신하면 캐시에 저장하고 반환한다`() = runTest {
        every { firebaseAuth.currentUser } returns user
        every { user.getIdToken(false) } returns task
        coEvery { task.await() } returns tokenResult
        every { tokenResult.token } returns "tok"

        assertEquals("tok", repository.refreshToken(false))
        assertEquals("tok", repository.getAccessToken())
    }

    @Test
    fun `force 값은 getIdToken에 그대로 전달된다`() = runTest {
        every { firebaseAuth.currentUser } returns user
        every { user.getIdToken(true) } returns task
        coEvery { task.await() } returns tokenResult
        every { tokenResult.token } returns "fresh"

        assertEquals("fresh", repository.refreshToken(true))
    }

    @Test
    fun `로그인된 사용자가 없으면 캐시를 비우고 null을 반환한다`() = runTest {
        every { firebaseAuth.currentUser } returns user
        every { user.getIdToken(false) } returns task
        coEvery { task.await() } returns tokenResult
        every { tokenResult.token } returns "tok"
        repository.refreshToken(false)

        every { firebaseAuth.currentUser } returns null

        assertNull(repository.refreshToken(false))
        assertNull(repository.getAccessToken())
    }
}