package com.hiczp.caeruleum

import io.ktor.client.HttpClient
import kotlin.reflect.KFunction

inline fun <reified T> HttpClient.execute(funciton: KFunction<T>): Nothing = throw UnsupportedOperationException()
