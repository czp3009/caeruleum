package com.hiczp.caeruleum

import io.ktor.util.*
import java.lang.reflect.Array

@OptIn(InternalAPI::class)
internal fun StringValuesBuilder.appendIterableValue(name: String, value: Any) = when {
    value.javaClass.isArray -> {
        mutableListOf<String>().apply {
            for (i in 0 until Array.getLength(value)) {
                //platform type
                @Suppress("USELESS_CAST")
                add((Array.get(value, i) as Any?).parseEnum())
            }
        }
    }
    value is Iterable<*> -> value.map { it.parseEnum() }
    else -> listOf(value.parseEnum())
}.let {
    if (it.isNotEmpty()) {
        appendAll(name, it)
    }
}

@OptIn(InternalAPI::class)
internal fun StringValuesBuilder.appendMap(value: Any) {
    (value as Map<*, *>).forEach { (key, value) ->
        if (key != null && value != null) {
            append(key.toString(), value.toString())
        }
    }
}
