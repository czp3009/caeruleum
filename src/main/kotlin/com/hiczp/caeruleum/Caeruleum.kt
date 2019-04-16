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

@PublishedApi
internal fun dynamicProxyToHttpClient(kClass: KClass<*>, httpClient: HttpClient): Any {
    val javaClass = kClass.java
    if (!javaClass.isInterface) throw IllegalArgumentException("API declarations must be interfaces")
    if (javaClass.interfaces.isNotEmpty()) throw IllegalArgumentException("API interfaces must not extend other interfaces")

    //non-extension, non-static, abstract method
    val declaredAbstractMethods = kClass.declaredMemberFunctions
        .filter { it.isAbstract }
        .mapNotNull { it.javaMethod }
        .toHashSet()

    return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(javaClass)) { proxy, method, args ->
        //if isSynthetic
        val kFunction = method.kotlinFunction ?: return@newProxyInstance method.invoke(proxy, *args.orEmpty())

        //method in Object
        if (method.declaringClass == Any::class.java) {
            return@newProxyInstance when (method.name) {
                "equals" -> false
                "hashCode" -> kClass.qualifiedName.hashCode()
                "toString" -> "Service interface ${kClass.qualifiedName}"
                else -> null    //Impossible
            }
        }

        //TODO
        //non-abstract
        if (!kFunction.isAbstract) {
            return@newProxyInstance method.invoke(proxy, *args.orEmpty())
        }

        val serviceFunction = serviceFunctionCache.getOrPut(method) { ServiceFunction(kClass, kFunction) }
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
