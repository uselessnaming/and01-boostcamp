package com.andone.memorip.data.place.datasource.remote

import android.content.Context
import android.content.res.AssetManager
import com.andone.memorip.data.common.ApiResult
import com.andone.memorip.data.place.datasource.PlaceService
import com.andone.memorip.data.place.model.PlaceGroupsUpdateRequest
import com.andone.memorip.data.place.model.PlaceListItemResponse
import com.andone.memorip.domain.model.NetworkError
import com.andone.memorip.domain.model.Region
import com.andone.memorip.domain.model.response.PlaceImageUploadResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

class PlaceRemoteDataSourceImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val service: PlaceService = mockk()
    private val context: Context = mockk()
    private val assetManager: AssetManager = mockk()
    private val dataSource = PlaceRemoteDataSourceImpl(service, context)

    private fun stubRegions(json: String) {
        every { assetManager.open("regions.json") } answers { ByteArrayInputStream(json.toByteArray()) }
    }

    @Before
    fun setUp() {
        every { context.assets } returns assetManager
    }

    @Test
    fun `객체의 키는 상위 지역이 되고 배열 값은 하위 지역이 된다`() {
        stubRegions("""{"서울":["강남구","마포구"],"부산":["해운대구"]}""")

        val roots = dataSource.loadRegions()

        assertEquals(listOf("서울", "부산"), roots.map { it.name })
        assertEquals(listOf(1, 1), roots.map { it.level })
        assertEquals(listOf("강남구", "마포구"), roots[0].subRegions.map { it.name })
        assertEquals(listOf(2, 2), roots[0].subRegions.map { it.level })
        assertEquals("서울", roots[0].subRegions[0].parent?.name)
    }

    @Test
    fun `객체가 중첩되면 레벨이 하나씩 깊어진다`() {
        stubRegions("""{"경기":{"수원시":["장안구","팔달구"]}}""")

        val gyeonggi = dataSource.loadRegions().single()
        val suwon = gyeonggi.subRegions.single()

        assertEquals(1, gyeonggi.level)
        assertEquals(2, suwon.level)
        assertEquals(listOf("장안구", "팔달구"), suwon.subRegions.map { it.name })
        assertEquals(listOf(3, 3), suwon.subRegions.map { it.level })
    }

    @Test
    fun `배열 안에 문자열과 객체가 섞여 있어도 같은 레벨의 하위 지역으로 처리한다`() {
        stubRegions("""{"경기":["수원시",{"성남시":["분당구","수정구"]}]}""")

        val gyeonggi = dataSource.loadRegions().single()
        val seongnam = gyeonggi.subRegions.first { it.name == "성남시" }

        assertEquals(listOf("수원시", "성남시"), gyeonggi.subRegions.map { it.name })
        assertEquals(2, seongnam.level)
        assertEquals(listOf("분당구", "수정구"), seongnam.subRegions.map { it.name })
        assertEquals(3, seongnam.subRegions.first().level)
    }

    @Test
    fun `문자열이 아닌 값은 지역에서 제외한다`() {
        stubRegions("""{"서울":["강남구",3,true]}""")

        assertEquals(listOf("강남구"), dataSource.loadRegions().single().subRegions.map { it.name })
    }

    @Test
    fun `빈 객체는 빈 목록이다`() {
        stubRegions("{}")

        assertTrue(dataSource.loadRegions().isEmpty())
    }

    @Test
    fun `실제 regions json 파일이 올바른 계층 구조로 파싱된다`() {
        val file = File("../app/src/main/assets/regions.json")
        assumeTrue("regions.json 위치가 달라져 건너뜀", file.exists())
        every { assetManager.open("regions.json") } answers { FileInputStream(file) }

        val roots = dataSource.loadRegions()

        assertFalse(roots.isEmpty())
        fun verify(region: Region, expectedLevel: Int) {
            assertTrue("이름이 비어 있음", region.name.isNotBlank())
            assertEquals("${region.name}의 레벨", expectedLevel, region.level)
            region.subRegions.forEach { verify(it, expectedLevel + 1) }
        }
        roots.forEach { verify(it, 1) }
    }

    @Test
    fun `장소의 여행 변경 요청은 추가 제거 목록으로 요청 모델을 만든다`() = runTest {
        val request = slot<PlaceGroupsUpdateRequest>()
        coEvery { service.updatePlaceTrips("p1", capture(request)) } returns ApiResult(data = Unit)

        val result =
            dataSource.updatePlaceTrips("p1", addTripIds = listOf("a"), removeTripIds = listOf("b"))

        assertTrue(result.isSuccess)
        assertEquals(
            PlaceGroupsUpdateRequest(
                addGroupIds = listOf("a"),
                removeGroupIds = listOf("b")
            ), request.captured
        )
    }

    @Test
    fun `장소 삭제가 404이면 NotFoundError로 실패한다`() = runTest {
        coEvery { service.deletePlace("p1") } throws HttpException(
            Response.error<Any>(404, "".toResponseBody("application/json".toMediaType()))
        )

        assertTrue(dataSource.deletePlace("p1").exceptionOrNull() is NetworkError.NotFoundError)
    }

    @Test
    fun `장소 목록 조회는 서비스 응답을 그대로 반환한다`() = runTest {
        val response = ApiResult<List<PlaceListItemResponse>>(data = emptyList())
        coEvery {
            service.getPlaces(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns response

        val result = dataSource.getPlaceList(query = "카페", page = 0, size = 20)

        assertSame(response, result)
        coVerify { service.getPlaces("카페", null, null, null, 0, 20, null) }
    }

    @Test
    fun `이미지 업로드에 성공하면 응답의 이미지 URL을 반환한다`() = runTest {
        val file = tempFolder.newFile("a.jpg")
        coEvery { service.uploadImage(any()) } returns ApiResult(data = PlaceImageUploadResponse("url"))

        val result = dataSource.uploadImage(file)

        assertEquals("url", result.getOrNull()!!.imageUrl)
    }
}