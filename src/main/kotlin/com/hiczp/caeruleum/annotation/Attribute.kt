package com.hiczp.caeruleum.annotation

import kotlin.annotation.AnnotationTarget.FUNCTION

@MustBeDocumented
@Target(FUNCTION)
@Repeatable
annotation class Attribute(val key: String, val value: String = "")

//container of Attribute
@MustBeDocumented
@Target(FUNCTION)
@Repeatable
annotation class Attributes(vararg val value: Attribute)
