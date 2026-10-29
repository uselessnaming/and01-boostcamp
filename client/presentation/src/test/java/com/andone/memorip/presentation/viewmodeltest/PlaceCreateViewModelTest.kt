package com.andone.memorip.presentation.viewmodeltest

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import app.cash.turbine.test
import com.andone.memorip.domain.ai.ToxicityAnalyzer
import com.andone.memorip.domain.model.request.Address
import com.andone.memorip.domain.model.request.PlaceCreateUpdate
import com.andone.memorip.domain.model.response.PlaceImageUploadResponse
import com.andone.memorip.domain.repository.PlaceRepository
import com.andone.memorip.presentation.MainDispatcherRule
import com.andone.memorip.presentation.model.LocationUiModel
import com.andone.memorip.presentation.model.TripUiModel
import com.andone.memorip.presentation.screen.placecreate.PlaceCreateViewModel
import com.andone.memorip.presentation.screen.placecreate.model.PlaceCreateAction
import com.andone.memorip.presentation.screen.placecreate.model.PlaceCreateEvent
import com.andone.memorip.presentation.util.snackbar.SnackBarEvent
import com.andone.memorip.presentation.util.snackbar.SnackBarManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class PlaceCreateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val placeRepository: PlaceRepository = mockk()
    private val snackBarManager: SnackBarManager = mockk(relaxed = true)
    private val toxicityAnalyzer: ToxicityAnalyzer = mockk()
    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()
    private lateinit var viewModel: PlaceCreateViewModel

    private val uri1: Uri = mockk()
    private val uri2: Uri = mockk()
    private val location = LocationUiModel(
        address = "서울 강남구",
        latitude = 37.5,
        longitude = 127.0
    )
    private val trip = TripUiModel(id = "t1", name = "제주", images = emptyList())

    private fun prepareValidState(vm: PlaceCreateViewModel = viewModel) {
        vm.updateImages(listOf(uri1, uri2), thumbnailImageRatio = 1.5f)
        vm.updateLocation(location)
        vm.updateTrip(listOf(trip))
        vm.onAction(PlaceCreateAction.OnTitleChange("제목"))
        vm.onAction(PlaceCreateAction.OnContentChange("내용"))
        vm.onAction(PlaceCreateAction.OnPublicChange)
    }

    @Before
    fun setUp() {
        every { context.contentResolver } returns contentResolver
        every { context.cacheDir }  returns tempFolder.root
        every { contentResolver.openInputStream(any()) } answers {
            ByteArrayInputStream(byteArrayOf(1, 2, 3))
        }
        every { toxicityAnalyzer.predict(any()) } returns emptyList()
        coEvery { placeRepository.uploadImage(any()) } returnsMany listOf(
            Result.success(PlaceImageUploadResponse("url-1")),
            Result.success(PlaceImageUploadResponse("url-2"))
        )
        coEvery { placeRepository.createPlace(any()) } returns Result.success(mockk(relaxed = true))

        viewModel = PlaceCreateViewModel(placeRepository, snackBarManager, toxicityAnalyzer)
    }

    @Test
    fun `제목 내용 공개 여부 스크롤 위치 변경이 반영된다`() {
        viewModel.onAction(PlaceCreateAction.OnTitleChange("제목"))
        viewModel.onAction(PlaceCreateAction.OnContentChange("내용"))
        viewModel.onAction(PlaceCreateAction.OnPublicChange)
        viewModel.onAction(PlaceCreateAction.OnScrollPositionChange(80))

        val state = viewModel.uiState.value
        assertEquals("제목", state.title)
        assertEquals("내용", state.content)
        assertTrue(state.isPublic)
        assertEquals(80, state.scrollPosition)
    }

    @Test
    fun `이미지 위치 여행 갱신이 상태에 반영된다`() {
        viewModel.updateImages(listOf(uri1), thumbnailImageRatio = 2f)
        viewModel.updateLocation(location)
        viewModel.onAction(PlaceCreateAction.OnTripSelect(listOf(trip)))
        viewModel.updateCategory(emptyList())

        val state = viewModel.uiState.value
        assertEquals(listOf(uri1), state.images)
        assertEquals(2f, state.thumbnailImageRatio, 0f)
        assertEquals(location, state.location)
        assertEquals(listOf(trip), state.trips)
    }

    @Test
    fun `이미지를 선택하면 selectedImage가 갱신된다`() {
        viewModel.onAction(PlaceCreateAction.OnImageSelect(uri1))

        assertEquals(uri1, viewModel.uiState.value.selectedImage)
    }

    @Test
    fun `선택된 이미지를 삭제하면 남은 첫 이미지가 선택된다`() {
        viewModel.updateImages(listOf(uri1, uri2), 1f)
        viewModel.onAction(PlaceCreateAction.OnImageSelect(uri1))

        viewModel.onAction(PlaceCreateAction.OnImagesRemove(uri1))

        assertEquals(listOf(uri2), viewModel.uiState.value.images)
        assertEquals(uri2, viewModel.uiState.value.selectedImage)
    }

    @Test
    fun `선택되지 않은 이미지를 삭제하면 선택은 유지된다`() {
        viewModel.updateImages(listOf(uri1, uri2), 1f)
        viewModel.onAction(PlaceCreateAction.OnImageSelect(uri2))

        viewModel.onAction(PlaceCreateAction.OnImagesRemove(uri1))

        assertEquals(uri2, viewModel.uiState.value.selectedImage)
    }

    @Test
    fun `마지막 한 장은 삭제할 수 없고 에러 스낵바를 보여준다`() {
        viewModel.updateImages(listOf(uri1), 1f)

        viewModel.onAction(PlaceCreateAction.OnImagesRemove(uri1))

        assertEquals(listOf(uri1), viewModel.uiState.value.images)
        verify { snackBarManager.show(SnackBarEvent.IMAGE_COUNT_ERROR) }
    }

    @Test
    fun `클릭 액션들은 대응되는 이벤트를 발생시킨다`() = runTest {
        viewModel.event.test {
            viewModel.onAction(PlaceCreateAction.OnCategoryClick)
            assertEquals(PlaceCreateEvent.NavigateToCategory, awaitItem())

            viewModel.onAction(PlaceCreateAction.OnLocationClick)
            assertEquals(PlaceCreateEvent.NavigateToLocation, awaitItem())

            viewModel.onAction(PlaceCreateAction.OnTripClick)
            assertEquals(PlaceCreateEvent.NavigateToTrip, awaitItem())

            viewModel.onAction(PlaceCreateAction.OnCreateSuccess)
            assertEquals(PlaceCreateEvent.NavigateToHome, awaitItem())
        }
    }

    @Test
    fun `필수 값이 하나라도 없으면 생성 요청을 보내지 않는다`() {
        // image X
        PlaceCreateViewModel(placeRepository, snackBarManager, toxicityAnalyzer).apply {
            prepareValidState(this)
            updateImages(emptyList(), 1f)
            onAction(PlaceCreateAction.OnPlaceCreate(context))
        }

        // 위치 X
        PlaceCreateViewModel(placeRepository, snackBarManager, toxicityAnalyzer).apply {
            updateImages(listOf(uri1), 1f)
            updateTrip(listOf(trip))
            onAction(PlaceCreateAction.OnTitleChange("제목"))
            onAction(PlaceCreateAction.OnPlaceCreate(context))
        }

        // 제목 X
        PlaceCreateViewModel(placeRepository, snackBarManager, toxicityAnalyzer).apply {
            prepareValidState(this)
            onAction(PlaceCreateAction.OnTitleChange(" "))
            onAction(PlaceCreateAction.OnPlaceCreate(context))
        }

        // 여행 X
        PlaceCreateViewModel(placeRepository, snackBarManager, toxicityAnalyzer).apply {
            prepareValidState(this)
            updateTrip(emptyList())
            onAction(PlaceCreateAction.OnPlaceCreate(context))
        }

        coVerify(exactly = 0) { placeRepository.createPlace(any()) }
        verify(exactly = 0) { toxicityAnalyzer.predict(any()) }
    }

    @Test
    fun `유해 문장이 감지되면 에러 라벨을 저장하고 업로드와 생성을 하지 않는다` () {
        every { toxicityAnalyzer.predict("내용") } returns listOf("toxic" to 0.9f)
        prepareValidState()

        viewModel.onAction(PlaceCreateAction.OnPlaceCreate(context))

        assertEquals("toxic", viewModel.uiState.value.contentErrorLabel)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 0) { placeRepository.uploadImage(any()) }
        coVerify(exactly = 0) { placeRepository.createPlace(any()) }
    }

    @Test
    fun `생성에 성공하면 이미지를 업로드해 요청을 만들고 홈 이동 이벤트를 발생시킨다`() = runTest {
        val request = slot<PlaceCreateUpdate>()
        coEvery { placeRepository.createPlace(capture(request)) } returns Result.success(mockk(relaxed = true))
        prepareValidState()

        viewModel.event.test {
            viewModel.onAction(PlaceCreateAction.OnPlaceCreate(context))

            assertEquals(PlaceCreateEvent.NavigateToHome, awaitItem())
        }
        coVerify(exactly = 2) { placeRepository.uploadImage(any<File>()) }
        with(request.captured) {
            assertEquals(listOf("t1"), tripIds)
            assertEquals("제목", title)
            assertEquals("내용", content)
            assertEquals(37.5, latitude, 0.0)
            assertEquals(127.0, longitude, 0.0)
            assertEquals(Address.from("서울 강남구"), address)
            assertEquals(listOf("url-1", "url-2"), imageUrls)
            assertEquals(1.5f, thumbnailImageRatio, 0f)
            assertTrue(isPublic)
        }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `일부 이미지 업로드가 실패하면 성공한 이미지 URL만 담아 생성한다`() {
        val request = slot<PlaceCreateUpdate>()
        coEvery { placeRepository.uploadImage(any()) } returnsMany listOf(
            Result.failure(RuntimeException()),
            Result.success(PlaceImageUploadResponse("url-2"))
        )
        coEvery { placeRepository.createPlace(capture(request)) } returns Result.success(mockk(relaxed = true))
        prepareValidState()

        viewModel.onAction(PlaceCreateAction.OnPlaceCreate(context))

        assertEquals(listOf("url-2"), request.captured.imageUrls)
    }

    @Test
    fun `생성에 실패하면 저장 실패 스낵바를 보여주고 이동하지 않는다`() = runTest {
        coEvery { placeRepository.createPlace(any()) } returns Result.failure(RuntimeException())
        prepareValidState()

        viewModel.event.test {
            viewModel.onAction(PlaceCreateAction.OnPlaceCreate(context))

            expectNoEvents()
        }
        verify { snackBarManager.show(SnackBarEvent.DATA_SAVE_FAILED) }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `생성 중에 다시 생성을 요청하면 무시된다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { placeRepository.createPlace(any()) } coAnswers {
            gate.await()
            Result.success(mockk(relaxed = true))
        }
        prepareValidState()

        viewModel.onAction(PlaceCreateAction.OnPlaceCreate(context))
        assertTrue(viewModel.uiState.value.isLoading)
        viewModel.onAction(PlaceCreateAction.OnPlaceCreate(context))
        gate.complete(Unit)

        coVerify (exactly = 1) { placeRepository.createPlace(any()) }
        assertFalse(viewModel.uiState.value.isLoading)
    }
}