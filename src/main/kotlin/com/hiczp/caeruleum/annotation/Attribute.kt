package com.hiczp.caeruleum.annotation

import kotlin.annotation.AnnotationTarget.FUNCTION

@MustBeDocumented
@Target(FUNCTION)
@Repeatable
annotation class Attribute(val key: String, val value: String = "")

//Container of Attribute
@Deprecated("Kotlin support repeatable annotation since 1.6, please use @Attribute instead")
@MustBeDocumented
@Target(FUNCTION)
@Repeatable
annotation class Attributes(vararg val value: Attribute)
