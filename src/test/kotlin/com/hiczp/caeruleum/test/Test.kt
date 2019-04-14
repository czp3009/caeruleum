package com.hiczp.caeruleum.test

import com.google.gson.JsonElement
import com.hiczp.caeruleum.annotation.BaseUrl
import com.hiczp.caeruleum.annotation.Get
import com.hiczp.caeruleum.annotation.Path
import com.hiczp.caeruleum.annotation.Query
import com.hiczp.caeruleum.create
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockHttpResponse
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.json
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test

const val LOCALHOST = "https://localhost/"

val httpMockEngine = MockEngine {
    MockHttpResponse(
        call,
        HttpStatusCode.OK,
        ByteReadChannel(
            json {
                "header" to headers.toString()
                "url" to url.toString()
                "contentLength" to content.contentLength
            }.toString()
        ),
        headersOf("Content-Type", ContentType.Application.Json.toString())
    )
}

@Suppress("DeferredIsResult")
@BaseUrl(LOCALHOST)
interface Service {
    @Get
    fun noReturnValue(): Job

    @Get
    fun withReturnValue(): Deferred<JsonElement>

    @Get("user")
    fun withUrl(): Deferred<JsonElement>

    @Get("users/{user}/repos")
    fun withPathVariable(@Path user: String): Deferred<JsonElement>

    @Get
    fun withQueryParam(@Query param1: String, @Query param2: String): Deferred<JsonElement>

    fun nonAbstract() = "hello"

    @JvmDefault
    fun jvmDefault() = "hello"
}

@TestMethodOrder(NatureOrder::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Test {
    private val httpClient = HttpClient(httpMockEngine) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }
    lateinit var service: Service

    @BeforeAll
    fun init() {
        service = httpClient.create()
    }

    @Test
    fun noReturnValue() = runBlocking {
        service.noReturnValue().join()
    }

    @Test
    fun withReturnValue() = runBlocking {
        service.withReturnValue().await().assert {
            url == LOCALHOST
        }
    }

    @Test
    fun withUrl() = runBlocking {
        service.withUrl().await().assert {
            url == LOCALHOST + "user"
        }
    }

    @Test
    fun withPathVariable() = runBlocking {
        service.withPathVariable("czp3009").await().assert {
            url == LOCALHOST + "users/czp3009/repos"
        }
    }

    @Test
    fun withQueryParam() = runBlocking {
        service.withQueryParam("czp1", "czp2").await().assert {
            url == "$LOCALHOST?param1=czp1&param2=czp2"
        }
    }

    @AfterAll
    fun dispose() {
        httpClient.close()
    }
}
