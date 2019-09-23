package com.hiczp.caeruleum.annotation

//default Content-Type of request
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
annotation class DefaultContentType(val value: String)
