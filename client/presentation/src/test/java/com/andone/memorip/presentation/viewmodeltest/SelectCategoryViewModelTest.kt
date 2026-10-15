package com.andone.memorip.presentation.viewmodeltest

import androidx.compose.ui.graphics.Color
import androidx.paging.PagingData
import app.cash.turbine.test
import com.andone.memorip.domain.model.Tag
import com.andone.memorip.domain.repository.TagRepository
import com.andone.memorip.presentation.MainDispatcherRule
import com.andone.memorip.presentation.model.TagUiModel
import com.andone.memorip.presentation.screen.selectcategory.SelectCategoryViewModel
import com.andone.memorip.presentation.screen.selectcategory.model.SelectCategoryAction
import com.andone.memorip.presentation.screen.selectcategory.model.SelectCategoryError
import com.andone.memorip.presentation.screen.selectcategory.model.SelectCategoryEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SelectCategoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tagRepository: TagRepository = mockk(relaxed = true)
    private lateinit var viewModel: SelectCategoryViewModel

    private fun tag(id: String) = TagUiModel(id = id, name = "tag$id", color = Color.Red)

    @Before
    fun setUp() {
        every { tagRepository.loadTags() } returns flowOf(PagingData.empty())
        viewModel = SelectCategoryViewModel(tagRepository)
    }

    @Test
    fun `카테고리 3개 선택 후 4번째 클릭하면 에러 스낵바 이벤트가 발생한다`() = runTest {
        val initial = listOf(tag("1"), tag("2"))

        viewModel.onAction(SelectCategoryAction.OnInitialTags(initial))

        assertEquals(initial, viewModel.uiState.value.checkedCategories)
    }

    @Test
    fun `선택되지 않은 카테고리를 클릭하면 선택 목록에 추가된다`() {
        viewModel.onAction(SelectCategoryAction.OnCategoryItemClick(tag("1"), checked = true))

        assertEquals(listOf(tag("1")), viewModel.uiState.value.checkedCategories)
    }

    @Test
    fun `이미 선택된 카테고리를 클릭하면 선택이 해제된다`() {
        viewModel.onAction(SelectCategoryAction.OnInitialTags(listOf(tag("1"))))
        viewModel.onAction(SelectCategoryAction.OnCategoryItemClick(tag("1"), checked = false))

        assertEquals(emptyList<TagUiModel>(), viewModel.uiState.value.checkedCategories)
    }

    @Test
    fun `3개 선택된 상태에서 추가 선택하면 에러 이벤트가 발생하고 목록은 유지된다`() = runTest {
        val selected = listOf(tag("1"), tag("2"), tag("3"))
        viewModel.onAction(SelectCategoryAction.OnInitialTags(selected))

        viewModel.event.test {
            viewModel.onAction(SelectCategoryAction.OnCategoryItemClick(tag("4"), checked = true))

            assertEquals(
                SelectCategoryEvent.ShowSnackBar(SelectCategoryError.MaxCategoryOverError),
                awaitItem()
            )
        }

        assertEquals(selected, viewModel.uiState.value.checkedCategories)
    }

    @Test
    fun `확인 클릭 시 현재 선택 목록과 함께 SelectCategory 이벤트가 발생한다`() = runTest {
        val selected = listOf(tag("1"), tag("2"))
        viewModel.onAction(SelectCategoryAction.OnInitialTags(selected))

        viewModel.event.test {
            viewModel.onAction(SelectCategoryAction.OnConfirmClick)

            assertEquals(SelectCategoryEvent.SelectCategory(selected), awaitItem())
        }
    }

    @Test
    fun `단순 클릭 액션들은 대응되는 이벤트를 발생시킨다`() = runTest {
        viewModel.event.test {
            viewModel.onAction(SelectCategoryAction.OnBackClick)
            assertEquals(SelectCategoryEvent.NavigateBack, awaitItem())

            viewModel.onAction(SelectCategoryAction.OnFABClick)
            assertEquals(SelectCategoryEvent.ShowDialog, awaitItem())

            viewModel.onAction(SelectCategoryAction.OnDialogCancelClick)
            assertEquals(SelectCategoryEvent.DismissDialog, awaitItem())
        }
    }

    @Test
    fun `태그 추가에 성공하면 다이얼로그 닫기 이벤트가 발생한다`() = runTest {
        coEvery { tagRepository.addTags(any()) } answers { Result.success(firstArg<Tag>()) }

        viewModel.event.test {
            viewModel.onAction(SelectCategoryAction.OnDialogConfirmClick("맛집", Color.Blue))

            assertEquals(SelectCategoryEvent.DismissDialog, awaitItem())
        }

        coVerify(exactly = 1) {
            tagRepository.addTags(match { it.name == "맛집" && it.color == "#0000FF" })
        }
    }

    @Test
    fun `태그 추가에 실패하면 이벤트가 발생하지 않는다`() = runTest {
        coEvery { tagRepository.addTags(any()) } returns Result.failure(RuntimeException())

        viewModel.event.test {
            viewModel.onAction(SelectCategoryAction.OnDialogConfirmClick("맛집", Color.Blue))

            expectNoEvents()
        }
    }
}