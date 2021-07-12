package com.hiczp.caeruleum.test

import com.github.salomonbrys.kotson.jsonObject
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.json.*
import io.ktor.client.features.logging.*
import io.ktor.http.*
import io.ktor.utils.io.*

private val jsonResponseHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())

fun createHttpClient() = HttpClient(MockEngine) {
    engine {
        addHandler {
            when (it.url.encodedPath) {
                "/notFound" -> respondError(HttpStatusCode.NotFound)
                "/onlyReturnBody" -> respond(
                    content = it.body.toByteReadPacket().readText(),
                    headers = jsonResponseHeaders
                )
                else -> respond(
                    content = ByteReadChannel(
                        jsonObject(
                            "header" to it.headers.toString(),
                            "method" to it.method.value,
                            "url" to it.url.toString(),
                            "contentLength" to it.body.contentLength,
                            "body" to it.body.toByteReadPacket().readText()
                        ).toString()
                    ),
                    headers = jsonResponseHeaders
                )
            }
        }
    }

    install(JsonFeature)
    install(Logging) {
        level = LogLevel.ALL
    }
}
