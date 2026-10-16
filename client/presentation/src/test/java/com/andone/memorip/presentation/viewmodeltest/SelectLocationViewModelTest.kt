package com.andone.memorip.presentation.viewmodeltest

import android.content.Context
import androidx.paging.PagingData
import app.cash.turbine.test
import com.andone.memorip.domain.repository.KakaoSearchRepository
import com.andone.memorip.presentation.MainDispatcherRule
import com.andone.memorip.presentation.model.LocationUiModel
import com.andone.memorip.presentation.screen.selectlocation.SelectLocationViewModel
import com.andone.memorip.presentation.screen.selectlocation.model.SelectLocationAction
import com.andone.memorip.presentation.screen.selectlocation.model.SelectLocationEvent
import com.naver.maps.geometry.LatLng
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SelectLocationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val searchRepository: KakaoSearchRepository = mockk()
    private val context: Context = mockk()
    private lateinit var viewModel: SelectLocationViewModel
    private val location = LocationUiModel(
        id = "1",
        name = "스타벅스",
        address = "서울 강남구",
        latitude = 37.5,
        longitude = 127.0
    )

    @Before
    fun setUp() {
        coEvery { searchRepository.searchLocations(any()) } returns flowOf(PagingData.empty())
        viewModel = SelectLocationViewModel(searchRepository)
    }

    @Test
    fun `초기 상태 확인`() {
        val state = viewModel.uiState.value

        assertEquals("", state.query)
        assertNull(state.location)
    }

    @Test
    fun `쿼리를 변경하면 uiState의 query가 갱신된다`() {
        viewModel.onAction(SelectLocationAction.OnQueryChange("카페"))

        assertEquals("카페", viewModel.uiState.value.query)
    }

    @Test
    fun `장소를 클릭하면 선택된 location이 갱신된다`() {
        viewModel.onAction(SelectLocationAction.OnLocationClick(location))

        assertEquals(location, viewModel.uiState.value.location)
    }

    @Test
    fun `null 장소를 클릭하면 선택이 해제된다`() {
        viewModel.onAction(SelectLocationAction.OnLocationClick(location))
        viewModel.onAction(SelectLocationAction.OnLocationClick(null))

        assertNull(viewModel.uiState.value.location)
    }

    @Test
    fun `지도를 클릭하면 좌표만 가진 location이 만들어진다`() {
        viewModel.onAction(SelectLocationAction.OnMapClick(LatLng(37.1, 127.1)))

        assertEquals(
            LocationUiModel(latitude = 37.1, longitude = 127.1),
            viewModel.uiState.value.location
        )
    }

    @Test
    fun `주소가 있는 장소를 선택하면 그대로 SelectLocation 이벤트가 발생한다`() = runTest {
        viewModel.event.test {
            viewModel.onAction(SelectLocationAction.OnLocationSelect(context, location))

            assertEquals(SelectLocationEvent.SelectLocation(location), awaitItem())
        }
    }

    @Test
    fun `검색어를 입력해도 디바운스 시간 전에는 검색하지 않는다`() = runTest {
        backgroundScope.launch { viewModel.locationsPagingFlow.collect{ } }

        viewModel.onAction(SelectLocationAction.OnQueryChange("카페"))
        advanceTimeBy(499)
        runCurrent()

        coVerify(exactly = 0) { searchRepository.searchLocations(any()) }
    }

    @Test
    fun `검색어 입력 후 500ms가 지나면 해당 검색어로 검색한다`() = runTest {
        backgroundScope.launch { viewModel.locationsPagingFlow.collect{  } }

        viewModel.onAction(SelectLocationAction.OnQueryChange("카페"))
        advanceTimeBy(500)
        runCurrent()

        coVerify(exactly = 1) { searchRepository.searchLocations("카페") }
    }

    @Test
    fun `검색어가 빈 문자열이면 검색하지 않는다`() = runTest {
        backgroundScope.launch { viewModel.locationsPagingFlow.collect{  } }

        viewModel.onAction(SelectLocationAction.OnQueryChange(" "))
        advanceTimeBy(500)
        runCurrent()

        coVerify(exactly = 0) { searchRepository.searchLocations(any()) }
    }

    @Test
    fun `디바운스 시간 안에 연속 입력하면 마지막 검색어로만 검색한다`() = runTest {
        backgroundScope.launch { viewModel.locationsPagingFlow.collect{  } }

        viewModel.onAction(SelectLocationAction.OnQueryChange("카"))
        advanceTimeBy(200)
        viewModel.onAction(SelectLocationAction.OnQueryChange("카페"))
        advanceTimeBy(500)
        runCurrent()

        coVerify(exactly = 0) { searchRepository.searchLocations("카") }
        coVerify(exactly = 1) { searchRepository.searchLocations("카페") }
    }
}