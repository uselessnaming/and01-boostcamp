package com.andone.memorip.presentation.viewmodeltest

import app.cash.turbine.test
import com.andone.memorip.domain.model.Trip
import com.andone.memorip.domain.model.Visibility
import com.andone.memorip.domain.repository.TripRepository
import com.andone.memorip.presentation.MainDispatcherRule
import com.andone.memorip.presentation.model.TripUiModel
import com.andone.memorip.presentation.screen.triplist.TripListViewModel
import com.andone.memorip.presentation.screen.triplist.model.TripListAction
import com.andone.memorip.presentation.screen.triplist.model.TripListEvent
import com.andone.memorip.presentation.util.snackbar.SnackBarEvent
import com.andone.memorip.presentation.util.snackbar.SnackBarManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class TripListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tripRepository: TripRepository = mockk()
    private val snackBarManager: SnackBarManager = mockk(relaxed = true)
    private lateinit var viewModel: TripListViewModel

    private fun trip(id: String, title: String = "여행$id", images: List<String> = emptyList()) =
        Trip(
            id = id,
            owner = mockk(relaxed = true),
            title = title,
            visibility = Visibility.PRIVATE,
            createdAt = "",
            updatedAt = "",
            images = images
        )

    @Before
    fun setUp() {
        coEvery { tripRepository.myTrips } returns flowOf(emptyList())
        coEvery { tripRepository.fetchMyTrips(any(), any(), any()) } returns Result.success(Unit)
        viewModel = TripListViewModel(tripRepository, snackBarManager)
    }

    @Test
    fun `구독하면 내 여행을 불러와 목록에 반영하고 로딩이 끝난다`() = runTest {
        coEvery { tripRepository.myTrips } returns flowOf(listOf(trip("1", images = listOf("a"))))
        viewModel = TripListViewModel(tripRepository, snackBarManager)

        collectState()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(
            listOf(TripUiModel(id = "1", name = "여행1", images = listOf("a"))),
            state.trips
        )
    }

    @Test
    fun `초기 로딩에 실패하면 로딩이 끝나고 네트워크 에러 스낵바를 보여준다`() = runTest {
        coEvery { tripRepository.fetchMyTrips(any(), any(), any()) } returns Result.failure(
            RuntimeException()
        )

        collectState()

        assertFalse(viewModel.uiState.value.isLoading)
        verify { snackBarManager.show(SnackBarEvent.NETWORK_ERROR) }
    }

    @Test
    fun `FAB 클릭 시 장소 생성 화면으로 이동 이벤트가 발생한다`() = runTest {
        collectState()

        viewModel.event.test {
            viewModel.onAction(TripListAction.OnFABClick)

            assertEquals(TripListEvent.NavigateToPlaceCreate, awaitItem())
        }
    }

    @Test
    fun `장소가 있는 여행을 클릭하면 상세 화면 이동 이벤트가 발생한다`() = runTest {
        coEvery { tripRepository.myTrips } returns flowOf(listOf(trip("1", images = listOf("a"))))
        viewModel = TripListViewModel(tripRepository, snackBarManager)
        collectState()

        viewModel.event.test {
            viewModel.onAction(TripListAction.OnTripClick("1"))

            assertEquals(TripListEvent.NavigateToTripDetail("1"), awaitItem())
        }
    }

    @Test
    fun `장소가 없는 여행을 클릭하면 스낵바만 보여주고 이동하지 않는다`() = runTest {
        coEvery { tripRepository.myTrips } returns flowOf(listOf(trip("1")))
        viewModel = TripListViewModel(tripRepository, snackBarManager)
        collectState()

        viewModel.event.test {
            viewModel.onAction(TripListAction.OnTripClick("1"))

            expectNoEvents()
        }
        verify { snackBarManager.show(SnackBarEvent.TRIP_NO_PLACES) }
    }

    @Test
    fun `새 여행 만들기를 누르면 다이얼로그가 열리고 이름이 초기화된다`() = runTest {
        collectState()
        viewModel.onAction(TripListAction.OnNewTripNameChange("이전 입력"))
        viewModel.onAction(TripListAction.OnCreateNewTripClick)

        assertTrue(viewModel.uiState.value.isCreateTripDialogVisible)
        assertEquals("", viewModel.uiState.value.newTripName)
    }

    @Test
    fun `다이얼로그를 닫으면 다이얼로그가 숨겨지고 이름이 초기화된다`() = runTest {
        collectState()
        viewModel.onAction(TripListAction.OnCreateNewTripClick)
        viewModel.onAction(TripListAction.OnNewTripNameChange("제주"))
        viewModel.onAction(TripListAction.OnDismissCreateTripDialog)

        assertFalse(viewModel.uiState.value.isCreateTripDialogVisible)
        assertEquals("", viewModel.uiState.value.newTripName)
    }

    @Test
    fun `스크롤 방향에 따라 sticky 헤더 노출이 변경된다`() = runTest {
        collectState()

        viewModel.onAction(TripListAction.OnScrollStateChange(isScrollingDown = true))
        assertFalse(viewModel.uiState.value.isStickyHeaderVisible)

        viewModel.onAction(TripListAction.OnScrollStateChange(isScrollingDown = false))
        assertTrue(viewModel.uiState.value.isStickyHeaderVisible)
    }

    @Test
    fun `검색어를 입력하고 300ms가 지나면 해당 검색어로 여행을 조회한다`() = runTest {
        collectState()

        viewModel.onAction(TripListAction.OnSearchQueryChange("제주"))
        advanceTimeBy(300.milliseconds)
        runCurrent()

        assertEquals("제주", viewModel.uiState.value.searchQuery)
        coVerify(exactly = 1) { tripRepository.fetchMyTrips(any(), any(), "제주") }
    }

    @Test
    fun `공백뿐인 이름으로 생성을 확인하면 아무것도 하지 않는다`() = runTest {
        coEvery { tripRepository.createTrip(any(), any()) } returns Result.success(trip("9"))
        collectState()
        viewModel.onAction(TripListAction.OnNewTripNameChange("   "))
        viewModel.onAction(TripListAction.OnConfirmCreateTrip)

        coVerify(exactly = 0) { tripRepository.createTrip(any(), any()) }
    }

    @Test
    fun `여행 생성에 성공하면 다이얼로그가 닫히고 목록을 다시 불러온다`() = runTest {
        coEvery { tripRepository.createTrip(any(), any()) } returns Result.success(trip("9"))
        collectState()
        viewModel.onAction(TripListAction.OnCreateNewTripClick)
        viewModel.onAction(TripListAction.OnNewTripNameChange("  제주  "))
        viewModel.onAction(TripListAction.OnConfirmCreateTrip)

        coVerify(exactly = 1) { tripRepository.createTrip("제주", Visibility.PRIVATE) }
        assertFalse(viewModel.uiState.value.isCreateTripDialogVisible)
        assertEquals("", viewModel.uiState.value.newTripName)
        coVerify(exactly = 2) { tripRepository.fetchMyTrips(any(), any(), any()) }
    }

    @Test
    fun `여행 생성에 실패하면 다이얼로그가 닫히고 저장 실패 스낵바를 보여준다`() = runTest {
        coEvery { tripRepository.createTrip(any(), any()) } returns Result.failure(RuntimeException())
        collectState()
        viewModel.onAction(TripListAction.OnCreateNewTripClick)
        viewModel.onAction(TripListAction.OnNewTripNameChange("제주"))
        viewModel.onAction(TripListAction.OnConfirmCreateTrip)

        assertFalse(viewModel.uiState.value.isCreateTripDialogVisible)
        verify { snackBarManager.show(SnackBarEvent.DATA_SAVE_FAILED) }
    }
    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher((testScheduler))) {
            viewModel.uiState.collect{  }
        }
    }
}
