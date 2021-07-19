package com.hiczp.caeruleum.test.sample

import com.google.gson.JsonElement
import com.hiczp.caeruleum.Caeruleum
import com.hiczp.caeruleum.annotation.BaseUrl
import com.hiczp.caeruleum.annotation.Get
import com.hiczp.caeruleum.annotation.Path
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.logging.*
import kotlinx.coroutines.runBlocking

//define HTTP API in interface with annotation
@BaseUrl("https://api.github.com")
interface GitHubService {
    @Get("users/{user}/repos")
    suspend fun listRepos(@Path user: String): JsonElement
}

fun main() {
    //create closeable HttpClient
    val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }
    //get implement of interface
    val githubService = Caeruleum(httpClient).create<GitHubService>()
    runBlocking {
        //send http request
        githubService.listRepos("czp3009").run(::println)
    }
    //cleanup
    httpClient.close()
}
