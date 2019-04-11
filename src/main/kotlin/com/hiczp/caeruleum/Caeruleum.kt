package com.hiczp.caeruleum

import io.ktor.client.HttpClient
import io.ktor.client.call.TypeInfo
import io.ktor.client.call.call
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinFunction

@PublishedApi
internal val cache = HashMap<KClass<*>, Any>()

inline fun <reified T> HttpClient.create(): T {
    val declaredMethods = T::class.declaredMemberFunctions
        .mapNotNull { it.javaMethod }
        .filter { !it.isDefault }
        .toHashSet()
    return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
        if (method !in declaredMethods) return@newProxyInstance method.invoke(proxy, args)

        val kFunction = method.kotlinFunction!!
        val returnType = kFunction.returnType
        if (kFunction.isSuspend) {
            val realArgs = args.copyOf(args.size - 1)
            val continuation = args.last() as Continuation<*>
            return@newProxyInstance runBlocking(continuation.context) {
                call("https://api.github.com/").receive(TypeInfo(returnType.jvmErasure, returnType.javaType))
            }
        }

        //TODO
        "hello"
    } as T
}
