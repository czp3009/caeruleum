package com.hiczp.caeruleum.annotation

/**
 * For enum value
 */
@MustBeDocumented
@Target(AnnotationTarget.FIELD)
annotation class EncodeName(val value: String)
