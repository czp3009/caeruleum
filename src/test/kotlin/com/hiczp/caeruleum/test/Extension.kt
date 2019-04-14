package com.hiczp.caeruleum.test

import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement

fun Any.println() = println(toString())

inline fun <reified T> T.assert(block: T.() -> Boolean) = assert(this.block())

inline fun <reified T> T.assert(block: T.() -> Boolean, lazyMessage: () -> Any) = assert(this.block(), lazyMessage)

val JsonElement.url get() = obj["url"].string
