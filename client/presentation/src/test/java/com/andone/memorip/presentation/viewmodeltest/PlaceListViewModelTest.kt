package com.andone.memorip.presentation.viewmodeltest

import androidx.compose.ui.graphics.Color
import androidx.paging.PagingData
import app.cash.turbine.test
import com.andone.memorip.domain.auth.TokenRefresher
import com.andone.memorip.domain.model.Region
import com.andone.memorip.domain.repository.AuthRepository
import com.andone.memorip.domain.repository.PlaceRepository
import com.andone.memorip.domain.repository.TagRepository
import com.andone.memorip.presentation.MainDispatcherRule
import com.andone.memorip.presentation.model.TagUiModel
import com.andone.memorip.presentation.screen.placelist.PlaceListViewModel
import com.andone.memorip.presentation.screen.placelist.model.PlaceListAction
import com.andone.memorip.presentation.screen.placelist.model.PlaceListEvent
import com.andone.memorip.presentation.screen.placelist.model.RegionUiModel
import com.andone.memorip.presentation.screen.placelist.model.SelectedRegionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val placeRepository: PlaceRepository = mockk()
    private val tagRepository: TagRepository = mockk()
    private val authRepository: AuthRepository = mockk()
    private val tokenRefresher: TokenRefresher = mockk()
    private lateinit var viewModel: PlaceListViewModel

    private val regions = listOf(
        Region(
            name = "서울",
            parent = null,
            level = 1,
            subRegions = listOf(
                Region("강남구", null, 2),
                Region("마포구", null, 2)
            )
        ),
        Region(
            name = "부산",
            parent = null,
            level = 1,
            subRegions = listOf(
                Region("해운대구", null, 2)
            )
        )
    )

    private val tag = TagUiModel(id = "t1", name = "맛집", color = Color.Red)

    private fun createViewModel() =
        PlaceListViewModel(placeRepository, tagRepository, authRepository, tokenRefresher)

    private fun root(name: String): RegionUiModel =
        viewModel.uiState.value.rootRegions.first { it.name == name }

    private fun sub(parent: String, name: String): RegionUiModel =
        root(parent).child.first { it.name == name }

    private fun TestScope.collectPlaces() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.placesPagingFlow.collect { }
        }
    }

    @Before
    fun setUp() {
        every { placeRepository.loadRegions() } returns regions
        every { placeRepository.getPlaceList(any(), any(), any(), any(), any()) } returns
                flowOf(PagingData.empty())
        every { tagRepository.loadTags() } returns flowOf(PagingData.empty())
        coEvery { tokenRefresher.refreshToken(any()) } returns null
        coEvery { authRepository.signOut() } just runs
        viewModel = createViewModel()
    }

    @Test
    fun `초기화 시 지역 목록이 UI 모델로 변환되어 상태에 담긴다`() {
        val roots = viewModel.uiState.value.rootRegions

        assertEquals(listOf("서울", "부산"), roots.map { it.name })
        assertEquals(listOf("강남구", "마포구"), roots[0].child.map { it.name })
        assertEquals(listOf("해운대구"), roots[1].child.map { it.name })
    }

    @Test
    fun `초기화 시 토큰을 강제로 갱신한다`() {
        coVerify(exactly = 1) { tokenRefresher.refreshToken(true) }
    }

    @Test
    fun `토큰 갱신에 실패하면 로그아웃 처리한다`() {
        coEvery { tokenRefresher.refreshToken(any()) } throws RuntimeException()

        createViewModel()

        coVerify(exactly = 1) { authRepository.signOut() }
    }

    @Test
    fun `검색어 변경이 상태에 반영된다`() {
        viewModel.onAction(PlaceListAction.OnQueryChange("카페"))

        assertEquals("카페", viewModel.uiState.value.query)
    }

    @Test
    fun `클릭 액션들은 대응되는 이벤트를 발생시킨다`() = runTest {
        viewModel.event.test {
            viewModel.onAction(PlaceListAction.OnFABClick)
            assertEquals(PlaceListEvent.NavigateToPlaceCreate, awaitItem())

            viewModel.onAction(PlaceListAction.OnPlaceClick("p1"))
            assertEquals(PlaceListEvent.NavigateToPlaceDetail("p1"), awaitItem())

            viewModel.onAction(PlaceListAction.OnRefreshPull)
            assertEquals(PlaceListEvent.RefreshPagingData, awaitItem())
        }
    }

    @Test
    fun `태그 칩을 누르면 추가되고 삭제하면 제거된다`() {
        viewModel.onAction(PlaceListAction.OnTagChipClick(tag))
        assertEquals(setOf(tag), viewModel.uiState.value.selectedTags)

        viewModel.onAction(PlaceListAction.OnDeleteTagClick(tag))
        assertTrue(viewModel.uiState.value.selectedTags.isEmpty())
    }

    @Test
    fun `시도를 선택하면 하위 지역 목록이 보인다`() {
        viewModel.onAction(PlaceListAction.OnRegionChipClick(root("서울")))

        val state = viewModel.uiState.value
        assertEquals(listOf("서울"), state.selectedRegionState.parents.map { it.name })
        assertEquals(1, state.currentLevel)
        assertEquals(listOf("강남구", "마포구"), state.currentRegionList.map { it.name })
    }

    @Test
    fun `시군구를 선택하면 상위와 함께 parents에 쌓인다`() {
        viewModel.onAction(PlaceListAction.OnRegionChipClick(root("서울")))

        viewModel.onAction(PlaceListAction.OnRegionChipClick(sub("서울", "강남구")))

        val state = viewModel.uiState.value
        assertEquals(listOf("서울", "강남구"), state.selectedRegionState.parents.map { it.name })
        assertTrue(state.selectedRegionState.child.isEmpty())
        assertEquals(2, state.currentLevel)
        assertTrue(state.currentRegionList.first { it.name == "강남구" }.isSelected)
    }

    @Test
    fun `같은 레벨의 다른 지역을 추가로 선택하면 child로 다중 선택된다`() {
        viewModel.onAction(PlaceListAction.OnRegionChipClick(root("서울")))
        viewModel.onAction(PlaceListAction.OnRegionChipClick(sub("서울", "강남구")))

        viewModel.onAction(PlaceListAction.OnRegionChipClick(sub("서울", "마포구")))

        val selected = viewModel.uiState.value.selectedRegionState
        assertEquals(listOf("서울"), selected.parents.map { it.name })
        assertEquals(setOf("강남구", "마포구"), selected.child.map { it.name }.toSet())
    }

    @Test
    fun `다중 선택된 지역을 다시 누르면 해당 지역만 해제된다`() {
        viewModel.onAction(PlaceListAction.OnRegionChipClick(root("서울")))
        viewModel.onAction(PlaceListAction.OnRegionChipClick(sub("서울", "강남구")))
        viewModel.onAction(PlaceListAction.OnRegionChipClick(sub("서울", "마포구")))

        viewModel.onAction(PlaceListAction.OnRegionChipClick(sub("서울", "강남구")))

        val selected = viewModel.uiState.value.selectedRegionState
        assertEquals(setOf("마포구"), selected.child.map { it.name }.toSet())
    }

    @Test
    fun `지역 필터를 초기화하면 선택 상태가 비워진다`() {
        viewModel.onAction(PlaceListAction.OnRegionChipClick(root("서울")))

        viewModel.onAction(PlaceListAction.ClearRegionFilter)

        assertEquals(SelectedRegionState(), viewModel.uiState.value.selectedRegionState)
    }

    @Test
    fun `검색어를 입력하고 300ms가 지나면 해당 검색어로 목록을 조회한다`() = runTest {
        collectPlaces()

        viewModel.onAction(PlaceListAction.OnQueryChange("카페"))
        advanceTimeBy(300.milliseconds)
        runCurrent()

        verify(exactly = 1) {
            placeRepository.getPlaceList("카페", emptyList(), null, emptyList(), any())
        }
    }

    @Test
    fun `디바운스 시간 안에는 조회되지 않는다`() = runTest {
        collectPlaces()

        viewModel.onAction(PlaceListAction.OnQueryChange("카페"))
        advanceTimeBy(299.milliseconds)
        runCurrent()

        verify(exactly = 0) { placeRepository.getPlaceList(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `시도를 선택하면 region1Depth로 조회한다`() = runTest {
        collectPlaces()

        viewModel.onAction(PlaceListAction.OnRegionChipClick(root("서울")))
        advanceTimeBy(300.milliseconds)
        runCurrent()

        verify { placeRepository.getPlaceList("", emptyList(), "서울", emptyList(), any()) }
    }

    @Test
    fun `여러 시군구를 선택하면 region2Depth에 모두 담아 조회한다`() = runTest {
        collectPlaces()

        viewModel.onAction(PlaceListAction.OnRegionChipClick(root("서울")))
        viewModel.onAction(PlaceListAction.OnRegionChipClick(sub("서울", "강남구")))
        viewModel.onAction(PlaceListAction.OnRegionChipClick(sub("서울", "마포구")))
        advanceTimeBy(300.milliseconds)
        runCurrent()

        verify {
            placeRepository.getPlaceList("", emptyList(), "서울", listOf("강남구", "마포구"), any())
        }
    }

    @Test
    fun `태그를 선택하면 tagIds로 조회한다`() = runTest {
        collectPlaces()

        viewModel.onAction(PlaceListAction.OnTagChipClick(tag))
        advanceTimeBy(300.milliseconds)
        runCurrent()

        verify { placeRepository.getPlaceList("", listOf("t1"), null, emptyList(), any()) }
    }
}