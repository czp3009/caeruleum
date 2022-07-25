package com.hiczp.caeruleum.test.sample

import com.google.gson.JsonElement
import com.hiczp.caeruleum.Caeruleum
import com.hiczp.caeruleum.annotation.BaseUrl
import com.hiczp.caeruleum.annotation.Get
import com.hiczp.caeruleum.annotation.Path
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.gson.*
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
        install(ContentNegotiation) {
            gson()
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }
    //get implement of interface
    val githubService = Caeruleum().create<GitHubService>(httpClient)
    runBlocking {
        //send http request
        githubService.listRepos("czp3009").run(::println)
    }
    //cleanup
    httpClient.close()
}
