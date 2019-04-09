package com.hiczp.caeruleum.test

import com.hiczp.caeruleum.annotation.Get
import com.hiczp.caeruleum.annotation.Path
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking

interface GithubAPI {
    @Get("users/{user}/repos")
    fun listRepos(@Path user: String)
}

@UseExperimental(KtorExperimentalAPI::class)
fun main() {
    runBlocking {
        HttpClient(CIO).get<String>("https://api.github.com").println()
    }
}

private fun Any.println() = println(toString())
