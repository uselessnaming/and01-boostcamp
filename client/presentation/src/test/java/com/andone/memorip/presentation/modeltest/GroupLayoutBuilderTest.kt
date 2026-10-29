package com.andone.memorip.presentation.modeltest

import com.andone.memorip.presentation.screen.triplist.model.TripItem
import com.andone.memorip.presentation.util.buildBento5x3Items
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupLayoutBuilderTest {

    private fun images(count: Int) = (1..count).map { "img$it" }

    @Test
    fun `이미지가 없으면 빈 목록이다`() {
        assertTrue(buildBento5x3Items(emptyList()).isEmpty())
    }

    @Test
    fun `이미지 한 장은 전체 영역을 차지한다`() {
        assertEquals(
            listOf(TripItem(colSpan = 5, rowSpan = 3, imageUrl = "img1", overNumber = 0)),
            buildBento5x3Items(images(1))
        )
    }

    @Test
    fun `이미지 세 장은 큰 칸 하나와 작은 칸 둘로 구성된다`() {
        val items = buildBento5x3Items(images(3))

        assertEquals(listOf(3 to 3, 2 to 2, 2 to 1), items.map { it.colSpan to it.rowSpan })
    }

    @Test
    fun `이미지 1장부터 7장까지 칸 넓이의 합은 항상 15이다`() {
        (1..7).forEach { cnt ->
            val area = buildBento5x3Items(images(cnt)).sumOf { it.colSpan * it.rowSpan }

            assertEquals("이미지 ${cnt}장", 15, area)
        }
    }

    @Test
    fun `이미지 1장부터 7장까지는 순서대로 모두 배치되고 초과 표시는 없다`() {
        (1..7).forEach { cnt ->
            val items = buildBento5x3Items(images(cnt))

            assertEquals("이미지 ${cnt}장", images(cnt), items.map { it.imageUrl })
            assertTrue(items.all { it.overNumber == 0 })
        }
    }

    @Test
    fun `7장을 넘으면 7장만 보여주고 마지막 칸에 초과 개수를 표시한다`() {
        val items = buildBento5x3Items(images(10))

        assertEquals(7, items.size)
        assertEquals(images(7), items.map { it.imageUrl })
        assertEquals(3, items.last().overNumber)
        assertTrue(items.dropLast(1).all { it.overNumber == 0 })
    }
}