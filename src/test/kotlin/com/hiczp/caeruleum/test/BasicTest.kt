package com.hiczp.caeruleum.test

import com.hiczp.caeruleum.annotation.BaseUrl
import com.hiczp.caeruleum.annotation.Get
import com.hiczp.caeruleum.test.mock.createMockService
import kotlinx.coroutines.runBlocking
import org.testng.annotations.Test

@BaseUrl("https://localhost")
private interface Service {
    @Get("echo")
    suspend fun echo(body: String): String
}

private class BasicTest {
    private val service = createMockService<Service>()

    @Test
    fun echoTest() {
        runBlocking {
            val body = "i am body"
            service.echo(body)
        }
    }
}
