package com.hiczp.caeruleum.annotation

import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Url

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Path(val value: String = "")

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Header(val value: String)

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class HeaderMap

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Query(val value: String = "")

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class QueryMap

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Field(val value: String = "")

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class FieldMap

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Part(val value: String = "")

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class PartMap

/**
 * @param value ContentType
 */
@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Body(val value: String = "")
