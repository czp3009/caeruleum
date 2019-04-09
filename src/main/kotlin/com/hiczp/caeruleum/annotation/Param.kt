package com.hiczp.caeruleum.annotation

import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Path(val value: String = "", val encoded: Boolean = false)

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Query(val value: String = "", val encoded: Boolean = false)

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Field(val value: String = "", val encoded: Boolean = false)

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Body

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Part(val value: String = "", val encoding: String = "binary")

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Url
