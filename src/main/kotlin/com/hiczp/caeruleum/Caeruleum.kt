package com.hiczp.caeruleum

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.invoke.MethodHandles
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy.newProxyInstance
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.reflect.KClass
import kotlin.reflect.jvm.kotlinFunction

@PublishedApi
internal fun dynamicProxyToHttpClient(kClass: KClass<*>, httpClient: HttpClient, baseUrl: String?): Any {
    val javaClass = kClass.java
    require(javaClass.isInterface) { "API declarations must be interfaces" }
    require(javaClass.interfaces.isEmpty()) { "API interfaces must not extend other interfaces" }

    return newProxyInstance(javaClass.classLoader, arrayOf(javaClass)) { proxy, method, args ->
        if (!httpClient.isActive) throw CancellationException("Parent context in HttpClient is cancelled")

        val kFunction = method.kotlinFunction
        when {
            //if isSynthetic
            kFunction == null -> method(proxy, *args.orEmpty())

            //method in Object
            method.declaringClass == Any::class.java -> when (method.name) {
                "equals" -> when {
                    args == null -> false
                    args[0] === proxy -> true
                    args[0] !is java.lang.reflect.Proxy -> false
                    else -> args[0]!!.javaClass.interfaces.let {
                        if (it.size != 1) false else it[0] == javaClass
                    }
                }
                "hashCode" -> kClass.qualifiedName.hashCode()
                "toString" -> "Service interface ${kClass.qualifiedName}"
                else -> Unit   //Impossible
            }

            //non-abstract method
            !kFunction.isAbstract -> {
                //default method
                if (!Modifier.isAbstract(method.modifiers)) {
                    // Because the service interface might not be public, we need to use a MethodHandle lookup
                    // that ignores the visibility of the declaringClass
                    MethodHandles.Lookup::class.java.getDeclaredConstructor(
                        Class::class.java,
                        Int::class.javaPrimitiveType
                    ).apply {
                        setAccessible(true)
                    }.newInstance(javaClass, -1)
                        .unreflectSpecial(method, javaClass)
                        .bindTo(proxy)
                        .invokeWithArguments(*args.orEmpty())
                } else { //non-abstract kotlin function
                    javaClass.declaredClasses.find {
                        it.simpleName == "DefaultImpls"
                    }!!.getDeclaredMethod(
                        method.name,
                        javaClass,
                        *method.parameterTypes
                    )(null, proxy, *args.orEmpty())
                }
            }

            else -> {
                val serviceFunction = ServiceFunction(kClass, kFunction)
                if (serviceFunction.isSuspend) {
                    @Suppress("UNCHECKED_CAST")
                    val continuation = args!!.last() as Continuation<Any>
                    val realArgs = args.copyOf(args.size - 1)
                    httpClient.launch {
                        runCatching {
                            httpClient.execute(serviceFunction.httpRequestBuilder(baseUrl, realArgs))
                                .receive(serviceFunction.returnTypeInfo)
                        }.run(continuation::resumeWith)
                    }
                    COROUTINE_SUSPENDED
                } else {
                    httpClient.async {
                        httpClient.execute(serviceFunction.httpRequestBuilder(baseUrl, args.orEmpty()))
                            .receive(serviceFunction.returnTypeInfo)
                    }
                }
            }
        }
    }
}

inline fun <reified T> HttpClient.create(baseUrl: String? = null) =
    dynamicProxyToHttpClient(T::class, this, baseUrl) as T
