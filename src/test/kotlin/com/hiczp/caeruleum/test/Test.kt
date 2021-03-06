package com.hiczp.caeruleum.test

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.jsonObject
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.hiczp.caeruleum.annotation.*
import com.hiczp.caeruleum.annotation.Headers
import com.hiczp.caeruleum.annotation.Url
import com.hiczp.caeruleum.create
import io.ktor.client.features.*
import io.ktor.client.statement.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test

const val LOCALHOST = "https://localhost/"

@Suppress("DeferredIsResult")
@BaseUrl(LOCALHOST)
@DefaultContentType("application/json")
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

    fun nonAbstract(arg1: String) = arg1

    suspend fun nonAbstractAndSuspend(arg1: String) = arg1

    @JvmDefault
    fun jvmDefault(arg1: String) = arg1

    @JvmDefault
    suspend fun jvmDefaultAndSuspend(arg1: String) = arg1

    @Post
    fun postJson(@Body body: JsonObject): Deferred<JsonElement>

    @Get
    fun url(@Url url: String, @Query arg1: String): Deferred<JsonElement>

    @Get("user/{path}/repos")
    fun pathWithSlash(@Path path: String): Deferred<JsonElement>

    @Get
    fun queryEncode(@Query arg1: String = "!"): Deferred<JsonElement>

    @Post
    @FormUrlEncoded
    fun fieldEncode(
        @FieldMap map: Map<String, String> = mapOf(
            "param1" to "1 2!+/"
        )
    ): Deferred<JsonElement>

    @Get
    fun paramNullable(@Query arg: String?): Deferred<JsonElement>

    companion object {
        fun functionInCompanionObject(arg: String) = arg

        @JvmStatic
        fun jvmStatic(arg: String) = arg
    }

    @Get
    fun raw(): Deferred<HttpResponse>

    @Get
    suspend fun suspendGet(@Query arg1: String = "!"): JsonElement

    @Post
    suspend fun suspendPost(@Body jsonObject: JsonObject): JsonElement

    @Get
    suspend fun bothDeferredAndSuspend(): Deferred<JsonElement>

    @Get("/notFound")
    suspend fun notFound()

    @Post
    @FormUrlEncoded
    suspend fun multiAnnotation(@Query("arg1") @Field("arg2") arg: String = "!")

    @Post
    @FormUrlEncoded
    suspend fun containerAnnotation(
        @Queries(Query("arg1"), Query("arg2"))
        @Fields(Field("field1"), Field("field2"))
        arg: String = "!"
    )

    @Post
    suspend fun postWithTextBody(
        @Body body: TextContent = TextContent("!", ContentType.Text.Plain)
    ): JsonElement

    @Get
    suspend fun getWithEnum(@Query testEnum: TestEnum = TestEnum.MY_NAME_VERY_LONG): JsonElement

    @Headers(["Key: "])
    @Get
    suspend fun headersWithoutValue(): JsonElement

    @Get
    suspend fun queryParamWithArray(@Query args: IntArray = intArrayOf(1, 2, 3)): JsonElement

    @Get
    suspend fun queryParamWithObjectArray(@Query args: Array<String> = arrayOf("1", "2", "3")): JsonElement

    @Get
    suspend fun queryParamWithList(@Query args: List<Int> = listOf(1, 2, 3)): JsonElement

    @Get
    suspend fun queryParamWithVarargs(@Query vararg args: Int = intArrayOf(1, 2, 3)): JsonElement

    @Get
    suspend fun queryParamWithEmpty(@Query args: String = ""): JsonElement

    @Post
    @FormUrlEncoded
    suspend fun fieldWithArray(@Field args: IntArray = intArrayOf(1, 2, 3)): JsonElement

    @Get
    suspend fun queryParamWithNullableArray(@Query args: Array<Int?> = arrayOf(1, null, 2)): JsonElement

    @Get
    suspend fun queryParamWithEnumArray(
        @Query args: Array<TestEnum> = arrayOf(
            TestEnum.MY_NAME_VERY_LONG,
            TestEnum.SORT
        )
    ): JsonElement

    @Post
    suspend fun returnWithHttpResponse(@Body body: String = "HelloWorld"): HttpResponse

    @Post
    suspend fun returnWithHttpStatement(@Body body: JsonObject): HttpStatement
}

interface NoBaseUrl {
    @Get
    suspend fun get(): JsonElement

    @Get
    suspend fun getWithParam(@Query param1: String = "!"): JsonElement

    @Get
    suspend fun dynamic(@Url url: String = LOCALHOST): JsonElement

    @Get(LOCALHOST + "path")
    suspend fun urlInGet(): JsonElement

    companion object {
        const val baseUrl = "https://test.com/"
    }
}

@BaseUrl(LOCALHOST)
interface ProgrammaticBaseUrl {
    @Get
    suspend fun get(): JsonElement
}

enum class TestEnum {
    @EncodeName("sort")
    MY_NAME_VERY_LONG,

    @EncodeName("long")
    SORT
}

@TestMethodOrder(NatureOrder::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Test {
    private val httpClient = createHttpClient()
    private lateinit var service: Service

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
        service.withReturnValue().await().url.assert {
            LOCALHOST
        }
    }

    @Test
    fun withUrl() = runBlocking {
        service.withUrl().await().url.assert {
            LOCALHOST + "user"
        }
    }

    @Test
    fun withPathVariable() = runBlocking {
        service.withPathVariable("czp3009").await().url.assert {
            LOCALHOST + "users/czp3009/repos"
        }
    }

    @Test
    fun withQueryParam() = runBlocking {
        service.withQueryParam("czp1", "czp2").await().url.assert {
            LOCALHOST + "users?param1=czp1&param2=czp2"
        }
    }

    @Test
    fun postFormUrlEncoded() = runBlocking {
        service.postFormUrlEncoded("01", "02").await().body.assert {
            "arg1=01&czp=02"
        }
    }

    @Test
    fun repeatPost() = runBlocking {
        service.postFormUrlEncoded("001", "002").await().body.assert {
            "arg1=001&czp=002"
        }
    }

    @Test
    fun postWithNullValue() = runBlocking {
        service.postFormUrlEncoded("01").await().body.assert {
            "arg1=01"
        }
    }

    @Test
    fun postEmptyContent() = runBlocking {
        service.postEmptyContent().await().body.assert {
            ""
        }
    }

    @Test
    fun postWithMap() = runBlocking {
        service.postWithMap(
            mapOf(
                "arg1" to "02",
                "arg2" to "01"
            )
        ).await().body.assert {
            "arg1=02&arg2=01"
        }
    }

    @Test
    fun deleteMethod() = runBlocking {
        service.delete().await().method.assert {
            HttpMethod.Delete.value
        }
    }

    @Test
    fun postWithoutBody() = runBlocking {
        service.postWithoutBody().await().body.assert {
            ""
        }
    }

    @Test
    fun nonAbstract() = service.nonAbstract("hello").assert {
        "hello"
    }

    @Test
    fun repeatNonAbstract() = service.nonAbstract("hello2").assert {
        "hello2"
    }

    @Test
    fun nonAbstractAndSuspend() = runBlocking {
        service.nonAbstractAndSuspend("hello").assert {
            "hello"
        }
    }

    @Test
    fun jvmDefault() = service.jvmDefault("hello").assert {
        "hello"
    }

    @Test
    fun jvmDefaultAndSuspend() = runBlocking {
        service.jvmDefaultAndSuspend("hello").assert {
            "hello"
        }
    }

    @Test
    fun postJson() = runBlocking {
        val jsonObject = jsonObject(
            "key1" to "value1"
        )
        service.postJson(jsonObject).await().body.assert {
            jsonObject.toString()
        }
    }

    @Test
    fun url() = runBlocking {
        service.url("user", "value1").await().url.assert {
            LOCALHOST + "user?arg1=value1"
        }
    }

    @Test
    fun pathWithSlash() = runBlocking {
        service.pathWithSlash("a/b/c").await().url.assert {
            LOCALHOST + "user/a/b/c/repos"
        }
    }

    @Test
    fun queryWithEncode() = runBlocking {
        service.queryEncode().await().url.assert {
            "$LOCALHOST?arg1=%21"
        }
    }

    @Test
    fun filedEncode() = runBlocking {
        service.fieldEncode().await().body.assert {
            "param1=1+2%21%2B%2F"
        }
    }

    @Test
    fun paramNullable() = runBlocking {
        service.paramNullable(null).await().url.assert {
            LOCALHOST
        }
    }

    @Suppress("RemoveRedundantQualifierName")
    @Test
    fun functionInCompanionObject() = Service.functionInCompanionObject("hello").assert {
        "hello"
    }

    @Suppress("RemoveRedundantQualifierName")
    @Test
    fun jvmStatic() = Service.jvmStatic("hello").assert {
        "hello"
    }

    @Test
    fun toStringMethod() = service.toString().assert {
        "Service interface ${Service::class.qualifiedName}"
    }

    @Test
    fun equalsMethod() = (service == httpClient.create<Service>()).assert {
        true
    }

    @Test
    fun raw() = runBlocking {
        service.raw().await().status.assert {
            HttpStatusCode.OK
        }
    }

    @Test
    fun suspendGet() = runBlocking {
        service.suspendGet().url.assert {
            "$LOCALHOST?arg1=%21"
        }
    }

    @Test
    fun suspendPost() = runBlocking {
        val jsonObject = jsonObject(
            "a" to 1
        )
        service.suspendPost(jsonObject).body.assert { jsonObject.toString() }
    }

    @Test
    fun bothDeferredAndSuspend() {
        assertThrows<IllegalStateException> {
            runBlocking {
                service.bothDeferredAndSuspend().await()
            }
        }
    }

    @Test
    fun closeHttpClient() {
        val oldHttpClient = createHttpClient()
        val oldService = oldHttpClient.create<Service>()
        runBlocking {
            oldService.suspendGet().let(::println)
        }
        oldHttpClient.close()

        val newHttpClient = createHttpClient()
        val newService = newHttpClient.create<Service>()
        runBlocking {
            newService.suspendGet().let(::println)
        }
        newHttpClient.close()
    }

    @Test
    fun callClosedHttpClient() {
        val httpClient = createHttpClient()
        val service = httpClient.create<Service>()
        httpClient.close()
        assertThrows<CancellationException> {
            runBlocking {
                service.suspendGet().let(::println)
            }
        }
    }

    @Test
    fun noBaseUrl() {
        val noBaseUrl = httpClient.create<NoBaseUrl>()
        runBlocking {
            noBaseUrl.get().url.assert {
                "http://localhost/"
            }
        }
    }

    @Test
    fun noBaseUrlWithParam() {
        val noBaseUrl = httpClient.create<NoBaseUrl>()
        runBlocking {
            noBaseUrl.getWithParam().url.assert {
                "http://localhost/?param1=%21"
            }
        }
    }

    @Test
    fun dynamicBaseUrl() {
        val dynamicBaseUrl = httpClient.create<NoBaseUrl>(NoBaseUrl.baseUrl)
        runBlocking {
            dynamicBaseUrl.get().url.assert {
                NoBaseUrl.baseUrl
            }
        }
    }

    @Test
    fun dynamicUrl() {
        val dynamicUrl = httpClient.create<NoBaseUrl>()
        runBlocking {
            dynamicUrl.dynamic(NoBaseUrl.baseUrl).url.assert {
                NoBaseUrl.baseUrl
            }
        }
    }

    @Test
    fun urlInGet() {
        val dynamicUrl = httpClient.create<NoBaseUrl>()
        runBlocking {
            dynamicUrl.urlInGet().url.assert {
                LOCALHOST + "path"
            }
        }
    }

    @Test
    fun notFound() {
        assertThrows<ClientRequestException> {
            runBlocking {
                service.notFound()
            }
        }
    }

    @Test
    fun multiAnnotation() {
        runBlocking {
            service.multiAnnotation()
        }
    }

    @Test
    fun containerAnnotation() {
        runBlocking {
            service.containerAnnotation()
        }
    }

    @Test
    fun postWithTextBody() {
        runBlocking {
            service.postWithTextBody()
        }
    }

    @Test
    fun getWithEnum() {
        runBlocking {
            service.getWithEnum().url.assert {
                "https://localhost/?testEnum=sort"
            }
        }
    }

    @Test
    fun headersWithoutValue() {
        runBlocking {
            service.headersWithoutValue().header.assert {
                "Headers [Accept=[application/json], Accept-Charset=[UTF-8]]"
            }
        }
    }

    @Test
    fun iterableArgs() {
        runBlocking {
            service.queryParamWithArray().url.assert { "https://localhost/?args=1&args=2&args=3" }
            service.queryParamWithObjectArray().url.assert { "https://localhost/?args=1&args=2&args=3" }
            service.queryParamWithList().url.assert { "https://localhost/?args=1&args=2&args=3" }
            service.queryParamWithVarargs().url.assert { "https://localhost/?args=1&args=2&args=3" }
            service.queryParamWithEmpty().url.assert { "https://localhost/?args=" }
            service.fieldWithArray().body.assert { "args=1&args=2&args=3" }
            service.queryParamWithNullableArray().url.assert { "https://localhost/?args=1&args=&args=2" }
            service.queryParamWithEnumArray().url.assert { "https://localhost/?args=sort&args=long" }
        }
    }

    @Test
    fun returnWithHttpResponse() {
        runBlocking {
            service.returnWithHttpResponse().content.readUTF8Line()!!.let {
                Gson().fromJson<JsonObject>(it)
            }.body.assert {
                "\"HelloWorld\""
            }
        }
    }

    @Test
    fun returnWithHttpStatement() {
        val jsonObject = jsonObject("key" to "value")
        runBlocking {
            service.returnWithHttpStatement(jsonObject).receive<JsonObject>().body.assert { jsonObject.toString() }
        }
    }

    @Test
    fun programmaticBaseUrl() {
        runBlocking {
            createHttpClient().use {
                it.create<ProgrammaticBaseUrl>().get().url.assert { LOCALHOST }
            }
            val baseUrl = "https://test.com/"
            createHttpClient().use {
                it.create<ProgrammaticBaseUrl>(baseUrl).get().url.assert { baseUrl }
            }
        }
    }

    @AfterAll
    fun dispose() {
        httpClient.close()
    }
}
