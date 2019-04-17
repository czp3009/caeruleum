package com.hiczp.caeruleum.test

import com.github.salomonbrys.kotson.nullInt
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement

inline fun <reified T> T.assert(block: T.() -> T) {
    val expect = block()
    if (block() != this) {
        throw AssertionError("Expect $expect but get $this")
    }
}

val JsonElement.url get() = obj["url"].string

val JsonElement.method get() = obj["method"].string

val JsonElement.contentLength get() = obj["contentLength"].nullInt
