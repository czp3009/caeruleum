package com.hiczp.caeruleum

import io.ktor.util.*
import java.lang.reflect.Array

@OptIn(InternalAPI::class)
internal fun StringValuesBuilder.appendValueAsStringList(name: String, value: Any) {
    val values = when {
        value.javaClass.isArray -> (0 until Array.getLength(value)).mapNotNull { index ->
            Array.get(value, index)?.toStringOrEnumName()
        }
        value is Iterable<*> -> value.mapNotNull { it?.toStringOrEnumName() }
        else -> listOf(value.toStringOrEnumName())
    }
    if (values.isNotEmpty()) {
        appendAll(name, values)
    }
}

@OptIn(InternalAPI::class)
internal fun StringValuesBuilder.appendMap(value: Map<*, *>) {
    value.forEach { (key, value) ->
        if (key != null && value != null) {
            append(key.toStringOrEnumName(), value.toStringOrEnumName())
        }
    }
}
