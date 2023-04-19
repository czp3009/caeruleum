package com.hiczp.caeruleum.annotation

import kotlin.annotation.AnnotationTarget.FUNCTION

@MustBeDocumented
@Target(FUNCTION)
@Repeatable
annotation class Attribute(val key: String, val value: String = "")
