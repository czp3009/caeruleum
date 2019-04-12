package com.hiczp.caeruleum

import io.ktor.client.HttpClient
import io.ktor.client.call.TypeInfo
import io.ktor.client.call.call
import io.ktor.client.request.url
import io.ktor.http.Url
import kotlinx.coroutines.*
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinFunction

private val jobType = Job::class.createType()
private val deferredType = Deferred::class.starProjectedType
private val anyType = Any::class.createType()

fun dynamicProxyToHttpClient(kClass: KClass<*>, httpClient: HttpClient): Any {
    //non-extension, non-static, abstract method
    val declaredAbstractMethods = kClass.declaredMemberFunctions
        .filter { it.isAbstract }
        .mapNotNull { it.javaMethod }
        .toHashSet()
    val javaClass = kClass.java
    return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(javaClass)) { proxy, method, args ->
        if (method !in declaredAbstractMethods) return@newProxyInstance method.invoke(proxy, args)

        val kFunction = method.kotlinFunction!!
        val returnType = kFunction.returnType
        val isJob = returnType.isSubtypeOf(jobType).also {
            if (!it) throw IllegalArgumentException("Functions in Service Interface must return $jobType")
        }
        val isSuspend = kFunction.isSuspend
        val realArgs = if (isSuspend) args.copyOf(args.size - 1) else args
        val coroutineContext = if (isSuspend) (args.last() as Continuation<*>).context else httpClient.coroutineContext
        val realReturnType = if (isJob) returnType.arguments[0].type ?: anyType else returnType
        val realReturnTypeInfo = TypeInfo(realReturnType.jvmErasure, realReturnType.javaType)

        if (returnType.isSubtypeOf(deferredType)) {
            CoroutineScope(coroutineContext).async {
                httpClient.call {
                    url(Url("https://api.github.com/"))
                }.receive(realReturnTypeInfo)
            }
        } else {
            CoroutineScope(coroutineContext).launch {
                httpClient.call {
                    url(Url("https://api.github.com/"))
                }
            }
        }
    }
}

inline fun <reified T> HttpClient.create() = dynamicProxyToHttpClient(T::class, this) as T
