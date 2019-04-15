package com.hiczp.caeruleum.test

import com.google.gson.JsonElement
import com.hiczp.caeruleum.annotation.*
import com.hiczp.caeruleum.create
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockHttpResponse
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
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
                "method" to method.value
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

    @Get("users")
    fun withQueryParam(@Query param1: String, @Query param2: String): Deferred<JsonElement>

    @Post("user")
    @FormUrlEncoded
    fun postFormUrlEncoded(@Field arg1: String, @Field("czp") arg2: String? = null): Deferred<JsonElement>

    @Post("user")
    @FormUrlEncoded
    fun postEmptyContent(): Deferred<JsonElement>

    @Post("user")
    @FormUrlEncoded
    fun postWithMap(@FieldMap fieldMap: Map<String, String>): Deferred<JsonElement>

    @Delete
    fun delete(): Deferred<JsonElement>

    @Post("user")
    fun postWithoutBody(): Deferred<JsonElement>

    fun nonAbstract() = "hello"

    suspend fun nonAbstractAndSuspend(arg1: String) = arg1

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
            url == LOCALHOST + "users?param1=czp1&param2=czp2"
        }
    }

    @Test
    fun postFormUrlEncoded() = runBlocking {
        service.postFormUrlEncoded("01", "02").await().assert {
            contentLength == "arg1=01&czp=02".length
        }
    }

    @Test
    fun repeatPost() = runBlocking {
        service.postFormUrlEncoded("001", "002").await().assert {
            contentLength == "arg1=001&czp=002".length
        }
    }

    @Test
    fun postWithNullValue() = runBlocking {
        service.postFormUrlEncoded("01").await().assert {
            contentLength == "arg1=01".length
        }
    }

    @Test
    fun postEmptyContent() = runBlocking {
        service.postEmptyContent().await().assert {
            contentLength == 0
        }
    }

    @Test
    fun postWithMap() = runBlocking {
        service.postWithMap(
            mapOf(
                "arg1" to "02",
                "arg2" to "01"
            )
        ).await().assert {
            contentLength == "arg1=02&arg2=01".length
        }
    }

    @Test
    fun deleteMethod() = runBlocking {
        service.delete().await().assert {
            method == HttpMethod.Delete.value
        }
    }

    @Test
    fun postWithoutBody() = runBlocking {
        service.postWithoutBody().await().assert {
            contentLength == null
        }
    }

//    @Test
//    fun nonAbstract()= service.nonAbstract().assert {
//        this=="hello"
//    }

    @AfterAll
    fun dispose() {
        httpClient.close()
    }
}
