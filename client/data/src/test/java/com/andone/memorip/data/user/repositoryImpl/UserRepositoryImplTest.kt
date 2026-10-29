package com.andone.memorip.data.user.repositoryImpl

import com.andone.memorip.data.user.datasource.remote.UserRemoteDataSource
import com.andone.memorip.data.user.repositoryimpl.UserRepositoryImpl
import com.andone.memorip.domain.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UserRepositoryImplTest {
    private val dataSource: UserRemoteDataSource = mockk()
    private val repository = UserRepositoryImpl(dataSource)
    private val user = User(id = "1", nickname = "닉네임", profileImg = "img")

    @Test
    fun `내 정보 조회는 데이터소스 결과를 그대로 반환한다`() = runTest {
        coEvery { dataSource.getMe() } returns Result.success(user)

        assertEquals(user, repository.getMe().getOrNull())
    }

    @Test
    fun `닉네임 변경은 이름을 전달하고 결과를 그대로 반환한다`() = runTest {
        val error = RuntimeException("실패")
        coEvery { dataSource.updateNickname("새 닉네임") } returns Result.failure(error)

        val result = repository.updateNickname("새 닉네임")

        assertSame(error, result.exceptionOrNull())
        coVerify(exactly = 1) { dataSource.updateNickname("새 닉네임") }
    }
}