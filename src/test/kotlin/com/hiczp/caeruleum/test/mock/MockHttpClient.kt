package com.hiczp.caeruleum.test.mock

import com.hiczp.caeruleum.Caeruleum
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun createMockHttpClient() = HttpClient(MockEngine) {
    engine {
        addHandler {
            //encodedPath may not prefix with '/'
            when (Url(it.url.toString()).encodedPath) {
                "/echo" -> respond(
                    content = it.body.toByteArray(),
                    headers = it.body.contentType?.let { contentType ->
                        headersOf(HttpHeaders.ContentType, contentType.toString())
                    } ?: Headers.Empty
                )

                "/returnEncodedPath" -> respondOk(it.url.encodedPath)
                "/returnUrl" -> respondOk(it.url.toString())
                "/returnHost" -> respondOk(it.url.host)
                "/returnPort" -> respondOk(it.url.port.toString())
                "/returnHeadersAsString" -> respondOk(it.headers.toString())
                "/returnMethod" -> respondOk(it.method.value)
                "/returnQueryParametersAsString" -> respondOk(it.url.parameters.toString())

                //error response
                "/ok" -> respondOk()
                "/redirect" -> respondRedirect("/redirected")
                "/redirected" -> respondOk("redirected")
                "/badRequest" -> respondBadRequest()
                "/notFound" -> respondError(HttpStatusCode.NotFound)
                "/internalError" -> respondError(HttpStatusCode.InternalServerError)

                //other request
                else -> respond(
                    content = ByteReadChannel(
                            buildJsonObject {
                                put("header", it.headers.toString())
                                put("method", it.method.value)
                                put("url", it.url.toString())
                                put("contentLength", it.body.contentLength)
                                put("body", it.body.toByteReadPacket().readText())
                            }.toString()
                    ),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
    }

    install(ContentNegotiation) {
        json()
    }
    install(Logging) {
        level = LogLevel.ALL
    }
}

val mockHttpClient = createMockHttpClient()
val mockCaeruleum = Caeruleum()

inline fun <reified T : Any> createMockService() = mockCaeruleum.create<T>(mockHttpClient)
