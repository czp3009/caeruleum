package com.hiczp.caeruleum

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy.newProxyInstance
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KClass
import kotlin.reflect.jvm.kotlinFunction

private typealias Proxy = Any

private val cache = ConcurrentHashMap<Method, (Proxy, HttpClient, String?, Array<Any?>?) -> Any>()

@PublishedApi
internal fun dynamicProxyToHttpClient(kClass: KClass<*>, httpClient: HttpClient, baseUrl: String?): Any {
    val javaClass = kClass.java
    if (!javaClass.isInterface) throw IllegalArgumentException("API declarations must be interfaces")
    if (javaClass.interfaces.isNotEmpty()) throw IllegalArgumentException("API interfaces must not extend other interfaces")

    return newProxyInstance(javaClass.classLoader, arrayOf(javaClass)) { proxy, method, args ->
        if (!httpClient.isActive) throw CancellationException("Parent context in HttpClient is cancelled")

        cache.getOrPut(method) {
            val kFunction = method.kotlinFunction
            when {
                //if isSynthetic
                kFunction == null -> { proxy, _, _, args -> method.invoke(proxy, *args.orEmpty()) }

                //method in Object
                method.declaringClass == Any::class.java -> when (method.name) {
                    "equals" -> { proxy, _, _, args ->
                        when {
                            args == null -> false
                            args[0] === proxy -> true
                            args[0] !is java.lang.reflect.Proxy -> false
                            else -> args[0]!!.javaClass.interfaces.let {
                                if (it.size != 1) false else it[0] == javaClass
                            }
                        }
                    }
                    "hashCode" -> { _, _, _, _ -> kClass.qualifiedName.hashCode() }
                    "toString" -> { _, _, _, _ -> "Service interface ${kClass.qualifiedName}" }
                    else -> { _, _, _, _ -> Unit }  //Impossible
                }

                //non-abstract method
                !kFunction.isAbstract -> {
                    //default method
                    if (!Modifier.isAbstract(method.modifiers)) {
                        // Because the service interface might not be public, we need to use a MethodHandle lookup
                        // that ignores the visibility of the declaringClass
                        val methodHandles = MethodHandles.Lookup::class.java.getDeclaredConstructor(
                            Class::class.java,
                            Int::class.javaPrimitiveType
                        ).apply {
                            setAccessible(true)
                        }

                        fun(proxy, _, _, args) = methodHandles.newInstance(javaClass, -1)
                            .unreflectSpecial(method, javaClass)
                            .bindTo(proxy)
                            .invokeWithArguments(*args.orEmpty())
                    } else { //non-abstract kotlin function
                        val realMethod = javaClass.declaredClasses.find {
                            it.simpleName == "DefaultImpls"
                        }!!.getDeclaredMethod(
                            method.name,
                            javaClass,
                            *method.parameterTypes
                        )

                        fun(proxy, _, _, args) = realMethod.invoke(null, proxy, *args.orEmpty())
                    }
                }

                else -> {
                    val serviceFunction = ServiceFunction(kClass, kFunction)
                    if (serviceFunction.isSuspend) {
                        { _, httpClient, baseUrl, args ->
                            @Suppress("UNCHECKED_CAST")
                            val continuation = args!!.last() as Continuation<Any>
                            val realArgs = args.copyOf(args.size - 1)
                            httpClient.launch {
                                val result = runCatching {
                                    httpClient.execute(serviceFunction.httpRequestBuilder(baseUrl, realArgs))
                                        .receive(serviceFunction.returnTypeInfo)
                                }
                                continuation.resumeWith(result)
                            }.invokeOnCompletion { if (it != null) continuation.resumeWithException(it) }
                            COROUTINE_SUSPENDED
                        }
                    } else {
                        { _, httpClient, baseUrl, args ->
                            httpClient.async {
                                httpClient.execute(serviceFunction.httpRequestBuilder(baseUrl, args ?: emptyArray()))
                                    .receive(serviceFunction.returnTypeInfo)
                            }
                        }
                    }
                }
            }
        }(proxy, httpClient, baseUrl, args)
    }
}

inline fun <reified T> HttpClient.create(baseUrl: String? = null) =
    dynamicProxyToHttpClient(T::class, this, baseUrl) as T
