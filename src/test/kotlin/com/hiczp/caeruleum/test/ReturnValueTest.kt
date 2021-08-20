package com.hiczp.caeruleum.test

import com.hiczp.caeruleum.annotation.Get
import com.hiczp.caeruleum.test.mock.createMockService
import com.hiczp.caeruleum.test.mock.mockHttpClient
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import org.testng.annotations.Test

class ReturnValueTest {
    private interface Service {
        @Get
        suspend fun suspendNoReturnValue()
    }

    private val service = createMockService<Service>()

    @Test
    fun httpClientGetUnit() = runBlocking {
        mockHttpClient.get<Unit>()
    }

    @Test
    fun suspendNoReturnValue() = runBlocking {
        service.suspendNoReturnValue()
    }
}
