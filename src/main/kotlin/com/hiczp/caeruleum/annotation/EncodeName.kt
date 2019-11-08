package com.hiczp.caeruleum.annotation

/**
 * For enum
 */
@MustBeDocumented
@Target(AnnotationTarget.FIELD)
annotation class EncodeName(val value: String)
