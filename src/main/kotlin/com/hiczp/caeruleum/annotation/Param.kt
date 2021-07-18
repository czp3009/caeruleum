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

//Repeatable is not support, see below
//https://youtrack.jetbrains.com/issue/KT-12794?_ga=2.40144167.443754125.1569230332-295160856.1538112684
@MustBeDocumented
@Target(VALUE_PARAMETER)
@Repeatable
annotation class Query(vararg val value: String = [""])

//Container of Query
//@Deprecated("use @Query instead")
@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Queries(vararg val value: Query)

@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class QueryMap

@MustBeDocumented
@Target(VALUE_PARAMETER)
@Repeatable
annotation class Field(vararg val value: String = [""])

//Container of Field
//@Deprecated("use @Field instead")
@MustBeDocumented
@Target(VALUE_PARAMETER)
annotation class Fields(vararg val value: Field)

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
