package com.andone.memorip.presentation.viewmodeltest

import app.cash.turbine.test
import com.andone.memorip.domain.model.Trip
import com.andone.memorip.domain.model.TripWithPlaceAdded
import com.andone.memorip.domain.model.Visibility
import com.andone.memorip.domain.repository.PlaceRepository
import com.andone.memorip.domain.repository.TripRepository
import com.andone.memorip.presentation.MainDispatcherRule
import com.andone.memorip.presentation.screen.selecttrip.SelectTripViewModel
import com.andone.memorip.presentation.screen.selecttrip.model.SelectTripAction
import com.andone.memorip.presentation.screen.selecttrip.model.SelectTripEvent
import com.andone.memorip.presentation.screen.selecttrip.model.SelectTripUiModel
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class SelectTripViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tripRepository: TripRepository = mockk()
    private val placeRepository: PlaceRepository = mockk()
    private val snackBarManager: SnackBarManager = mockk(relaxed = true)
    private lateinit var viewModel: SelectTripViewModel

    private fun trip(id: String) = Trip(
        id = id,
        owner = mockk(relaxed = true),
        title = "여행$id",
        visibility = Visibility.PRIVATE,
        createdAt = "",
        updatedAt = ""
    )

    private fun withPlace(id: String, added: Boolean) = TripWithPlaceAdded(trip(id), added)
    private fun uiModel(id: String) =
        SelectTripUiModel(id = id, name = "여행$id", images = emptyList())

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }

    private fun TestScope.initialize(
        placeId: String?,
        isPlaceMine: Boolean = true,
        initialSelectedTripIds: List<String>? = null
    ) {
        collectState()
        viewModel.onAction(
            SelectTripAction.OnInitialize(
                placeId,
                isPlaceMine,
                initialSelectedTripIds
            )
        )
        advanceUntilIdle()
    }

    private fun stubPlaceTrips(vararg trips: TripWithPlaceAdded) {
        coEvery {
            tripRepository.fetchMyTripsWithPlaceStatus(
                any(),
                any(),
                any()
            )
        } returns Result.success(trips.toList())
    }

    @Before
    fun setUp() {
        coEvery { tripRepository.fetchMyTrips(any(), any(), any()) } coAnswers {
            delay(10.milliseconds)
            Result.success(Unit)
        }
        coEvery { tripRepository.myTrips } returns flowOf(emptyList())
        coEvery { tripRepository.fetchMyTripsWithPlaceStatus(any(), any(), any()) } coAnswers {
            delay(10.milliseconds)
            Result.success(emptyList())
        }
        viewModel = SelectTripViewModel(tripRepository, placeRepository, snackBarManager)
    }

    @Test
    fun `placeId가 있으면 장소 포함 여부를 기준으로 선택 상태를 초기화한다`() = runTest {
        stubPlaceTrips(withPlace("A", true), withPlace("B", false))

        initialize(placeId = "p1")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("A", "B"), state.trips.map { it.id })
        assertEquals(setOf("A"), state.selectedTripIds)
        assertEquals(setOf("A"), state.initialSelectedTripIds)
    }

    @Test
    fun `placeId 조회에 실패하면 로딩만 끝난다`() = runTest {
        coEvery {
            tripRepository.fetchMyTripsWithPlaceStatus(
                any(),
                any(),
                any()
            )
        } returns Result.failure(RuntimeException())

        initialize(placeId = "p1")

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.trips.isEmpty())
    }

    @Test
    fun `placeId가 없으면 내 여행 목록과 전달받은 초기 선택 id를 반영한다`() = runTest {
        coEvery { tripRepository.myTrips } returns flowOf(listOf(trip("A"), trip("B")))

        initialize(placeId = null, initialSelectedTripIds = listOf("A"))

        val state = viewModel.uiState.value
        assertEquals(listOf("A", "B"), state.trips.map { it.id })
        assertEquals(setOf("A"), state.selectedTripIds)
    }

    @Test
    fun `선택되지 않은 여행을 클릭하면 선택에 추가된다`() = runTest {
        stubPlaceTrips(withPlace("A", true), withPlace("B", false))
        initialize(placeId = "p1")

        viewModel.onAction(SelectTripAction.OnTripClick(uiModel("B")))

        assertEquals(setOf("A", "B"), viewModel.uiState.value.selectedTripIds)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun `내 장소의 마지막 선택을 해제하려 하면 스낵바를 보여주고 유지한다`() = runTest {
        stubPlaceTrips(withPlace("A", true), withPlace("B", false))
        initialize(placeId = "p1", isPlaceMine = true)

        viewModel.onAction(SelectTripAction.OnTripClick(uiModel("A")))

        assertEquals(setOf("A"), viewModel.uiState.value.selectedTripIds)
        verify { snackBarManager.show(SnackBarEvent.PLACE_MUST_HAVE_ONE_TRIP) }
    }

    @Test
    fun `내 장소가 아니면 마지막 선택도 해제할 수 있다`() = runTest {
        stubPlaceTrips(withPlace("A", true), withPlace("B", false))
        initialize(placeId = "p1", isPlaceMine = false)

        viewModel.onAction(SelectTripAction.OnTripClick(uiModel("A")))

        assertTrue(viewModel.uiState.value.selectedTripIds.isEmpty())
    }

    @Test
    fun `placeId가 있을 때 확인하면 추가와 제거 목록을 계산해 저장하고 완료 이벤트를 보낸다`() = runTest {
        stubPlaceTrips(withPlace("A", true), withPlace("B", false))
        coEvery { placeRepository.updatePlaceTrips(any(), any(), any()) } returns Result.success(
            Unit
        )
        initialize(placeId = "p1")
        viewModel.onAction(SelectTripAction.OnTripClick(uiModel("B")))

        viewModel.event.test {
            viewModel.onAction(SelectTripAction.OnCheckClick)

            assertEquals(SelectTripEvent.PlaceTripsUpdated, awaitItem())
        }

        coVerify {
            placeRepository.updatePlaceTrips(
                placeId = "p1",
                addTripIds = listOf("B"),
                removeTripIds = emptyList()
            )
        }

        verify { snackBarManager.show(SnackBarEvent.SUCCESS) }
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(setOf("A", "B"), viewModel.uiState.value.initialSelectedTripIds)
    }

    @Test
    fun `placeId가 있을 떄 저장에 실패하면 네트워크 에러 스낵바를 보여주고 로딩을 끝낸다`() = runTest {
        stubPlaceTrips(withPlace("A", true), withPlace("B", false))
        coEvery { placeRepository.updatePlaceTrips(any(), any(), any()) } returns Result.failure(
            RuntimeException()
        )
        initialize(placeId = "p1")
        viewModel.onAction(SelectTripAction.OnTripClick(uiModel("B")))

        viewModel.onAction(SelectTripAction.OnCheckClick)

        verify { snackBarManager.show(SnackBarEvent.NETWORK_ERROR) }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `placeId가 없을 때 확인하면 선택한 여행들과 함께 SelectTrip 이벤트를 보낸다`() = runTest {
        coEvery { tripRepository.myTrips } returns flowOf(listOf(trip("A"), trip("B")))
        initialize(placeId = null, initialSelectedTripIds = listOf("A"))

        viewModel.event.test {
            viewModel.onAction(SelectTripAction.OnCheckClick)

            val event = awaitItem() as SelectTripEvent.SelectTrip
            assertEquals(listOf("A"), event.trips.map { it.id })
        }
    }

    @Test
    fun `placeId가 없고 선택이 없으면 확인해도 이벤트가 없다`() = runTest {
        coEvery { tripRepository.myTrips } returns flowOf(listOf(trip("A")))
        initialize(placeId = null, initialSelectedTripIds = null)

        viewModel.event.test {
            viewModel.onAction(SelectTripAction.OnCheckClick)

            expectNoEvents()
        }
    }

    @Test
    fun `단순 클릭 액션들은 대응되는 이벤트를 발생시킨다`() = runTest {
        collectState()

        viewModel.event.test {
            viewModel.onAction(SelectTripAction.OnFABClick)
            assertEquals(SelectTripEvent.ShowDialog, awaitItem())

            viewModel.onAction(SelectTripAction.OnAddTripClick)
            assertEquals(SelectTripEvent.ShowDialog, awaitItem())

            viewModel.onAction(SelectTripAction.OnBackClick)
            assertEquals(SelectTripEvent.NavigateBack, awaitItem())

            viewModel.onAction(SelectTripAction.OnDialogCancelClick)
            assertEquals(SelectTripEvent.DismissDialog, awaitItem())
        }
    }

    @Test
    fun `여행 추가에 성공하면 PUBLIC으로 생성하고 다이얼로그를 닫는다`() = runTest {
        coEvery { tripRepository.createTrip(any(), any()) } returns Result.success(trip("N"))
        collectState()

        viewModel.event.test {
            viewModel.onAction(SelectTripAction.OnDialogConfirmClick("제주"))

            assertEquals(SelectTripEvent.DismissDialog, awaitItem())
        }
        coVerify { tripRepository.createTrip("제주", Visibility.PUBLIC) }
    }

    @Test
    fun `여행 추가에 실패하면 스낵바 이벤트를 보내고 로딩을 끝낸다`() = runTest {
        coEvery { tripRepository.createTrip(any(), any()) } returns Result.failure(RuntimeException())
        collectState()

        viewModel.event.test {
            viewModel.onAction(SelectTripAction.OnDialogConfirmClick("제주"))

            assertEquals(SelectTripEvent.ShowSnackBar, awaitItem())
        }
        assertFalse(viewModel.uiState.value.isLoading)
    }
}