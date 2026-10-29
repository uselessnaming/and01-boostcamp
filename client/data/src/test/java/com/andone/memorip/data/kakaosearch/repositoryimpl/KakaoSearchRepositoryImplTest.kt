package com.andone.memorip.data.kakaosearch.repositoryimpl

import androidx.paging.testing.asSnapshot
import com.andone.memorip.data.kakaosearch.datasource.KakaoSearchService
import com.andone.memorip.data.kakaosearch.model.KakaoLocationResponse
import com.andone.memorip.data.kakaosearch.model.KakaoMeta
import com.andone.memorip.data.kakaosearch.model.KakaoSearchLocation
import com.andone.memorip.domain.model.response.KakaoLocation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class KakaoSearchRepositoryImplTest {

    private val service: KakaoSearchService = mockk()
    private val repository = KakaoSearchRepositoryImpl(service)

    @Test
    fun `검색하면 첫 페이지 15건을 요청하고 도메인 모델로 변환해 제공한다`() = runTest {
        coEvery { service.searchLocations("카페", 1, 15) } returns KakaoSearchLocation(
            locations = listOf(KakaoLocationResponse("1", "스타벅스", "카페", "서울 강남구", "서울 강남대로", "127.0", "37.5")),
            meta = KakaoMeta(totalCount = 1, pageableCount = 1, isEnd = true)
        )

        val items = repository.searchLocations("카페").asSnapshot()

        assertEquals(
            listOf(KakaoLocation("1", "스타벅스", "카페", "서울 강남구", "서울 강남대로", "127.0", "37.5")),
            items
        )
        coVerify { service.searchLocations(query = "카페", page = 1, size = 15) }
    }

}