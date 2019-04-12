package com.hiczp.caeruleum.test

import com.google.gson.JsonElement
import com.hiczp.caeruleum.annotation.BaseUrl
import com.hiczp.caeruleum.annotation.Get
import com.hiczp.caeruleum.annotation.Path
import com.hiczp.caeruleum.create
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking

@Suppress("DeferredIsResult")
@BaseUrl("https://api.github.com/")
interface GithubService {
    @Get
    suspend fun noReturnValue()

    @Get
    suspend fun withReturnValue(): Deferred<JsonElement>

    @Get("users/{user}/repos")
    fun withPathVariable(@Path user: String): Deferred<JsonElement>

    fun nonAbstract() = "hello"

    @JvmDefault
    fun jvmDefault() = "hello"
}

@UseExperimental(KtorExperimentalAPI::class)
fun main() {
    val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }
//    runBlocking {
//        httpClient.get<List<*>>("https://api.github.com/users/czp3009/repos").println()
//    }
    val githubAPI = httpClient.create<GithubService>()

    runBlocking {
        //githubAPI.index().println()
        githubAPI.withPathVariable("czp3009").await().println()
    }

    httpClient.close()
}

private fun Any.println() = println(toString())
