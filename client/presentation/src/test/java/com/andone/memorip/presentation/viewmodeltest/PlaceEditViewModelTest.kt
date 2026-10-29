package com.andone.memorip.presentation.viewmodeltest

import android.content.Context
import android.net.Uri
import app.cash.turbine.test
import com.andone.memorip.domain.ai.ToxicityAnalyzer
import com.andone.memorip.domain.model.request.Address
import com.andone.memorip.domain.model.request.PlaceCreateUpdate
import com.andone.memorip.domain.repository.PlaceRepository
import com.andone.memorip.presentation.MainDispatcherRule
import com.andone.memorip.presentation.model.TripUiModel
import com.andone.memorip.presentation.screen.placedetail.model.PlaceUiModel
import com.andone.memorip.presentation.screen.placedetail.model.TripCompactUiModel
import com.andone.memorip.presentation.screen.placeedit.PlaceEditViewModel
import com.andone.memorip.presentation.screen.placeedit.model.PlaceEditAction
import com.andone.memorip.presentation.screen.placeedit.model.PlaceEditEvent
import com.andone.memorip.presentation.util.BitmapCropUtil
import com.andone.memorip.presentation.util.snackbar.SnackBarEvent
import com.andone.memorip.presentation.util.snackbar.SnackBarManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val placeRepository: PlaceRepository = mockk()
    private val snackBarManager: SnackBarManager = mockk(relaxed = true)
    private val toxicityAnalyzer: ToxicityAnalyzer = mockk()
    private val context: Context = mockk(relaxed = true)
    private lateinit var viewModel: PlaceEditViewModel

    private val place = PlaceUiModel(
        id = "p1",
        title = "제목",
        content = "내용",
        latitude = 37.5,
        longitude = 127.0,
        imageUrls = persistentListOf("a", "b"),
        trips = persistentListOf(TripCompactUiModel("t1", "제주")),
        locationName = "서울 강남구",
        isPublic = true
    )

    private val uris = mutableMapOf<String, Uri>()
    private fun fakeUri(value: String): Uri = uris.getOrPut(value) {
        mockk<Uri>().also { every { it.toString() } returns value }
    }

    private fun TestScope.collectUpdateEnabled() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.isUpdateEnabled.collect {  }
        }
    }

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers { fakeUri(firstArg()) }
        mockkObject(BitmapCropUtil)
        coEvery { BitmapCropUtil.getAspectRatioFromUrl(any(), any()) } returns 1.5f
        every { toxicityAnalyzer.predict(any()) } returns emptyList()

        viewModel = PlaceEditViewModel(place, placeRepository, snackBarManager, toxicityAnalyzer)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        unmockkObject(BitmapCropUtil)
    }

    @Test
    fun `장소 정보로 초기 상태가 만들어진다`() {
        val state = viewModel.uiState.value

        assertEquals("p1", state.id)
        assertEquals("제목", state.title)
        assertEquals("내용", state.content)
        assertEquals(listOf(fakeUri("a"), fakeUri("b")), state.images)
        assertEquals(37.5, state.location!!.latitude, 0.0)
        assertEquals(listOf(TripUiModel("t1", "제주", emptyList())), state.trips)
        assertTrue(state.isPublic)
        assertFalse(state.isLoading)
    }

    @Test
    fun `제목 내용 공개 여부 스크롤 위치 변경이 반영된다`() {
        viewModel.onAction(PlaceEditAction.OnTitleChange("새 제목"))
        viewModel.onAction(PlaceEditAction.OnContentChange("새 내용"))
        viewModel.onAction(PlaceEditAction.OnPublicChange)
        viewModel.onAction(PlaceEditAction.OnScrollPositionChange(120))

        val state = viewModel.uiState.value
        assertEquals("새 제목", state.title)
        assertEquals("새 내용", state.content)
        assertFalse(state.isPublic)
        assertEquals(120, state.scrollPosition)
    }

    @Test
    fun `이미지를 선택하면 selectedImage가 갱신된다`() {
        viewModel.onAction(PlaceEditAction.OnImageSelect(fakeUri("a")))

        assertEquals(fakeUri("a"), viewModel.uiState.value.selectedImage)
    }

    @Test
    fun `여행과 태그를 갱신하면 상태에 반영된다`() {
        val trips = listOf(TripUiModel("t2", "부산", emptyList()))

        viewModel.updateTrip(trips)
        viewModel.updateTag(emptyList())

        assertEquals(trips, viewModel.uiState.value.trips)
        assertTrue(viewModel.uiState.value.tags.isEmpty())
    }

    @Test
    fun `이미지를 삭제하면 목록에서 제거된다`() {
        viewModel.onAction(PlaceEditAction.OnImagesRemove(fakeUri("a")))

        assertEquals(listOf(fakeUri("b")), viewModel.uiState.value.images)
    }

    @Test
    fun `선택된 이미지를 삭제하면 남은 첫 이미지가 선택된다`() {
        viewModel.onAction(PlaceEditAction.OnImageSelect(fakeUri("a")))

        viewModel.onAction(PlaceEditAction.OnImagesRemove(fakeUri("a")))

        assertEquals(fakeUri("b"), viewModel.uiState.value.selectedImage)
    }

    @Test
    fun `선택되지 않은 이미지를 삭제하면 선택은 유지된다`() {
        viewModel.onAction(PlaceEditAction.OnImageSelect(fakeUri("b")))

        viewModel.onAction(PlaceEditAction.OnImagesRemove(fakeUri("a")))

        assertEquals(fakeUri("b"), viewModel.uiState.value.selectedImage)
    }

    @Test
    fun `마지막 한 장은 삭제할 수 없고 에러 스낵바를 보여준다`() {
        viewModel.onAction(PlaceEditAction.OnImagesRemove(fakeUri("a")))

        viewModel.onAction(PlaceEditAction.OnImagesRemove(fakeUri("b")))

        assertEquals(listOf(fakeUri("b")), viewModel.uiState.value.images)
        verify { snackBarManager.show(SnackBarEvent.IMAGE_COUNT_ERROR) }
    }

    @Test
    fun `클릭 액션들은 대응되는 이벤트를 발생시킨다`() = runTest {
        viewModel.event.test {
            viewModel.onAction(PlaceEditAction.OnCategoryClick)
            assertEquals(PlaceEditEvent.NavigateToCategory, awaitItem())

            viewModel.onAction(PlaceEditAction.OnLocationClick)
            assertEquals(PlaceEditEvent.NavigateToLocation, awaitItem())

            viewModel.onAction(PlaceEditAction.OnTripClick)
            assertEquals(PlaceEditEvent.NavigateToTrip, awaitItem())

            viewModel.onAction(PlaceEditAction.OnBackClick)
            assertEquals(PlaceEditEvent.NavigateBack, awaitItem())
        }
    }

    @Test
    fun `변경이 없으면 수정 버튼이 비활성화된다`() = runTest {
        collectUpdateEnabled()

        assertFalse(viewModel.isUpdateEnabled.value)
    }

    @Test
    fun `제목을 바꾸면 활성화되고 원래대로 되돌리면 다시 비활성화된다`() = runTest {
        collectUpdateEnabled()

        viewModel.onAction(PlaceEditAction.OnTitleChange("새 제목"))
        assertTrue(viewModel.isUpdateEnabled.value)

        viewModel.onAction(PlaceEditAction.OnTitleChange("제목"))
        assertFalse(viewModel.isUpdateEnabled.value)
    }

    @Test
    fun `제목이 비어 있으면 변경이 있어도 비활성화된다`() = runTest {
        collectUpdateEnabled()

        viewModel.onAction(PlaceEditAction.OnTitleChange(" "))

        assertFalse(viewModel.isUpdateEnabled.value)
    }

    @Test
    fun `여행이 비어 있으면 비활성화된다`() = runTest {
        collectUpdateEnabled()

        viewModel.updateTrip(emptyList())

        assertFalse(viewModel.isUpdateEnabled.value)
    }

    @Test
    fun `필수 값이 없으면 수정 요청을 보내지 않는다`() {
        viewModel.onAction(PlaceEditAction.OnTitleChange(" "))

        viewModel.onAction(PlaceEditAction.OnPlaceUpdate(context))

        coVerify(exactly = 0) { placeRepository.updatePlace(any(), any()) }
        verify(exactly = 0) { toxicityAnalyzer.predict(any()) }
    }

    @Test
    fun `유해 문장이 감지되면 에러 라벨을 저장하고 수정 요청을 보내지 않는다`() {
        every { toxicityAnalyzer.predict("내용") } returns listOf("toxic" to 0.9f)

        viewModel.onAction(PlaceEditAction.OnPlaceUpdate(context))

        assertEquals("toxic", viewModel.uiState.value.contentErrorLabel)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 0) { placeRepository.updatePlace(any(), any()) }
    }

    @Test
    fun `수정에 성공하면 요청 내용을 올바르게 만들어 보내고 완료 이벤트를 발생시킨다`() = runTest {
        val request = slot<PlaceCreateUpdate>()
        coEvery { placeRepository.updatePlace("p1", capture(request)) } returns Result.success(mockk(relaxed = true))

        viewModel.event.test {
            viewModel.onAction(PlaceEditAction.OnPlaceUpdate(context))

            assertEquals(PlaceEditEvent.NavigateBackAfterUpdate, awaitItem())
        }
        with(request.captured) {
            assertEquals(listOf("t1"), tripIds)
            assertEquals("제목", title)
            assertEquals("내용", content)
            assertEquals(37.5, latitude, 0.0)
            assertEquals(127.0, longitude, 0.0)
            assertEquals(Address.from("서울 강남구"), address)
            assertEquals(listOf("a", "b"), imageUrls)
            assertEquals(1.5f, thumbnailImageRatio, 0f)
            assertTrue(isPublic)
        }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `수정에 실패하면 네트워크 에러 스낵바를 보여주고 이동하지 않는다`() = runTest {
        coEvery { placeRepository.updatePlace(any(), any()) } returns Result.failure(RuntimeException())

        viewModel.event.test {
            viewModel.onAction(PlaceEditAction.OnPlaceUpdate(context))

            expectNoEvents()
        }
        verify { snackBarManager.show(SnackBarEvent.NETWORK_ERROR) }
        assertFalse(viewModel.uiState.value.isLoading)
    }
}