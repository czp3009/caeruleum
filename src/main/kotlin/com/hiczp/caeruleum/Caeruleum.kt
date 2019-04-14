package com.hiczp.caeruleum

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

private val serviceFunctionCache = ConcurrentHashMap<Method, ServiceFunction>()

fun dynamicProxyToHttpClient(kClass: KClass<*>, httpClient: HttpClient): Any {
    //non-extension, non-static, abstract method
    val declaredAbstractMethods = kClass.declaredMemberFunctions
        .filter { it.isAbstract }
        .mapNotNull { it.javaMethod }
        .toHashSet()
    return Proxy.newProxyInstance(kClass.java.classLoader, arrayOf(kClass.java)) { proxy, method, args ->
        if (method !in declaredAbstractMethods) return@newProxyInstance method.invoke(proxy, args)

        val serviceFunction = serviceFunctionCache.getOrPut(method) { ServiceFunction(kClass, method.kotlinFunction!!) }
        val realArgs = if (serviceFunction.isSuspend) args.copyOf(args.size - 1) else args ?: emptyArray()
        val coroutineContext = if (serviceFunction.isSuspend) {
            (args.last() as Continuation<*>).context
        } else {
            httpClient.coroutineContext
        }

        CoroutineScope(coroutineContext).async {
            httpClient.execute(serviceFunction.httpRequestBuilder(realArgs))
                .receive(serviceFunction.returnTypeInfo)
        }
    }
}

inline fun <reified T> HttpClient.create() = dynamicProxyToHttpClient(T::class, this) as T
