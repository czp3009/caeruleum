package com.hiczp.caeruleum.test

import com.google.gson.JsonElement
import com.hiczp.caeruleum.annotation.BaseUrl
import com.hiczp.caeruleum.annotation.Get
import com.hiczp.caeruleum.annotation.Path
import com.hiczp.caeruleum.create
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking

@BaseUrl("https://api.github.com/")
interface GitHubService {
    @Get("users/{user}/repos")
    fun listReposAsync(@Path user: String): Deferred<JsonElement>

    @Get("users/{user}/repos")
    suspend fun listRepos(@Path user: String): JsonElement
}

val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        gson()
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
