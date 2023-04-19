package com.hiczp.caeruleum.test.sample

import com.hiczp.caeruleum.Caeruleum
import com.hiczp.caeruleum.annotation.BaseUrl
import com.hiczp.caeruleum.annotation.Get
import com.hiczp.caeruleum.annotation.Path
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement

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
            json()
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }
    //get implementation of interface
    val githubService = Caeruleum().create<GitHubService>(httpClient)
    runBlocking {
        //send http request
        githubService.listRepos("czp3009").run(::println)
    }
    //cleanup
    httpClient.close()
}
