package com.hiczp.caeruleum

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.reflect.KClass
import kotlin.reflect.jvm.kotlinFunction


private val serviceFunctionCache = ConcurrentHashMap<Method, ServiceFunction>()

@PublishedApi
internal fun dynamicProxyToHttpClient(kClass: KClass<*>, httpClient: HttpClient): Any {
    val javaClass = kClass.java
    if (!javaClass.isInterface) throw IllegalArgumentException("API declarations must be interfaces")
    if (javaClass.interfaces.isNotEmpty()) throw IllegalArgumentException("API interfaces must not extend other interfaces")

    return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(javaClass)) { proxy, method, args ->
        //if isSynthetic
        val kFunction = method.kotlinFunction ?: return@newProxyInstance method.invoke(proxy, *args.orEmpty())

        //non-abstract method
        if (!kFunction.isAbstract) {
            //default method
            return@newProxyInstance if (method.modifiers and (Modifier.ABSTRACT or Modifier.PUBLIC or Modifier.STATIC) == Modifier.PUBLIC) {
                // Because the service interface might not be public, we need to use a MethodHandle lookup
                // that ignores the visibility of the declaringClass.
                MethodHandles.Lookup::class.java.getDeclaredConstructor(
                    Class::class.java,
                    Int::class.javaPrimitiveType
                ).apply {
                    setAccessible(true)
                }.newInstance(javaClass, -1)
                    .unreflectSpecial(method, javaClass)
                    .bindTo(proxy)
                    .invokeWithArguments(*args.orEmpty())
            } else {    //non-abstract kotlin function in interface
                val types = args.map { it::class.java }.let {
                    if (kFunction.isSuspend) {
                        it.toMutableList().apply {
                            this[size - 1] = Continuation::class.java
                        }
                    } else {
                        it
                    }
                }.toTypedArray()
                javaClass.declaredClasses.find {
                    it.simpleName == "DefaultImpls"
                }!!.getDeclaredMethod(
                    method.name,
                    javaClass,
                    *types
                ).invoke(null, proxy, *args.orEmpty())
            }
        }

        //method in Object
        if (method.declaringClass == Any::class.java) {
            return@newProxyInstance when (method.name) {
                "equals" -> when {
                    args == null -> false
                    args[0] === proxy -> true
                    args[0] !is Proxy -> false
                    else -> args[0].javaClass.interfaces.let {
                        if (it.size != 1) false else it[0] == javaClass
                    }
                }
                "hashCode" -> kClass.qualifiedName.hashCode()
                "toString" -> "Service interface ${kClass.qualifiedName}"
                else -> null    //Impossible
            }
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
