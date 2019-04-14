package com.hiczp.caeruleum.test

import org.junit.jupiter.api.MethodDescriptor
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.MethodOrdererContext

class NatureOrder : MethodOrderer {
    override fun orderMethods(context: MethodOrdererContext) {
        val testClass = context.testClass
        @Suppress("UNCHECKED_CAST")
        (context.methodDescriptors as MutableList<MethodDescriptor>).sortWith(compareBy { testClass.methods.indexOf(it.method) })
    }
}
