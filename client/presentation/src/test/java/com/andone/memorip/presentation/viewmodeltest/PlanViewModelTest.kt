package com.andone.memorip.presentation.viewmodeltest

import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import app.cash.turbine.test
import com.andone.memorip.domain.model.TripListItem
import com.andone.memorip.domain.model.TripPlace
import com.andone.memorip.domain.model.Visibility
import com.andone.memorip.domain.repository.TripRepository
import com.andone.memorip.presentation.MainDispatcherRule
import com.andone.memorip.presentation.model.Place
import com.andone.memorip.presentation.screen.plan.PlanViewModel
import com.andone.memorip.presentation.screen.plan.model.PlanAction
import com.andone.memorip.presentation.screen.plan.model.PlanEvent
import com.andone.memorip.presentation.screen.plan.model.TripListUiModel
import com.andone.memorip.presentation.util.snackbar.SnackBarEvent
import com.andone.memorip.presentation.util.snackbar.SnackBarManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tripRepository: TripRepository = mockk()
    private val snackBarManager: SnackBarManager = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private lateinit var viewModel: PlanViewModel

    private val simpleTrips = listOf(
        TripListItem("t1", "제주", "2026-01-01", "2026-01-03"),
        TripListItem("t2", "부산", "2026-02-01", "2026-02-02")
    )
    private val tripT2 =
        TripListUiModel("t2", "부산", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2))

    private fun tripPlace(id: String, start: String?, end: String?) = TripPlace(
        tripPlaceId = id,
        placeId = "place-$id",
        title = "장소$id",
        thumbnail = null,
        address = "주소",
        latitude = 37.5,
        longitude = 127.0,
        startAt = start,
        endAt = end
    )

    private fun createViewModel(handle: SavedStateHandle = SavedStateHandle()) =
        PlanViewModel(tripRepository, snackBarManager, workManager, handle)

    private fun TestScope.start() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: PlanAction) {
        viewModel.onAction(action)
        advanceUntilIdle()
    }

    private val state get() = viewModel.uiState.value
    private fun place(id: String) = state.places.first { it.id == id } as Place
    private fun date(m: Int, d: Int) = LocalDate.of(2026, m, d)
    private fun time(m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(2026, m, d, h, min)

    @Before
    fun setUp() {
        coEvery { tripRepository.getSimpleTrips() } returns Result.success(simpleTrips)
        coEvery { tripRepository.getPlaceByTripId(any()) } returns Result.success(emptyList())
        coEvery { tripRepository.getPlaceByTripId("t1") } returns Result.success(
            listOf(
                tripPlace("A", "2026-01-01T09:00", "2026-01-01T10:00"),
                tripPlace("B", null, null)
            )
        )
        coEvery { tripRepository.updatePlaceTime(any(), any(), any()) } returns Result.success(Unit)
        coEvery { tripRepository.clearPlaceTime(any()) } returns Result.success(Unit)
        coEvery { tripRepository.updateTrip(any(), any(), any(), any(), any()) } returns Result.success(
            Unit
        )
        viewModel = createViewModel()
    }

    @Test
    fun `구독하면 첫 여행을 선택하고 날짜와 장소를 불러온다`() = runTest {
        start()

        assertEquals(listOf("t1", "t2"), state.trips.map { it.id })
        assertEquals("t1", state.selectedTrip?.id)
        assertEquals(date(1, 1), state.date.startDay)
        assertEquals(date(1, 3), state.date.endDay)
        assertEquals(date(1, 1), state.date.currentDay)
        assertEquals(listOf("A", "B"), state.places.map { it.id })
        assertEquals(listOf("A"), state.blockItems.map { it.id })
        assertEquals(listOf("B"), state.bottomItems.map { it.id })
    }

    @Test
    fun `저장된 선택 여행 id가 있으면 해당 여행을 선택한다`() = runTest {
        viewModel = createViewModel(SavedStateHandle(mapOf(PlanViewModel.SELECTED_TRIP_ID to "t2")))

        start()

        assertEquals("t2", state.selectedTrip?.id)
        coVerify { tripRepository.getPlaceByTripId("t2") }
    }

    @Test
    fun `여행 목록 조회에 실패하면 네트워크 에러 스낵바를 보여준다`() = runTest {
        coEvery { tripRepository.getSimpleTrips() } returns Result.failure(RuntimeException())

        start()

        verify { snackBarManager.show((SnackBarEvent.NETWORK_ERROR)) }
        assertTrue(state.trips.isEmpty())
    }

    @Test
    fun `여행이 하나도 없으면 선택된 여행이 없다`() = runTest {
        coEvery { tripRepository.getSimpleTrips() } returns Result.success(emptyList())

        start()

        assertNull(state.selectedTrip)
        assertTrue(state.trips.isEmpty())
    }

    @Test
    fun `장소 조회에 실패하면 네트워크 에러 스낵바를 보여준다`() = runTest {
        coEvery { tripRepository.getPlaceByTripId("t1") } returns Result.failure(RuntimeException())

        start()

        verify { snackBarManager.show(SnackBarEvent.NETWORK_ERROR) }
        assertTrue(state.places.isEmpty())
    }

    @Test
    fun `일차 추가를 누르면 종료일이 하루 늘어난다`() = runTest {
        start()

        act(PlanAction.AddDayClick)

        assertEquals(date(1, 4), state.date.endDay)
        assertEquals(4, state.date.totalDays)
    }

    @Test
    fun `일차 클릭과 스크롤은 현재 날짜를 바꾼다`() = runTest {
        start()

        act(PlanAction.DayClick(2))
        assertEquals(date(1, 2), state.date.currentDay)

        act(PlanAction.DayScrolled(3))
        assertEquals(date(1, 3), state.date.currentDay)
    }

    @Test
    fun `일차를 길게 누르면 기억하고 취소하면 지운다`() = runTest {
        start()

        act(PlanAction.DayLongClick(2))
        assertEquals(2, state.date.longClickedDay)

        act(PlanAction.RemoveDayCancelClick)
        assertNull(state.date.longClickedDay)
    }

    @Test
    fun `일차 삭제를 누르면 길게 누른 일차와 함께 삭제 다이얼로그 이벤트가 발생한다`() = runTest {
        start()
        act(PlanAction.DayLongClick(2))

        viewModel.event.test {
            viewModel.onAction(PlanAction.RemoveDayClick)

            assertEquals(PlanEvent.ShowDeleteDayDialog(day = 2), awaitItem())
        }
    }

    @Test
    fun `첫날을 삭제하면 시작일이 하루 뒤로 밀리고 현재 날짜가 보정된다`() = runTest {
        start()

        act(PlanAction.RemoveDayDialogConfirmClick(1))

        assertEquals(date(1, 2), state.date.startDay)
        assertEquals(date(1, 3), state.date.endDay)
        assertEquals(date(1, 2), state.date.currentDay)
        assertNull(state.date.longClickedDay)
    }

    @Test
    fun `마지막 날을 삭제하면 종료일이 하루 앞당겨진다`() = runTest {
        start()

        act(PlanAction.RemoveDayDialogConfirmClick(3))

        assertEquals(date(1, 1), state.date.startDay)
        assertEquals(date(1, 2), state.date.endDay)
    }

    @Test
    fun `날짜 범위를 30일 넘게 선택하면 에러 스낵바를 보여주고 바꾸지 않는다`() = runTest {
        start()

        act(PlanAction.SelectDateDialogConfirmClick(date(3, 1), date(4, 15)))

        verify { snackBarManager.show(SnackBarEvent.PLAN_DAYS_VALIDATION_ERROR) }
        assertEquals(date(1, 1), state.date.startDay)
    }

    @Test
    fun `유효한 날짜 범위를 선택하면 날짜와 여행 정보가 갱신되고 장소를 다시 불러온다`() = runTest {
        start()

        act(PlanAction.SelectDateDialogConfirmClick(date(3, 1), date(3, 5)))

        assertEquals(date(3, 1), state.date.startDay)
        assertEquals(date(3, 5), state.date.endDay)
        assertEquals(date(3, 1), state.date.currentDay)
        assertEquals(date(3, 1), state.selectedTrip?.startDate)
        assertEquals(date(3, 5), state.trips.first { it.id == "t1" }.endDate)
        coVerify(exactly = 2) { tripRepository.getPlaceByTripId("t1") }
    }

    @Test
    fun `여행 선택과 달력 클릭은 다이얼로그 이벤트를 발생시킨다`() = runTest {
        start()

        viewModel.event.test {
            viewModel.onAction(PlanAction.TripChoiceClick)
            assertEquals(PlanEvent.ShowTripChoiceDialog, awaitItem())

            viewModel.onAction(PlanAction.ShowCalendarClick)
            assertEquals(PlanEvent.ShowCalendarDialog, awaitItem())
        }
    }

    @Test
    fun `블록을 클릭하면 수정 대상이 되고 취소하면 해제된다`() = runTest {
        start()

        act(PlanAction.BlockClick("A"))
        assertEquals("A", state.updatedBlock?.id)

        act(PlanAction.PlanEditCancelClick)
        assertNull(state.updatedBlock)
    }

    @Test
    fun `존재하지 않는 블록을 클릭하면 아무 일도 없다`() = runTest {
        start()

        act(PlanAction.BlockClick("없는 id"))

        assertNull(state.updatedBlock)
    }

    @Test
    fun `블록을 슬라이드하면 시간이 지워져 미배치 목록으로 돌아간다`() = runTest {
        start()

        act(PlanAction.BlockSlide("A"))

        assertNull(place("A").startDateTime)
        assertNull(place("A").endDateTime)
        assertTrue(state.blockItems.isEmpty())
        assertEquals(setOf("A", "B"), state.bottomItems.map { it.id }.toSet())
    }

    @Test
    fun `블록을 이동하면 시작 분 기준으로 시작 종료 시간이 바뀐다`() = runTest {
        start()

        act(PlanAction.BlockMoved(id = "A", newStartMinute = 120))

        assertEquals(time(1, 1, 2), place("A").startDateTime)
        assertEquals(time(1, 1, 3), place("A").endDateTime)
    }

    @Test
    fun `미배치 장소를 빈 시간에 놓으면 배치된다`() = runTest {
        start()

        act(PlanAction.BottomBlockDragEnd(item = place("B"), startMinute = 9 * 60))

        verify { snackBarManager.show(SnackBarEvent.PLAN_INVALID_ERROR) }
        assertNull(place("B").startDateTime)
    }

    @Test
    fun `시작과 종료 시간이 같으면 에러 스낵바를 보여주고 수정하지 않는다`() = runTest {
        start()

        act(PlanAction.PlanEditDialogConfirmClick("A", time(1, 1, 13), time(1, 1, 13)))

        verify { snackBarManager.show(SnackBarEvent.PLAN_DATE_INVALID_ERROR) }
        assertEquals(time(1, 1, 9), place("A").startDateTime)
    }

    @Test
    fun `유효한 시간으로 수정하면 블록 시간이 바뀌고 수정 대상이 해제된다`() = runTest {
        start()
        act(PlanAction.BlockClick("A"))

        act(PlanAction.PlanEditDialogConfirmClick("A", time(1, 1, 13), time(1, 1, 14)))

        assertEquals(time(1, 1, 13), place("A").startDateTime)
        assertEquals(time(1, 1, 14), place("A").endDateTime)
        assertNull(state.updatedBlock)
    }

    @Test
    fun `여행을 전환하면 이전 여행을 저장하고 새 여행의 장소를 불러온다`() = runTest {
        start()

        act(PlanAction.TripChoiceDialogConfirmClick(tripT2))

        coVerify {
            tripRepository.updateTrip(
                tripId = "t1",
                title = "제주",
                visibility = Visibility.PRIVATE,
                startDate = "2026-01-01",
                endDate = "2026-01-03"
            )
        }
        assertEquals("t2", state.selectedTrip?.id)
        assertEquals(date(2, 1), state.date.startDay)
        coVerify { tripRepository.getPlaceByTripId("t2") }
        coVerify(exactly = 0) { tripRepository.updatePlaceTime(any(), any(), any()) }
    }

    @Test
    fun `이동한 블록이 있으면 여행 전환 시 변경된 시간을 서버에 저장한다`() = runTest {
        start()
        act(PlanAction.BlockMoved(id = "A", newStartMinute = 120))

        act(PlanAction.TripChoiceDialogConfirmClick(tripT2))

        coVerify {
            tripRepository.updatePlaceTime(
                tripPlaceId = "A",
                startAt = "2026-01-01T02:00",
                endAt = "2026-01-01T03:00"
            )
        }
    }
}