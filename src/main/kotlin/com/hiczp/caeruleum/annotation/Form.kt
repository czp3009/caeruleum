package com.hiczp.caeruleum.annotation

import kotlin.annotation.AnnotationTarget.FUNCTION

@MustBeDocumented
@Target(FUNCTION)
annotation class FormUrlEncoded

@MustBeDocumented
@Target(FUNCTION)
annotation class Multipart
