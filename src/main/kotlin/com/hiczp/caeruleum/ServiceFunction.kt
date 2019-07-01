package com.hiczp.caeruleum

import com.hiczp.caeruleum.annotation.*
import com.hiczp.caeruleum.annotation.Headers
import com.hiczp.caeruleum.annotation.Url
import io.ktor.client.call.TypeInfo
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.FormPart
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.*
import io.ktor.util.appendAll
import kotlinx.coroutines.Job
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

private val jobType = Job::class.createType()
private val mapType = Map::class.starProjectedType
private val anyTypeInfo = TypeInfo(Any::class, Any::class.java)

@Suppress("MemberVisibilityCanBePrivate")
internal class ServiceFunction(kClass: KClass<*>, kFunction: KFunction<*>) {
    val isSuspend = kFunction.isSuspend
    val isJob = kFunction.returnType.isSubtypeOf(jobType)

    init {
        if (!isSuspend && !isJob || isSuspend && isJob) {
            error("Service functions must be suspend or return $jobType")
        }
    }

    val returnTypeInfo = with(kFunction.returnType) {
        if (isJob) {
            arguments.firstOrNull()?.type?.let { TypeInfo(it.jvmErasure, it.javaType) } ?: anyTypeInfo
        } else {
            TypeInfo(this.jvmErasure, this.javaType)
        }
    }
    private val actions = Array(kFunction.valueParameters.size) {
        mutableListOf<HttpRequestBuilder.(value: Any) -> Unit>()
    }
    private val defaultHttpRequestBuilder = HttpRequestBuilder()
    private val preAction: HttpRequestBuilder.() -> Unit
    private val postAction: HttpRequestBuilder.() -> Unit

    init {
        var isFormUrlEncoded = false
        var isMultipart = false
        var httpMethod: HttpMethod? = null
        var particlePath = ""
        fun parseHttpMethodAndPath(method: HttpMethod, path: String) {
            if (httpMethod != null) error("Only one HTTP method is allowed")
            httpMethod = method
            particlePath = path
        }

        val headers = HeadersBuilder()

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
                        headers.append(
                            header.substring(0, colon),
                            header.substring(colon + 1).trim()
                        )
                    }
                }
                is FormUrlEncoded -> {
                    if (isMultipart) error("Only one encoding annotation is allowed")
                    isFormUrlEncoded = true
                }
                is Multipart -> {
                    if (isFormUrlEncoded) error("Only one encoding annotation is allowed")
                    isMultipart = true
                }
            }
        }
        if (httpMethod == null) error("HTTP method annotation is required")

        var gotUrl = false
        var gotPath = false
        var gotQuery = false
        var gotQueryMap = false
        var gotBody = false
        kFunction.valueParameters.forEachIndexed { index, kParameter ->
            fun String.orParameterName() = if (isEmpty()) kParameter.name ?: this else this

            kParameter.annotations.forEach { annotation ->
                when (annotation) {
                    is Url -> {
                        if (gotUrl) error("Multiple @Url method annotations found")
                        if (gotPath) error("@Path parameters may not be used with @Url")
                        if (gotQuery || gotQueryMap) error("A @Url parameter must not come after @Query or @QueryMap")
                        if (particlePath.isNotEmpty()) error("@Url cannot be used with ${httpMethod!!.value} URL")
                        gotUrl = true
                        actions[index].add { value ->
                            url.takeFrom(value.toString())
                        }
                    }

                    is Path -> {
                        if (gotQuery || gotQueryMap) error("A @Path parameter must not come after a @Query or @QueryMap")
                        if (gotUrl) error("@Path parameters may not be used with @Url")
                        if (particlePath.isEmpty()) error("@Path can only be used with relative url on ${httpMethod!!.value}")
                        gotPath = true
                        val name = "{${annotation.value.orParameterName()}}"
                        actions[index].add { value ->
                            url.encodedPath = url.encodedPath.replace(
                                name,
                                value.toString().encodeURLPath()
                            )
                        }
                    }

                    is Header -> {
                        val name = annotation.value.orParameterName()
                        actions[index].add { value ->
                            headers.append(name, value.toString())
                        }
                    }

                    is HeaderMap -> {
                        if (!kParameter.type.isSubtypeOf(mapType)) {
                            error("@HeaderMap parameter type must be Map")
                        }
                        actions[index].add { value ->
                            (value as Map<*, *>).forEach { (headerName, headerValue) ->
                                if (headerName != null && headerValue != null) {
                                    headers.append(headerName.toString(), headerValue.toString())
                                }
                            }
                        }
                    }

                    is Query -> {
                        val name = annotation.value.orParameterName()
                        gotQuery = true
                        actions[index].add { value ->
                            url.parameters.append(
                                name,
                                value.toString()
                            )
                        }
                    }

                    is QueryMap -> {
                        if (!kParameter.type.isSubtypeOf(mapType)) {
                            error("@QueryMap parameter type must be Map")
                        }
                        gotQueryMap = true
                        actions[index].add { value ->
                            (value as Map<*, *>).forEach { (queryParamName, queryParamValue) ->
                                if (queryParamName != null && queryParamValue != null) {
                                    url.parameters.append(
                                        queryParamName.toString(),
                                        queryParamValue.toString()
                                    )
                                }
                            }
                        }
                    }

                    is Field -> {
                        if (!isFormUrlEncoded) error("@Field parameters can only be used with form encoding")
                        val name = annotation.value.orParameterName()
                        actions[index].add { value ->
                            (body as ParametersBuilder).append(
                                name,
                                value.toString()
                            )
                        }
                    }

                    is FieldMap -> {
                        if (!isFormUrlEncoded) error("@FieldMap parameters can only be used with form encoding")
                        if (!kParameter.type.isSubtypeOf(mapType)) {
                            error("@FieldMap parameters can only be used with form encoding")
                        }
                        actions[index].add { value ->
                            val body = body as ParametersBuilder
                            (value as Map<*, *>).forEach { (fieldName, fieldValue) ->
                                if (fieldName != null && fieldValue != null) {
                                    body.append(fieldName.toString(), fieldValue.toString())
                                }
                            }
                        }
                    }

                    is Part -> {
                        if (!isMultipart) error("@Part parameters can only be used with multipart encoding")
                        val name = annotation.value.orParameterName()
                        actions[index].add { value ->
                            @Suppress("UNCHECKED_CAST")
                            (body as MutableList<FormPart<*>>).add(FormPart(name, value))
                        }
                    }

                    is PartMap -> {
                        if (!isMultipart) throw  IllegalArgumentException("@PartMap parameters can only be used with multipart encoding")
                        if (!kParameter.type.isSubtypeOf(mapType)) {
                            error("@FieldMap parameter type must be Map")
                        }
                        actions[index].add { value ->
                            @Suppress("UNCHECKED_CAST")
                            val body = body as MutableList<FormPart<*>>
                            (value as Map<*, *>).forEach { (partName, partValue) ->
                                if (partName != null && partValue != null) {
                                    body.add(FormPart(partName.toString(), partValue))
                                }
                            }
                        }
                    }

                    is Body -> {
                        if (isFormUrlEncoded || isMultipart) error("@Body parameters cannot be used with form or multi-part encoding")
                        if (gotBody) error("Multiple @Body method annotations found")
                        val contentTypeValue = annotation.value.takeIf { it.isNotEmpty() }
                            ?: kClass.findAnnotation<DefaultContentType>()?.value
                        gotBody = true
                        actions[index].add { value ->
                            body = value
                        }
                        if (contentTypeValue?.isNotEmpty() == true) {
                            val contentType = ContentType.parse(contentTypeValue)
                            actions[index].add {
                                contentType(contentType)
                            }
                        }
                    }
                }
            }
        }

        defaultHttpRequestBuilder.apply {
            this.headers.appendAll(headers)
            method = httpMethod!!
            url.takeFrom(
                URLBuilder(kClass.findAnnotation<BaseUrl>()?.value ?: "").apply {
                    if (particlePath.isNotEmpty()) {
                        if (particlePath.startsWith('/')) {
                            encodedPath = particlePath
                        } else {
                            encodedPath += particlePath
                        }
                    }
                }
            )
        }

        when {
            isFormUrlEncoded -> {
                preAction = { body = ParametersBuilder() }
                postAction = { body = FormDataContent((body as ParametersBuilder).build()) }
            }
            isMultipart -> {
                preAction = { body = mutableListOf<FormPart<*>>() }
                @Suppress("UNCHECKED_CAST")
                postAction = { body = MultiPartFormDataContent(formData(*(body as List<FormPart<*>>).toTypedArray())) }
            }
            else -> {
                preAction = {}
                postAction = {}
            }
        }
    }

    fun httpRequestBuilder(args: Array<Any?>) =
        HttpRequestBuilder().takeFrom(defaultHttpRequestBuilder).apply {
            preAction()
            args.forEachIndexed { index, arg ->
                if (arg != null) {
                    actions[index].forEach {
                        it(arg)
                    }
                }
            }
            postAction()
        }
}
