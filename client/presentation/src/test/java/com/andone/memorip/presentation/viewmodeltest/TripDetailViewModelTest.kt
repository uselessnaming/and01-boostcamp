package com.andone.memorip.presentation.viewmodeltest

import android.content.Context
import androidx.paging.PagingData
import app.cash.turbine.test
import com.andone.memorip.domain.model.Trip
import com.andone.memorip.domain.model.Visibility
import com.andone.memorip.domain.repository.TripRepository
import com.andone.memorip.navigation.TripDetail
import com.andone.memorip.presentation.MainDispatcherRule
import com.andone.memorip.presentation.model.Place
import com.andone.memorip.presentation.model.TripUiModel
import com.andone.memorip.presentation.screen.tripdetail.TripDetailViewModel
import com.andone.memorip.presentation.screen.tripdetail.model.MapBottomSheetStep
import com.andone.memorip.presentation.screen.tripdetail.model.PlaceViewMode
import com.andone.memorip.presentation.screen.tripdetail.model.TripDetailAction
import com.andone.memorip.presentation.screen.tripdetail.model.TripDetailEvent
import com.naver.maps.map.Projection
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TripDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = mockk(relaxed = true)
    private val repository: TripRepository = mockk()
    private lateinit var viewModel: TripDetailViewModel

    private val trip = Trip(
        id = "t1",
        owner = mockk(relaxed = true),
        title = "제주 여행",
        visibility = Visibility.PRIVATE,
        createdAt = "",
        updatedAt = "",
        images = listOf("img")
    )

    private val place: Place = mockk(relaxed = true)

    private fun createViewModel() = TripDetailViewModel(route = TripDetail("t1"), context = context, repository = repository)

    @Before
    fun setUp() {
        every { repository.getTripPlaces("t1") } returns flowOf(PagingData.empty())
        coEvery { repository.getTripById("t1") } returns Result.success(trip)
        viewModel = createViewModel()
    }

    @Test
    fun `생성 시 여행 정보를 불러와 상태에 반영한다`() {
        val state = viewModel.uiState.value

        assertEquals(TripUiModel(id = "t1", name = "제주 여행", images = listOf("img")), state.tripInfo)
        assertEquals("제주 여행", state.tripName)
    }

    @Test
    fun `여행 정보 조회에 실패하면 tripInfo는 null이고 이름은 빈 문자열이다`() {
        coEvery { repository.getTripById("t1") } returns Result.failure(RuntimeException())

        viewModel = createViewModel()

        assertNull(viewModel.uiState.value.tripInfo)
        assertEquals("", viewModel.uiState.value.tripName)
    }

    @Test
    fun `메뉴 클릭 시 expanded가 true가 된다`() {
        viewModel.onAction(TripDetailAction.OnMenuClick)

        assertTrue(viewModel.uiState.value.expanded)
    }

    @Test
    fun `뒤로가기 클릭 시 NavigateBack 이벤트가 발생한다`() = runTest {
        viewModel.event.test {
            viewModel.onAction(TripDetailAction.OnBackClick)

            assertEquals(TripDetailEvent.NavigateBack, awaitItem())
        }
    }

    @Test
    fun `검색 클릭은 현재 상태를 바꾸지 않는다`() {
        val before = viewModel.uiState.value

        viewModel.onAction(TripDetailAction.OnSearchClick)

        assertEquals(before, viewModel.uiState.value)
    }

    @Test
    fun `보기 모드 토글은 List와 Grid를 번갈아 바꾼다`() {
        assertEquals(PlaceViewMode.LIST, viewModel.uiState.value.placeViewMode)

        viewModel.onAction(TripDetailAction.OnViewModeToggle)
        assertEquals(PlaceViewMode.GRID, viewModel.uiState.value.placeViewMode)

        viewModel.onAction(TripDetailAction.OnViewModeToggle)
        assertEquals(PlaceViewMode.LIST, viewModel.uiState.value.placeViewMode)
    }

    @Test
    fun `장소를 클릭하면 선택 장소가 저장되고 상세 단계로 전환된다`() {
        viewModel.onAction(TripDetailAction.OnPlaceClick(place))

        val state = viewModel.uiState.value
        assertEquals(place, state.mapSelectedPlace)
        assertEquals(MapBottomSheetStep.PlaceDetail, state.mapBottomSheetContent)
    }

    @Test
    fun `상세 바텀시트를 닫으면 선택이 해제되고 목록 단계로 돌아간다`() {
        viewModel.onAction(TripDetailAction.OnPlaceClick(place))

        viewModel.onAction(TripDetailAction.OnPlaceDetailBottomSheetClose)

        val state = viewModel.uiState.value
        assertNull(state.mapSelectedPlace)
        assertEquals(MapBottomSheetStep.PlaceList, state.mapBottomSheetContent)
    }

    @Test
    fun `장소 상세로 이동하면 바텀시트 상태를 초기화하고 이동 이벤트를 보낸다`() = runTest {
        viewModel.onAction(TripDetailAction.OnPlaceClick(place))

        viewModel.event.test {
            viewModel.onAction(TripDetailAction.OnNavigateToPlaceDetail("p1"))

            assertEquals(TripDetailEvent.NavigateToPlaceDetail("p1"), awaitItem())
        }
        assertNull(viewModel.uiState.value.mapSelectedPlace)
        assertEquals(MapBottomSheetStep.PlaceList, viewModel.uiState.value.mapBottomSheetContent)
    }

    @Test
    fun `지도 초기화 액션을 받으면 isMapInitialized가 true가 된다`() {
        assertFalse(viewModel.isMapInitialized.value)

        viewModel.onAction(TripDetailAction.OnMapInitialized)

        assertTrue(viewModel.isMapInitialized.value)
    }

    @Test
    fun `카메라 변경 시 새 projection과 zoom이 저장된다`() {
        val projection: Projection = mockk()

        viewModel.onAction(TripDetailAction.OnMapCameraChange(projection, 12.0))

        assertEquals(projection, viewModel.projectionStateFlow.value)
        assertEquals(12.0, viewModel.zoomStateFlow.value, 0.0)
    }

    @Test
    fun `같은 projection에서 Zoom만 바뀌어도 zoom이 갱신된다`() {
        val projection: Projection = mockk()
        viewModel.onAction(TripDetailAction.OnMapCameraChange(projection, 12.0))

        viewModel.onAction(TripDetailAction.OnMapCameraChange(projection, 14.0))

        assertEquals(14.0, viewModel.zoomStateFlow.value, 0.0)
    }
}