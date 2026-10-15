package com.andone.memorip.presentation.viewmodeltest

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.cash.turbine.test
import com.andone.memorip.presentation.MainDispatcherRule
import com.andone.memorip.presentation.screen.selectimage.SelectImageViewModel
import com.andone.memorip.presentation.screen.selectimage.model.CropTransformData
import com.andone.memorip.presentation.screen.selectimage.model.SelectImageAction
import com.andone.memorip.presentation.screen.selectimage.model.SelectImageEvent
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SelectImageViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private lateinit var viewModel: SelectImageViewModel

    private val uri1: Uri = mockk()
    private val uri2: Uri = mockk()

    private fun transform(width: Float, height: Float) = CropTransformData(
        scale = 1f,
        offset = Offset.Zero,
        cropRect = Rect(0f, 0f, width, height)
    )

    @Before
    fun setUp() {
        viewModel = SelectImageViewModel()
    }

    @Test
    fun `초기 상태는 모두 비어 있다`() {
        val state = viewModel.uiState.value

        assertTrue(state.selectedImages.isEmpty())
        assertTrue(state.croppedImages.isEmpty())
        assertTrue(state.transformData.isEmpty())
    }

    @Test
    fun `이미지를 선택하면 selectedImages가 갱신된다`() {
        viewModel.onAction(SelectImageAction.OnImagesSelect(listOf(uri1, uri2)))

        assertEquals(listOf(uri1, uri2), viewModel.uiState.value.selectedImages)
    }

    @Test
    fun `이미지를 다시 선택하면 이전 선택이 교체된다`() {
        viewModel.onAction(SelectImageAction.OnImagesSelect(listOf(uri1, uri2)))
        viewModel.onAction(SelectImageAction.OnImagesSelect(listOf(uri2)))

        assertEquals(listOf(uri2), viewModel.uiState.value.selectedImages)
    }

    @Test
    fun `크롭 완료 시 상태가 갱신되고 첫 이미지의 비율로 장소 선택 화면 이동 이벤트가 발생한다`() = runTest {
        val first = transform(width = 200f, height = 100f)
        val second = transform(width = 100f, height = 100f)
        val transformData = mapOf(uri1 to first, uri2 to second)

        viewModel.event.test {
            viewModel.onAction(SelectImageAction.OnImagesCrop(listOf(uri1, uri2), transformData))

            assertEquals(
                SelectImageEvent.NavigateToSelectLocation(images = listOf(uri1, uri2), thumbnailImageRatio = 2f),
                awaitItem()
            )
        }
        assertEquals(listOf(uri1, uri2), viewModel.uiState.value.croppedImages)
        assertEquals(transformData, viewModel.uiState.value.transformData)
    }

    @Test
    fun `크롭 영역의 높이가 0이면 비율은 1로 전달된다`() = runTest {
        val transformData = mapOf(uri1 to transform(width = 200f, height = 0f))

        viewModel.event.test {
            viewModel.onAction(SelectImageAction.OnImagesCrop(listOf(uri1), transformData))

            assertEquals(SelectImageEvent.NavigateToSelectLocation(listOf(uri1), 1f), awaitItem())
        }
    }

    @Test(expected = NoSuchElementException::class)
    fun `transformData가 비어있으면 크롭 완료 시 예외가 발생한다 - 현재 동작`() {
        viewModel.onAction(SelectImageAction.OnImagesCrop(listOf(uri1), emptyMap()))
    }

    @Test
    fun `뒤로가기 클릭 시 NavigateBack 이벤트가 발생한다`() = runTest {
        viewModel.event.test {
            viewModel.onAction(SelectImageAction.OnBack)

            assertEquals(SelectImageEvent.NavigateBack, awaitItem())
        }
    }
}