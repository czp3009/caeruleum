package com.hiczp.caeruleum

import com.hiczp.caeruleum.annotation.*
import com.hiczp.caeruleum.annotation.Headers
import com.hiczp.caeruleum.annotation.Url
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.Job
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinFunction

private val jobType = Job::class.createType()
private val mapType = Map::class.starProjectedType
private val unitTypeInfo = typeInfo<Unit>()

internal fun parseServiceFunction(
    kClass: KClass<*>,
    method: Method,
    httpClient: HttpClient,
    baseUrl: String?
): ServiceFunction {
    val jClass = kClass.java
    val kFunction = method.kotlinFunction
    return when {
        //isSynthetic
        kFunction == null -> SyntheticServiceFunction
        //method in Object
        method.declaringClass == Any::class.java -> when (kFunction.name) {
            "equals" -> ObjectDeclaredServiceFunction.Equals(jClass)
            "hashCode" -> ObjectDeclaredServiceFunction.HashCode(jClass)
            "toString" -> ObjectDeclaredServiceFunction.ToString(jClass)
            else -> DoNothingServiceFunction  //impossible
        }
        //default method
        method.isDefault -> NonAbstractServiceFunction.DefaultMethod(jClass, method)
        //non-abstract kotlin function
        !kFunction.isAbstract -> NonAbstractServiceFunction.KotlinDefaultImpls(jClass, method)
        //http service function
        else -> parseHttpServiceFunction(kClass, kFunction).let {
            when {
                it.isBlocking -> HttpServiceFunction.Blocking(it, httpClient, baseUrl)
                it.isSuspend && !it.returnTypeIsJob -> HttpServiceFunction.Suspend(it, httpClient, baseUrl)
                !it.isSuspend && it.returnTypeIsJob -> HttpServiceFunction.Job(it, httpClient, baseUrl)
                else -> HttpServiceFunction.SuspendAndJob(it, httpClient, baseUrl)
            }
        }
    }
}

internal fun parseHttpServiceFunction(kClass: KClass<*>, kFunction: KFunction<*>): HttpServiceFunctionParseResult {
    val isSuspend = kFunction.isSuspend
    val returnTypeIsJob = kFunction.returnType.isSubtypeOf(jobType)
    val realReturnTypeInfo = with(kFunction.returnType) {
        if (returnTypeIsJob) {
            arguments.firstOrNull()?.type?.let { typeInfoImpl(it.javaType, it.jvmErasure, it) } ?: unitTypeInfo
        } else {
            typeInfoImpl(this.javaType, this.jvmErasure, this)
        }
    }

    val actions = Array(kFunction.valueParameters.size) {
        mutableListOf<HttpRequestBuilder.(value: Any) -> Unit>()
    }

    //parse function
    val functionLevelAttributes = Attributes(concurrent = false)
    val functionLevelHeaders = HeadersBuilder()
    var functionLevelPath = ""
    var httpMethod: HttpMethod? = null
    var isFormUrlEncoded = false
    var isMultipart = false
    fun parseHttpMethodAndPath(method: HttpMethod, path: String) {
        check(httpMethod == null) { "Only one HTTP method is allowed" }
        httpMethod = method
        functionLevelPath = path
    }
    kFunction.annotations.forEach {
        when (it) {
            is Get -> parseHttpMethodAndPath(HttpMethod.Get, it.value)
            is Post -> parseHttpMethodAndPath(HttpMethod.Post, it.value)
            is Put -> parseHttpMethodAndPath(HttpMethod.Put, it.value)
            is Patch -> parseHttpMethodAndPath(HttpMethod.Patch, it.value)
            is Delete -> parseHttpMethodAndPath(HttpMethod.Delete, it.value)
            is Head -> parseHttpMethodAndPath(HttpMethod.Head, it.value)
            is Options -> parseHttpMethodAndPath(HttpMethod.Options, it.value)
            is Headers -> {
                it.value.forEach { header ->
                    val colon = header.indexOf(':')
                    if (colon == -1 || colon == 0 || colon == header.length - 1) {
                        error("@Headers value must be in the form 'Name: Value'")
                    }
                    functionLevelHeaders.append(
                        header.substring(0, colon),
                        header.substring(colon + 1).trim()
                    )
                }
            }
            is FormUrlEncoded -> {
                check(!isMultipart) { "Only one encoding annotation is allowed" }
                isFormUrlEncoded = true
            }
            is Multipart -> {
                check(!isFormUrlEncoded) { "Only one encoding annotation is allowed" }
                isMultipart = true
            }
            is Attribute -> functionLevelAttributes.put(AttributeKey(it.key), it.value)
        }
    }
    checkNotNull(httpMethod) { "HTTP method annotation is required" }

    //parse parameters
    var gotUrl = false
    var gotPath = false
    var gotQuery = false
    var gotQueryMap = false
    var gotBody = false
    kFunction.valueParameters.forEachIndexed { index, kParameter ->
        fun String.orKParameterName() = ifEmpty { kParameter.name } ?: this

        kParameter.annotations.forEach { annotation ->
            fun parseQuery(annotationValues: Array<out String>) {
                annotationValues.forEach {
                    val name = it.ifEmpty { kParameter.name } ?: it
                    gotQuery = true
                    actions[index].add { value ->
                        url.parameters.appendValueAsStringList(name, value)
                    }
                }
            }

            fun parseField(annotationValues: Array<out String>) {
                check(isFormUrlEncoded) { "@Field parameters can only be used with form encoding" }
                annotationValues.forEach {
                    val name = it.ifEmpty { kParameter.name } ?: it
                    actions[index].add { value ->
                        (body as ParametersBuilder).appendValueAsStringList(name, value)
                    }
                }
            }

            when (annotation) {
                is Url -> {
                    check(!gotUrl) { "Multiple @Url method annotations found" }
                    check(!gotPath) { "@Path parameters may not be used with @Url" }
                    check(!gotQuery) { "A @Url parameter must not come after @Query" }
                    check(!gotQueryMap) { "A @Url parameter must not come after @QueryMap" }
                    check(functionLevelPath.isEmpty()) { "@Url cannot be used with ${httpMethod!!.value} URL" }
                    gotUrl = true
                    actions[index].add { value ->
                        url.takeFrom(value.toString())
                    }
                }

                is Path -> {
                    check(!gotQuery) { "A @Path parameter must not come after a @Query" }
                    check(!gotQueryMap) { "A @Path parameter must not come after a @QueryMap" }
                    check(!gotUrl) { "@Path parameters may not be used with @Url" }
                    check(functionLevelPath.isNotEmpty()) { "@Path can only be used with relative url on ${httpMethod!!.value}" }
                    gotPath = true
                    val name = "{${annotation.value.orKParameterName()}}"
                    actions[index].add { value ->
                        url.encodedPath = url.encodedPath.replace(
                            name,
                            value.toStringOrEnumName().encodeURLPath()
                        )
                    }
                }

                is Header -> {
                    val name = annotation.value.orKParameterName()
                    actions[index].add { value ->
                        headers.append(name, value.toStringOrEnumName())
                    }
                }

                is HeaderMap -> {
                    check(kParameter.type.isSubtypeOf(mapType)) { "@HeaderMap parameter type must be Map" }
                    actions[index].add { value ->
                        headers.appendMap(value as Map<*, *>)
                    }
                }

                is Query -> parseQuery(annotation.value)

                is QueryMap -> {
                    check(kParameter.type.isSubtypeOf(mapType)) { "@QueryMap parameter type must be Map" }
                    gotQueryMap = true
                    actions[index].add { value ->
                        url.parameters.appendMap(value as Map<*, *>)
                    }
                }

                is Field -> parseField(annotation.value)

                is FieldMap -> {
                    check(isFormUrlEncoded) { "@FieldMap parameters can only be used with form encoding" }
                    check(kParameter.type.isSubtypeOf(mapType)) { "@FieldMap parameters can only be used with form encoding" }
                    actions[index].add { value ->
                        val body = body as ParametersBuilder
                        body.appendMap(value as Map<*, *>)
                    }
                }

                is Part -> {
                    check(isMultipart) { "@Part parameters can only be used with multipart encoding" }
                    val name = annotation.value.orKParameterName()
                    actions[index].add { value ->
                        //value may not be String
                        @Suppress("UNCHECKED_CAST")
                        (body as MutableList<FormPart<*>>).add(FormPart(name, value))
                    }
                }

                is PartMap -> {
                    check(isMultipart) { "@PartMap parameters can only be used with multipart encoding" }
                    check(kParameter.type.isSubtypeOf(mapType)) { "@FieldMap parameter type must be Map" }
                    actions[index].add { value ->
                        @Suppress("UNCHECKED_CAST")
                        val body = body as MutableList<FormPart<*>>
                        (value as Map<*, *>).forEach { (partName, partValue) ->
                            if (partName != null && partValue != null) {
                                body.add(FormPart(partName.toStringOrEnumName(), partValue))
                            }
                        }
                    }
                }

                is Body -> {
                    check(!isFormUrlEncoded && !isMultipart) { "@Body parameters cannot be used with form or multi-part encoding" }
                    check(!gotBody) { "Multiple @Body method annotations found" }
                    gotBody = true
                    actions[index].add { value ->
                        body = value
                    }
                    //Content-Type priority: OutGoingContent.contentType > @Body > @DefaultContentType
                    val contentType = (annotation.value.takeIf { it.isNotEmpty() }
                        ?: kClass.findAnnotation<DefaultContentType>()?.value?.takeIf { it.isNotEmpty() })
                        ?.let { ContentType.parse(it) }
                    if (contentType != null) {
                        actions[index].add { value ->
                            if (value !is OutgoingContent || value.contentType == null) {
                                contentType(contentType)
                            }
                        }
                    }
                }

                is Queries -> annotation.value.forEach { parseQuery(it.value) }

                is Fields -> annotation.value.forEach { parseField(it.value) }
            }
        }
    }

    val classLevelBaseUrl = kClass.findAnnotation<BaseUrl>()?.value

    return HttpServiceFunctionParseResult(
        isSuspend,
        returnTypeIsJob,
        realReturnTypeInfo,
        actions,
        classLevelBaseUrl,
        functionLevelAttributes,
        functionLevelHeaders,
        functionLevelPath,
        httpMethod!!,
        isFormUrlEncoded,
        isMultipart
    )
}
