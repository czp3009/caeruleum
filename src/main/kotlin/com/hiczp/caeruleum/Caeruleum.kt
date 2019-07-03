package com.hiczp.caeruleum

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.lang.reflect.Proxy.newProxyInstance
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KClass
import kotlin.reflect.jvm.kotlinFunction

private val cache = ConcurrentHashMap<Method, (HttpClient, Array<Any?>?) -> Any>()

@PublishedApi
internal fun dynamicProxyToHttpClient(kClass: KClass<*>, httpClient: HttpClient): Any {
    val javaClass = kClass.java
    if (!javaClass.isInterface) throw IllegalArgumentException("API declarations must be interfaces")
    if (javaClass.interfaces.isNotEmpty()) throw IllegalArgumentException("API interfaces must not extend other interfaces")

    return newProxyInstance(javaClass.classLoader, arrayOf(javaClass)) { proxy, method, args ->
        if (!httpClient.isActive) throw CancellationException("Parent context in HttpClient is cancelled")

        cache.getOrPut(method) {
            val kFunction = method.kotlinFunction
            when {
                //if isSynthetic
                kFunction == null -> { _, args -> method.invoke(proxy, *args.orEmpty()) }

                //method in Object
                method.declaringClass == Any::class.java -> when (method.name) {
                    "equals" -> { _, args ->
                        when {
                            args == null -> false
                            args[0] === proxy -> true
                            args[0] !is Proxy -> false
                            else -> args[0]!!.javaClass.interfaces.let {
                                if (it.size != 1) false else it[0] == javaClass
                            }
                        }
                    }
                    "hashCode" -> { _, _ -> kClass.qualifiedName.hashCode() }
                    "toString" -> { _, _ -> "Service interface ${kClass.qualifiedName}" }
                    else -> { _, _ -> Unit }  //Impossible
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

                        fun(_, args) = methodHandles.newInstance(javaClass, -1)
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

                        fun(_, args) = realMethod.invoke(null, proxy, *args.orEmpty())
                    }
                }

                else -> {
                    val serviceFunction = ServiceFunction(kClass, kFunction)
                    if (serviceFunction.isSuspend) {
                        { httpClient, args ->
                            @Suppress("UNCHECKED_CAST")
                            val continuation = args!!.last() as Continuation<Any>
                            val realArgs = args.copyOf(args.size - 1)
                            httpClient.launch {
                                val result = runCatching {
                                    httpClient.execute(serviceFunction.httpRequestBuilder(realArgs))
                                        .receive(serviceFunction.returnTypeInfo)
                                }
                                continuation.resumeWith(result)
                            }.invokeOnCompletion { if (it != null) continuation.resumeWithException(it) }
                            COROUTINE_SUSPENDED
                        }
                    } else {
                        { httpClient, args ->
                            httpClient.async {
                                httpClient.execute(serviceFunction.httpRequestBuilder(args ?: emptyArray()))
                                    .receive(serviceFunction.returnTypeInfo)
                            }
                        }
                    }
                }
            }
        }(httpClient, args)
    }
}

inline fun <reified T> HttpClient.create() = dynamicProxyToHttpClient(T::class, this) as T
