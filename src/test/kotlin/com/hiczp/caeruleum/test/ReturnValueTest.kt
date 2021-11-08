package com.hiczp.caeruleum.test

import com.google.gson.JsonElement
import com.hiczp.caeruleum.annotation.Get
import com.hiczp.caeruleum.test.mock.createMockService
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.testng.annotations.Test
import kotlin.test.assertTrue

@Suppress("DeferredIsResult", "USELESS_IS_CHECK")
class ReturnValueTest {
    private interface Service {
        @Get
        fun noReturnValue()

        @Get
        fun returnJob(): Job

        @Get
        fun returnDeferredUnit(): Deferred<Unit>

        @Get
        fun returnDeferredJson(): Deferred<JsonElement>

        @Get
        suspend fun suspendNoReturnValue()

        @Get
        suspend fun suspendReturnJson(): JsonElement

        @Get
        suspend fun suspendReturnJob(): Job

        @Get
        suspend fun suspendReturnDeferredJson(): Deferred<JsonElement>
    }

    private val service = createMockService<Service>()

    @Test
    fun noReturnValue() {
        runBlocking {
            assertTrue { service.noReturnValue() is Unit }
        }
    }

    @Test
    fun returnJob() = runBlocking {
        service.returnJob().join()
    }

    @Test
    fun returnDeferredUnit() {
        runBlocking {
            assertTrue { service.returnDeferredUnit().join() is Unit }
        }
    }

    @Test
    fun returnDeferredJson() = runBlocking {
        assertTrue { service.returnDeferredJson().await() is JsonElement }
    }

    @Test
    fun suspendNoReturnValue() {
        runBlocking {
            assertTrue { service.suspendNoReturnValue() is Unit }
        }
    }

    @Test
    fun suspendReturnJson() {
        runBlocking {
            assertTrue { service.suspendReturnJson() is JsonElement }
        }
    }

    @Test
    fun suspendReturnJob() {
        runBlocking {
            assertTrue { service.suspendReturnJob().join() is Unit }
        }
    }

    @Test
    fun suspendReturnDeferredJson() {
        runBlocking {
            assertTrue { service.suspendReturnDeferredJson().await() is JsonElement }
        }
    }
}
