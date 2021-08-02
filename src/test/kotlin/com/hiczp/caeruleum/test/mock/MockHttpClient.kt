package com.hiczp.caeruleum.test.mock

import com.github.salomonbrys.kotson.jsonObject
import com.hiczp.caeruleum.Caeruleum
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.json.*
import io.ktor.client.features.logging.*
import io.ktor.http.*
import io.ktor.utils.io.*

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
                        jsonObject(
                            "header" to it.headers.toString(),
                            "method" to it.method.value,
                            "url" to it.url.toString(),
                            "contentLength" to it.body.contentLength,
                            "body" to it.body.toByteReadPacket().readText()
                        ).toString()
                    ),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
    }

    install(JsonFeature)
    install(Logging) {
        level = LogLevel.ALL
    }
}

val mockHttpClient = createMockHttpClient()
val mockCaeruleum = Caeruleum(httpClient = mockHttpClient)

inline fun <reified T : Any> createMockService() = mockCaeruleum.create<T>()
