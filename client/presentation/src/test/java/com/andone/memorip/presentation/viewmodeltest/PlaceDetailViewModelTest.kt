package com.andone.memorip.presentation.viewmodeltest

import app.cash.turbine.test
import com.andone.memorip.domain.model.request.Address
import com.andone.memorip.domain.model.response.TripCompact
import com.andone.memorip.domain.model.response.PlaceDetail as PlaceDetailModel
import com.andone.memorip.domain.repository.PlaceRepository
import com.andone.memorip.navigation.PlaceDetail
import com.andone.memorip.presentation.MainDispatcherRule
import com.andone.memorip.presentation.screen.placedetail.PlaceDetailViewModel
import com.andone.memorip.presentation.screen.placedetail.model.PlaceDetailAction
import com.andone.memorip.presentation.screen.placedetail.model.PlaceDetailEvent
import com.andone.memorip.presentation.screen.placedetail.model.TripCompactUiModel
import com.andone.memorip.presentation.util.snackbar.SnackBarEvent
import com.andone.memorip.presentation.util.snackbar.SnackBarManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlaceDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val placeRepository: PlaceRepository = mockk()
    private val snackBarManager: SnackBarManager = mockk(relaxed = true)
    private lateinit var viewModel: PlaceDetailViewModel

    private val detail = PlaceDetailModel(
        placeId = "p1",
        writerId = "w1",
        title = "제목",
        content = null,
        latitude = 37.5,
        longitude = 127.0,
        tags = emptyList(),
        images = listOf("i1", "i2"),
        thumbnailImageRatio = 1f,
        trips = listOf(TripCompact(tripId = "t1", tripName = "제주 여행")),
        address = Address(fullAddress = "서울 강남구", region1Depth = "서울"),
        isMine = true,
        isInMyGroup = false,
        isPublic = true
    )

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }

    @Before
    fun setUp() {
        coEvery { placeRepository.getPlaceDetail("p1") } returns Result.success(detail)
        viewModel = PlaceDetailViewModel(PlaceDetail("p1"), placeRepository, snackBarManager)
    }

    @Test
    fun `구독 전 초기 상태를 로딩 중이다`() {
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `조회에 성공하면 장소 정보가 UI 모델로 변환되어 로딩이 끝난다`() = runTest {
        collectState()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("p1", state.place.id)
        assertEquals("제목", state.place.title)
        assertEquals("서울 강남구", state.place.locationName)
        assertEquals("", state.place.content)
        assertEquals(listOf("i1", "i2"), state.place.imageUrls)
        assertEquals(listOf(TripCompactUiModel("t1", "제주 여행")), state.place.trips)
        assertTrue(state.place.isMine)
        assertFalse(state.place.isInMyTrip)
    }

    @Test
    fun `조회에 실패하면 에러 스낵바를 보여주고 뒤로 이동한다`() = runTest {
        coEvery { placeRepository.getPlaceDetail("p1") } returns Result.failure(RuntimeException())
        collectState()

        viewModel.event.test {
            assertEquals(PlaceDetailEvent.NavigateBack, awaitItem())
        }
        assertFalse(viewModel.uiState.value.isLoading)
        verify { snackBarManager.show(SnackBarEvent.NETWORK_ERROR) }
    }

    @Test
    fun `새로 고침을 요청하면 장소 상세를 다시 조회한다`() = runTest {
        collectState()

        viewModel.onAction(PlaceDetailAction.OnRefreshRequested)

        coVerify(exactly = 2) { placeRepository.getPlaceDetail("p1") }
    }

    @Test
    fun `클릭 액션들은 대응되는 이벤트를 발생시킨다`() = runTest {
        viewModel.event.test {
            viewModel.onAction(PlaceDetailAction.OnBackClick)
            assertEquals(PlaceDetailEvent.NavigateBack, awaitItem())

            viewModel.onAction(PlaceDetailAction.OnAddToTripClick)
            assertEquals(PlaceDetailEvent.NavigateToSelectTrip, awaitItem())

            viewModel.onAction(PlaceDetailAction.TripClick)
            assertEquals(PlaceDetailEvent.NavigateToTripList, awaitItem())

            viewModel.onAction(PlaceDetailAction.OnEditClick)
            assertEquals(PlaceDetailEvent.NavigateToPlaceEdit, awaitItem())
        }
    }

    @Test
    fun `더보기 메뉴와 삭제 다이얼로그의 열기 닫기 이벤트가 발생한다`() = runTest {
        viewModel.event.test {
            viewModel.onAction(PlaceDetailAction.OnMoreClick)
            assertEquals(PlaceDetailEvent.ShowMoreMenu, awaitItem())

            viewModel.onAction(PlaceDetailAction.OnMoreMenuDismiss)
            assertEquals(PlaceDetailEvent.HideMoreMenu, awaitItem())

            viewModel.onAction(PlaceDetailAction.OnDeleteClick)
            assertEquals(PlaceDetailEvent.ShowDeleteDialog, awaitItem())

            viewModel.onAction(PlaceDetailAction.OnDeleteDismiss)
            assertEquals(PlaceDetailEvent.HideDeleteDialog, awaitItem())
        }
    }

    @Test
    fun `삭제를 확정하면 다이얼로그를 닫고 삭제 성공 시 성공 스낵바와 뒤로 이동 이벤트가 발생한다`() = runTest {
        coEvery { placeRepository.deletePlace("p1") } returns Result.success(Unit)

        viewModel.event.test {
            viewModel.onAction(PlaceDetailAction.OnDeleteConfirm)

            assertEquals(PlaceDetailEvent.HideDeleteDialog, awaitItem())
            assertEquals(PlaceDetailEvent.NavigateBack, awaitItem())
        }
        coVerify(exactly = 1) { placeRepository.deletePlace("p1") }
        verify { snackBarManager.show(SnackBarEvent.SUCCESS) }
    }

    @Test
    fun `삭제에 실패하면 에러 스낵바를 보여주고 뒤로 이동하지 않는다`() = runTest {
        coEvery { placeRepository.deletePlace("p1") } returns Result.failure(RuntimeException())

        viewModel.event.test {
            viewModel.onAction(PlaceDetailAction.OnDeleteConfirm)

            assertEquals(PlaceDetailEvent.HideDeleteDialog, awaitItem())
            expectNoEvents()
        }
        verify { snackBarManager.show(SnackBarEvent.UNKNOWN_ERROR) }
    }
}