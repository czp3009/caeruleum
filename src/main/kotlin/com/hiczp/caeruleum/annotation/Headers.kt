package com.hiczp.caeruleum.annotation

import kotlin.annotation.AnnotationTarget.FUNCTION

@MustBeDocumented
@Target(FUNCTION)
@Repeatable
annotation class Headers(val value: Array<String>)
