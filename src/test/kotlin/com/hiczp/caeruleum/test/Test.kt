package com.hiczp.caeruleum.test

import com.google.gson.JsonElement
import com.hiczp.caeruleum.annotation.BaseUrl
import com.hiczp.caeruleum.annotation.Get
import com.hiczp.caeruleum.annotation.Path
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking

@BaseUrl("https://api.github.com/")
interface GithubAPI {
    @Get("users/{user}/repos")
    fun listRepos(@Path user: String): JsonElement
}

@UseExperimental(KtorExperimentalAPI::class)
fun main() {
    runBlocking {
        HttpClient(CIO) {
            install(JsonFeature) {
                serializer = GsonSerializer()
            }
        }.get<JsonElement>("https://api.github.com").println()
    }
}

private fun Any.println() = println(toString())
