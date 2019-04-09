package com.hiczp.caeruleum.annotation

import kotlin.annotation.AnnotationTarget.FUNCTION

@MustBeDocumented
@Target(FUNCTION)
annotation class Get(val value: String = "")

@MustBeDocumented
@Target(FUNCTION)
annotation class Post(val value: String = "")

@MustBeDocumented
@Target(FUNCTION)
annotation class Put(val value: String = "")

@MustBeDocumented
@Target(FUNCTION)
annotation class Patch(val value: String = "")

@MustBeDocumented
@Target(FUNCTION)
annotation class Delete(val value: String = "")

@MustBeDocumented
@Target(FUNCTION)
annotation class Head(val value: String = "")

@MustBeDocumented
@Target(FUNCTION)
annotation class Options(val value: String = "")
