@file:Suppress("UNCHECKED_CAST")

package com.hiczp.caeruleum

import io.ktor.client.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.reflect.full.isSuperclassOf

internal sealed interface ServiceFunction : InvocationHandler

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

    internal class HashCode(serviceInterface: Class<*>) : ConstantServiceFunction(serviceInterface.name.hashCode())

    internal class ToString(serviceInterface: Class<*>) : ConstantServiceFunction(serviceInterface.name)
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

    @Suppress("SpellCheckingInspection")
    class KotlinDefaultImpls(
        serviceInterface: Class<*>,
        targetMethod: Method,
    ) : NonAbstractServiceFunction {
        private val defaultImpl = serviceInterface.declaredClasses
            .find { it.simpleName == "DefaultImpls" }!!
            .getDeclaredMethod(targetMethod.name, serviceInterface, *targetMethod.parameterTypes)

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any =
            defaultImpl(null, proxy, *args.orEmpty())
    }
}

@Suppress("MemberVisibilityCanBePrivate")
internal sealed class HttpServiceFunction(
    protected val parseResult: HttpServiceFunctionParseResult,
    protected val httpClient: HttpClient,
) : ServiceFunction {
    protected fun generateHttpRequestBuilder(args: Array<out Any?>) =
        parseResult.generateHttpRequestBuilder(args)

    protected fun generateHttpStatement(args: Array<out Any?>) =
        HttpStatement(generateHttpRequestBuilder(args), httpClient)

    protected fun generateSuspendExecutor(): suspend (args: Array<out Any?>) -> Any =
        if (parseResult.realReturnTypeInfo.type.isSuperclassOf(HttpResponse::class)) {
            { args -> generateHttpStatement(args).execute() }
        } else {
            { args -> generateHttpStatement(args).execute { it.call.body(parseResult.realReturnTypeInfo) } }
        }

    //no need to send real request when return value is HttpRequestBuilder, HttpRequestData, HttpStatement
    internal sealed class NoRealRequest(
        parseResult: HttpServiceFunctionParseResult,
        httpClient: HttpClient,
    ) : HttpServiceFunction(parseResult, httpClient) {
        private val argumentProcessor: (args: Array<out Any?>) -> Array<out Any?> =
            if (parseResult.isSuspend) {
                { args -> args.copyOfRange(0, args.size - 1) }
            } else {
                { args -> args }
            }
        abstract val responseGenerator: (args: Array<out Any?>) -> Any
        private val returnValueWrapper: (value: Any) -> Any =
            if (parseResult.returnTypeIsJob) {
                { value -> CompletableDeferred(value) }
            } else {
                { value -> value }
            }

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any {
            return argumentProcessor(args.orEmpty()).let(responseGenerator).let(returnValueWrapper)
        }

        internal class HttpRequestBuilder(
            parseResult: HttpServiceFunctionParseResult,
            httpClient: HttpClient,
        ) : NoRealRequest(parseResult, httpClient) {
            override val responseGenerator: (args: Array<out Any?>) -> Any =
                { args -> generateHttpRequestBuilder(args) }
        }

        internal class HttpRequestData(
            parseResult: HttpServiceFunctionParseResult,
            httpClient: HttpClient,
        ) : NoRealRequest(parseResult, httpClient) {
            override val responseGenerator: (args: Array<out Any?>) -> Any =
                { args -> generateHttpRequestBuilder(args).build() }
        }

        internal class HttpStatement(
            parseResult: HttpServiceFunctionParseResult,
            httpClient: HttpClient,
        ) : NoRealRequest(parseResult, httpClient) {
            override val responseGenerator: (args: Array<out Any?>) -> Any =
                { args -> generateHttpStatement(args) }
        }
    }

    internal class Blocking(
        parseResult: HttpServiceFunctionParseResult,
        httpClient: HttpClient,
    ) : HttpServiceFunction(parseResult, httpClient) {
        private val suspendExecutor = generateSuspendExecutor()

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?) = runBlocking {
            suspendExecutor(args.orEmpty())
        }
    }

    internal class Suspend(
        parseResult: HttpServiceFunctionParseResult,
        httpClient: HttpClient,
    ) : HttpServiceFunction(parseResult, httpClient) {
        private val suspendExecutor = generateSuspendExecutor()

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any {
            val (realArgs, continuation) = args!!.parseSuspendFunArgs()
            CoroutineScope(continuation.context).launch {
                runCatching { suspendExecutor(realArgs) }.run(continuation::resumeWith)
            }
            return COROUTINE_SUSPENDED
        }
    }

    internal class Job(
        parseResult: HttpServiceFunctionParseResult,
        httpClient: HttpClient,
    ) : HttpServiceFunction(parseResult, httpClient) {
        private val suspendExecutor = generateSuspendExecutor()

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any {
            return httpClient.async { suspendExecutor(args.orEmpty()) }
        }
    }

    internal class SuspendAndJob(
        parseResult: HttpServiceFunctionParseResult,
        httpClient: HttpClient,
    ) : HttpServiceFunction(parseResult, httpClient) {
        private val suspendExecutor = generateSuspendExecutor()

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any {
            val (realArgs, continuation) = args!!.parseSuspendFunArgs()
            return CoroutineScope(continuation.context).async { suspendExecutor(realArgs) }
        }
    }

    protected companion object {
        @Suppress("NOTHING_TO_INLINE")
        protected inline fun Array<out Any?>.parseSuspendFunArgs() =
            copyOfRange(0, size - 1) to get(size - 1) as Continuation<Any>
    }
}
