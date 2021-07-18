@file:Suppress("UNCHECKED_CAST")

package com.hiczp.caeruleum

import io.ktor.client.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

internal sealed interface ServiceFunction : InvocationHandler {
    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any
}

internal object DoNothingServiceFunction : ServiceFunction {
    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any = Unit
}

internal object SyntheticServiceFunction : ServiceFunction {
    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any = method(proxy, *args.orEmpty())
}

internal open class ConstantServiceFunction(private val returnValue: Any) : ServiceFunction {
    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?) = returnValue
}

internal sealed class ObjectDeclaredServiceFunction(protected val serviceInterface: Class<*>) : ServiceFunction {
    internal class Equals(serviceInterface: Class<*>) : ObjectDeclaredServiceFunction(serviceInterface) {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any {
            val other = args?.getOrNull(0) ?: return false
            if (other === proxy) return true
            if (other !is java.lang.reflect.Proxy) return false
            val otherInterfaces = other.javaClass.interfaces
            return if (otherInterfaces.size != 1) false else otherInterfaces[0] == serviceInterface
        }
    }

    internal class HashCode(serviceInterface: Class<*>) : ConstantServiceFunction(serviceInterface.hashCode())

    internal class ToString(serviceInterface: Class<*>) :
        ConstantServiceFunction("Service interface ${serviceInterface.name}")
}

internal sealed interface NonAbstractServiceFunction : ServiceFunction {
    class DefaultMethod(serviceInterface: Class<*>, targetMethod: Method) : NonAbstractServiceFunction {
        private val methodHandler = lookUpConstructor.newInstance(serviceInterface, -1)
            .unreflectSpecial(targetMethod, serviceInterface)

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any =
            methodHandler.bindTo(proxy).invokeWithArguments(*args.orEmpty())

        companion object {
            private val lookUpConstructor = MethodHandles.Lookup::class.java
                .getDeclaredConstructor(Class::class.java, Int::class.javaPrimitiveType)
                .apply { setAccessible(true) }
        }
    }

    class KotlinDefaultImpls(
        serviceInterface: Class<*>,
        targetMethod: Method
    ) : NonAbstractServiceFunction {
        private val defaultImpl = serviceInterface.declaredClasses
            .find { it.simpleName == "DefaultImpls" }!!
            .getDeclaredMethod(targetMethod.name, serviceInterface, *targetMethod.parameterTypes)

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any =
            defaultImpl(null, proxy, *args.orEmpty())
    }
}

internal sealed class HttpServiceFunction(
    private val httpServiceFunctionParseResult: HttpServiceFunctionParseResult,
    protected val httpClient: HttpClient,
    private val baseUrl: String?
) : ServiceFunction {
    private val httpStatementExecutor: suspend (HttpStatement) -> Any =
        when (httpServiceFunctionParseResult.realReturnTypeInfo.type) {
            HttpStatement::class -> { it -> it }
            HttpResponse::class -> { it -> it.execute() }
            else -> { it -> it.execute { it.call.receive(httpServiceFunctionParseResult.realReturnTypeInfo) } }
        }

    private fun generateHttpStatement(args: Array<out Any?>?) =
        HttpStatement(httpServiceFunctionParseResult.generateHttpRequestBuilder(baseUrl, args.orEmpty()), httpClient)

    protected suspend inline fun execute(args: Array<out Any?>?) = httpStatementExecutor(generateHttpStatement(args))

    internal class Blocking(
        httpServiceFunctionParseResult: HttpServiceFunctionParseResult,
        httpClient: HttpClient,
        baseUrl: String?
    ) : HttpServiceFunction(httpServiceFunctionParseResult, httpClient, baseUrl) {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?) = runBlocking {
            execute(args)
        }
    }

    internal class Suspend(
        httpServiceFunctionParseResult: HttpServiceFunctionParseResult,
        httpClient: HttpClient,
        baseUrl: String?
    ) : HttpServiceFunction(httpServiceFunctionParseResult, httpClient, baseUrl) {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any {
            val (realArgs, continuation) = args!!.parseSuspendFunArgs()
            CoroutineScope(continuation.context).launch {
                runCatching { execute(realArgs) }.run(continuation::resumeWith)
            }
            return COROUTINE_SUSPENDED
        }
    }

    internal class Job(
        httpServiceFunctionParseResult: HttpServiceFunctionParseResult,
        httpClient: HttpClient,
        baseUrl: String?
    ) : HttpServiceFunction(httpServiceFunctionParseResult, httpClient, baseUrl) {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any {
            return httpClient.async { execute(args) }
        }
    }

    internal class SuspendAndJob(
        httpServiceFunctionParseResult: HttpServiceFunctionParseResult,
        httpClient: HttpClient,
        baseUrl: String?
    ) : HttpServiceFunction(httpServiceFunctionParseResult, httpClient, baseUrl) {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any {
            val (realArgs, continuation) = args!!.parseSuspendFunArgs()
            return CoroutineScope(continuation.context).async { execute(realArgs) }
        }
    }

    protected companion object {
        protected fun Array<out Any?>.parseSuspendFunArgs() =
            copyOfRange(0, size - 1) to get(size - 1) as Continuation<Any>
    }
}
