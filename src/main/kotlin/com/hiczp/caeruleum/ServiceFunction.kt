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
import io.ktor.http.content.OutgoingContent
import io.ktor.util.*
import kotlinx.coroutines.Job
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure
import java.lang.reflect.Array as ArrayUtils

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
    private val particlePathAction: URLBuilder.() -> Unit

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
        val attributes = Attributes(concurrent = false)

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
                is Attribute -> attributes.put(AttributeKey(it.key), it.value)
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
                fun parseQuery(annotationValue: String) {
                    val name = annotationValue.orParameterName()
                    gotQuery = true
                    actions[index].add { value ->
                        url.parameters.appendIterableValue(name, value)
                    }
                }

                fun parseField(annotationValue: String) {
                    if (!isFormUrlEncoded) error("@Field parameters can only be used with form encoding")
                    val name = annotationValue.orParameterName()
                    actions[index].add { value ->
                        (body as ParametersBuilder).appendIterableValue(name, value)
                    }
                }

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
                            headers.appendMap(value as Map<*, *>)
                        }
                    }

                    is Query -> parseQuery(annotation.value)

                    is QueryMap -> {
                        if (!kParameter.type.isSubtypeOf(mapType)) {
                            error("@QueryMap parameter type must be Map")
                        }
                        gotQueryMap = true
                        actions[index].add { value ->
                            url.parameters.appendMap(value as Map<*, *>)
                        }
                    }

                    is Field -> parseField(annotation.value)

                    is FieldMap -> {
                        if (!isFormUrlEncoded) error("@FieldMap parameters can only be used with form encoding")
                        if (!kParameter.type.isSubtypeOf(mapType)) {
                            error("@FieldMap parameters can only be used with form encoding")
                        }
                        actions[index].add { value ->
                            val body = body as ParametersBuilder
                            body.appendMap(value as Map<*, *>)
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
                        require(isMultipart) { "@PartMap parameters can only be used with multipart encoding" }
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
                        gotBody = true
                        actions[index].add { value ->
                            body = value
                        }
                        //Content-Type priority: OutGoingContent.contentType > @Body > @DefaultContentType
                        val contentTypeInAnnotation = (annotation.value.takeIf { it.isNotEmpty() }
                            ?: kClass.findAnnotation<DefaultContentType>()?.value?.takeIf { it.isNotEmpty() })
                            ?.let { ContentType.parse(it) }
                        if (contentTypeInAnnotation != null) {
                            actions[index].add { value ->
                                if (value !is OutgoingContent || value.contentType == null) {
                                    contentType(contentTypeInAnnotation)
                                }
                            }
                        }
                    }

                    is Queries -> annotation.value.forEach { parseQuery(it.value) }

                    is Fields -> annotation.value.forEach { parseField(it.value) }
                }
            }
        }

        particlePathAction = if (particlePath.isNotEmpty()) {
            if (particlePath.startsWith('/')) {
                fun URLBuilder.() { encodedPath = particlePath }
            } else {
                fun URLBuilder.() { encodedPath += particlePath }
            }
        } else {
            {}
        }

        defaultHttpRequestBuilder.apply {
            this.headers.appendAll(headers)
            method = httpMethod!!
            attributes.allKeys.forEach {
                @Suppress("UNCHECKED_CAST")
                this.attributes.put(it as AttributeKey<Any>, attributes[it])
            }
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

    private val baseUrlAnnotationValue = kClass.findAnnotation<BaseUrl>()?.value

    fun httpRequestBuilder(baseUrl: String?, args: Array<out Any?>) =
        HttpRequestBuilder().takeFrom(defaultHttpRequestBuilder).apply {
            (baseUrlAnnotationValue ?: baseUrl)?.let {
                url.takeFrom(URLBuilder(it).apply { particlePathAction() })
            }
            preAction()
            args.forEachIndexed { index, arg ->
                if (arg != null) {
                    //enum
                    val value = if (arg is Enum<*>) {
                        arg.javaClass.getField(arg.name).getAnnotation(EncodeName::class.java)?.value ?: arg.name
                    } else {
                        arg
                    }
                    actions[index].forEach {
                        it(value)
                    }
                }
            }
            postAction()
        }
}

@UseExperimental(InternalAPI::class)
private fun StringValuesBuilder.appendIterableValue(name: String, value: Any) {
    when {
        value.javaClass.isArray -> {
            mutableListOf<String>().apply {
                for (i in 0 until ArrayUtils.getLength(value)) {
                    add(ArrayUtils.get(value, i).toString())
                }
            }
        }
        value is Iterable<*> -> value.map { it.toString() }
        else -> listOf(value.toString())
    }.let {
        if (it.isNotEmpty()) {
            appendAll(name, it)
        }
    }
}

@UseExperimental(InternalAPI::class)
private fun StringValuesBuilder.appendMap(value: Map<*, *>) {
    value.forEach { (key, value) ->
        if (key != null && value != null) {
            append(key.toString(), value.toString())
        }
    }
}
