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
interface GitHubService {
    @Get("users/{user}/repos")
    fun listRepos(@Path user: String): Deferred<JsonElement>
}

@UseExperimental(KtorExperimentalAPI::class)
val httpClient = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.ALL
    }
}

val githubService = httpClient.create<GitHubService>()

fun main() {
    runBlocking {
        githubService.listRepos("czp3009").await().run(::println)
    }
}
