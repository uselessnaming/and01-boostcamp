package com.andone.memorip.presentation.modeltest

import com.andone.memorip.presentation.screen.placelist.model.PlaceListUiState
import com.andone.memorip.presentation.screen.placelist.model.RegionUiModel
import com.andone.memorip.presentation.screen.placelist.model.SelectedRegionState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceListUiStateTest {

    private val gangnam = RegionUiModel(id = "gangnam", name = "강남구", level = 2)
    private val mapo = RegionUiModel(id = "mapo", name = "마포구", level = 2)
    private val seoul =
        RegionUiModel(id = "seoul", name = "서울", level = 1, child = listOf(gangnam, mapo))
    private val sejong = RegionUiModel(id = "sejong", name = "세종", level = 1)

    private fun state(
        parents: List<RegionUiModel> = emptyList(),
        child: Set<RegionUiModel> = emptySet()
    ) = PlaceListUiState(
        rootRegions = listOf(seoul, sejong),
        selectedRegionState = SelectedRegionState(parents = parents, child = child)
    )

    private fun PlaceListUiState.listAsPairs() = currentRegionList.map { it.name to it.isSelected }

    @Test
    fun `선택이 없으면 최상위 지역을 보여주고 레벨은 0이다`() {
        val state = state()

        assertEquals(listOf("서울" to false, "세종" to false), state.listAsPairs())
        assertEquals(0, state.currentLevel)
    }

    @Test
    fun `시도를 선택하면 하위 지역을 보여준다`() {
        val state = state(parents = listOf(seoul))

        assertEquals(listOf("강남구" to false, "마포구" to false), state.listAsPairs())
        assertEquals(1, state.currentLevel)
    }

    @Test
    fun `하위 지역까지 선택하면 같은 레벨의 목록을 유지하고 선택 표시한다`() {
        val state = state(parents = listOf(seoul, gangnam))

        assertEquals(listOf("강남구" to true, "마포구" to false), state.listAsPairs())
        assertEquals(2, state.currentLevel)
    }

    @Test
    fun `여러 하위 지역을 다중 선택하면 모두 선택 표시되고 레벨은 부모 수 더하기 1이다`() {
        val state = state(parents = listOf(seoul), child = setOf(gangnam, mapo))

        assertEquals(listOf("강남구" to true, "마포구" to true), state.listAsPairs())
        assertEquals(2, state.currentLevel)
    }

    @Test
    fun `하위 지역이 없는 시도를 선택하면 최상위 목록에서 선택 표시된다`() {
        val state = state(parents = listOf(sejong))

        assertEquals(listOf("서울" to false, "세종" to true), state.listAsPairs())
        assertEquals(1, state.currentLevel)
    }
}