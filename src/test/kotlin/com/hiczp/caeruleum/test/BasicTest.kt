package com.hiczp.caeruleum.test

import com.hiczp.caeruleum.annotation.Body
import com.hiczp.caeruleum.annotation.DefaultContentType
import com.hiczp.caeruleum.annotation.Get
import com.hiczp.caeruleum.annotation.Post
import com.hiczp.caeruleum.test.mock.createMockService
import com.hiczp.caeruleum.test.model.RequestBody
import com.hiczp.caeruleum.test.model.ResponseBody
import io.ktor.client.statement.*
import io.ktor.content.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BasicTest {
    @DefaultContentType("application/json")
    private interface Service {
        @Get("ok")
        suspend fun ok(): HttpResponse

        @Post("echo")
        suspend fun echoString(@Body body: TextContent): String

        @Post("echo")
        suspend fun echoJson(@Body body: RequestBody): ResponseBody
    }

    private val service = createMockService<Service>()

    @Test
    fun ok() {
        runBlocking {
            val response = service.ok()
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun echoString() {
        runBlocking {
            val body = "i am body"
            val result = service.echoString(TextContent(body, ContentType.Text.Plain))
            assertEquals(body, result)
        }
    }

    @Test
    fun echoJson() {
        runBlocking {
            val data = "i am data"
            val response = service.echoJson(RequestBody(data))
            assertEquals(data, response.data)
        }
    }
}
