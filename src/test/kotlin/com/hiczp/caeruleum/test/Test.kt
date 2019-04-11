package com.hiczp.caeruleum.test

import com.google.gson.JsonElement
import com.hiczp.caeruleum.annotation.BaseUrl
import com.hiczp.caeruleum.annotation.Get
import com.hiczp.caeruleum.create
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.get
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking

@Suppress("DeferredIsResult")
@BaseUrl("https://api.github.com/")
interface GithubAPI {
    @Get
    suspend fun index(): JsonElement

    //@Get("users/{user}/repos")
    //fun listRepos(@Path user: String): Deferred<JsonElement>
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
    runBlocking { httpClient.get<Map<String, JsonElement>>("https://api.github.com/") }
    val githubAPI = httpClient.create<GithubAPI>()

    runBlocking {
        githubAPI.index().println()
        //githubAPI.listRepos("czp3009").await().println()
    }

    httpClient.close()
}

private fun Any.println() = println(toString())
