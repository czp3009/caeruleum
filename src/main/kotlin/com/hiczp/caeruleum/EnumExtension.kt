package com.hiczp.caeruleum

import com.hiczp.caeruleum.annotation.EncodeName

internal fun Any.toStringOrEnumName() = if (this is Enum<*>) {
    javaClass.getField(name).getAnnotation(EncodeName::class.java)?.value ?: name
} else {
    toString()
}
