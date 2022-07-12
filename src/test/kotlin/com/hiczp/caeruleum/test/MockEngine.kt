package com.hiczp.caeruleum.test

import com.github.salomonbrys.kotson.jsonObject
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.utils.io.*

fun createHttpClient() = HttpClient(MockEngine) {
    expectSuccess = true
    engine {
        addHandler {
            when (it.url.encodedPath) {
                "/notFound" -> respondError(HttpStatusCode.NotFound)
                else -> respond(
                    ByteReadChannel(
                        jsonObject(
                            "header" to it.headers.toString(),
                            "method" to it.method.value,
                            "url" to it.url.toString(),
                            "contentLength" to it.body.contentLength,
                            "body" to it.body.toByteReadPacket().readText()
                        ).toString()
                    ),
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                )
            }
        }
    }

    install(ContentNegotiation) {
        gson()
    }
    install(Logging) {
        level = LogLevel.ALL
    }
}
