package com.hiczp.caeruleum

import io.ktor.client.*
import io.ktor.client.statement.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.reflect.KClass
import kotlin.reflect.jvm.kotlinFunction

private typealias Invocation = (args: Array<out Any>?) -> Any

class CaeruleumProxy(
    private val httpClient: HttpClient,
    private val kClass: KClass<*>,
    private val jClass: Class<*> = kClass.java,
    private val programmaticBaseUrl: String? = null
) : InvocationHandler {
    private val cachedInvocations = ConcurrentHashMap<Method, Invocation>()

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {
        //check context available
        if (!httpClient.isActive) throw CancellationException("Parent context in HttpClient is cancelled")
        //use cache
        return cachedInvocations.getOrPut(method) { generateInvocation(proxy, method) }(args)
    }

    private fun generateInvocation(proxy: Any, method: Method): Invocation {
        val kFunction = method.kotlinFunction
        return when {
            //if isSynthetic
            kFunction == null -> { args -> method(proxy, *args.orEmpty()) }
            //method in Object
            method.declaringClass == Any::class.java -> when (method.name) {
                "equals" -> { args ->
                    when {
                        args == null -> false
                        args[0] === proxy -> true
                        args[0] !is Proxy -> false
                        else -> args[0].javaClass.interfaces.let {
                            if (it.size != 1) false else it[0] == jClass
                        }
                    }
                }
                "hashCode" -> { _ -> kClass.qualifiedName.hashCode() }
                "toString" -> { _ -> "Service interface ${kClass.qualifiedName}" }
                else -> { _ -> }   //Impossible
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
                    }.newInstance(jClass, -1)
                        .unreflectSpecial(method, jClass)
                        .bindTo(proxy)
                        .let { { args -> it.invokeWithArguments(*args.orEmpty()) } }
                } else { //non-abstract kotlin function
                    javaClass.declaredClasses.find {
                        it.simpleName == "DefaultImpls"
                    }!!.getDeclaredMethod(
                        method.name,
                        jClass,
                        *method.parameterTypes
                    ).let { { args -> it(null, proxy, *args.orEmpty()) } }
                }
            }
            //service function
            else -> {
                val serviceFunction = ServiceFunction(kClass, kFunction)
                if (serviceFunction.isSuspend) {
                    { args ->
                        @Suppress("UNCHECKED_CAST")
                        val continuation = args!!.last() as Continuation<Any>
                        val realArgs = args.copyOfRange(0, args.size - 1)
                        httpClient.launch {
                            runCatching {
                                HttpStatement(
                                    serviceFunction.httpRequestBuilder(programmaticBaseUrl, realArgs),
                                    httpClient
                                ).executeAndReceive(serviceFunction.returnTypeInfo)
                            }.run(continuation::resumeWith)
                        }
                        COROUTINE_SUSPENDED
                    }
                } else {
                    { args ->
                        httpClient.async {
                            HttpStatement(
                                serviceFunction.httpRequestBuilder(programmaticBaseUrl, args.orEmpty()),
                                httpClient
                            ).executeAndReceive(serviceFunction.returnTypeInfo)
                        }
                    }
                }
            }
        }
    }

    private suspend inline fun HttpStatement.executeAndReceive(returnTypeInfo: TypeInfo) = when (returnTypeInfo.type) {
        HttpStatement::class -> this
        HttpResponse::class -> execute()
        else -> execute {
            it.call.receive(returnTypeInfo)
        }
    }
}
