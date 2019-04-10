package com.hiczp.caeruleum.annotation

import kotlin.annotation.AnnotationTarget.CLASS

@MustBeDocumented
@Target(CLASS)
annotation class BaseUrl(val value: String = "")
