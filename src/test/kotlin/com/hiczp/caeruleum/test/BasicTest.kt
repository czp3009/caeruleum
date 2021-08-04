package com.hiczp.caeruleum.test

import com.hiczp.caeruleum.annotation.*
import com.hiczp.caeruleum.test.mock.createMockService
import io.ktor.client.statement.*
import io.ktor.content.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.testng.annotations.Test
import kotlin.test.assertEquals

private data class Request(val data: String)
private data class Response(val data: String)

@DefaultContentType("application/json")
@BaseUrl("https://localhost")
private interface Service {
    @Get("ok")
    suspend fun ok(): HttpResponse

    @Post("echo")
    suspend fun echoString(@Body body: TextContent): String

    @Post("echo")
    suspend fun echoJson(@Body body: Request): Response
}

class BasicTest {
    private val service = createMockService<Service>()

    @Test
    fun ok() = runBlocking {
        val response = service.ok()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun echoString() = runBlocking {
        val body = "i am body"
        val result = service.echoString(TextContent(body, ContentType.Text.Plain))
        assertEquals(body, result)
    }

    @Test
    fun echoJson() = runBlocking {
        val data = "i am data"
        val response = service.echoJson(Request(data))
        assertEquals(data, response.data)
    }
}
