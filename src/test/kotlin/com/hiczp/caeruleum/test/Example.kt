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
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logging
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking

@BaseUrl("https://api.github.com/")
interface GitHubService {
    @Get("users/{user}/repos")
    fun listReposAsync(@Path user: String): Deferred<JsonElement>

    @Get("users/{user}/repos")
    suspend fun listRepos(@Path user: String): JsonElement
}

@UseExperimental(KtorExperimentalAPI::class)
val httpClient = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
    install(Logging) {
        level = LogLevel.ALL
    }
}

fun main() {
    val githubService = httpClient.create<GitHubService>()
    runBlocking {
        githubService.listReposAsync("czp3009").await().run(::println)
        githubService.listRepos("czp3009").run(::println)
    }
}
