package com.andone.memorip.data.data

import com.andone.memorip.data.tag.model.TagRequest
import com.andone.memorip.data.tag.model.TagResponse
import com.andone.memorip.data.tag.model.toDataModel
import com.andone.memorip.data.tag.model.toDomainModel
import com.andone.memorip.data.user.model.UserResponse
import com.andone.memorip.data.user.model.toDomain
import com.andone.memorip.domain.model.Tag
import com.andone.memorip.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Test

class DataMappingTest {

    @Test
    fun `TagResponse를 도메인 Tag로 변환한다`() {
        assertEquals(
            Tag(id = "1", name = "맛집", color = "#FF0000"),
            TagResponse(id = "1", name = "맛집", colorHex = "#FF0000").toDomainModel()
        )
    }

    @Test
    fun `도메인 Tag를 TagRequest로 변환할 때 id는 보내지 않는다`() {
        assertEquals(
            TagRequest(name = "맛집", colorHex = "#FF0000"),
            Tag(id = "1", name = "맛집", color = "#FF0000").toDataModel()
        )
    }

    @Test
    fun `UserResponse를 도메인 User로 변환한다`() {
        assertEquals(
            User(id = "1",nickname = "닉네임", profileImg = "img"),
            UserResponse(id = "1", nickname = "닉네임", profileImage = "img").toDomain()
        )
    }
}