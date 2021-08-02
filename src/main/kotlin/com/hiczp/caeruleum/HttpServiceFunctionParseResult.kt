package com.hiczp.caeruleum

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.reflect.*

@Suppress("ArrayInDataClass", "MemberVisibilityCanBePrivate")
internal data class HttpServiceFunctionParseResult(
    val isSuspend: Boolean,
    val returnTypeIsJob: Boolean,
    val realReturnTypeInfo: TypeInfo,
    val actions: Array<out List<HttpRequestBuilder.(value: Any) -> Unit>>,
    val classLevelBaseUrl: String?,
    val functionLevelAttributes: Attributes,
    val functionLevelHeaders: HeadersBuilder,
    val functionLevelPath: String,
    val httpMethod: HttpMethod,
    val isFormUrlEncoded: Boolean,
    val isMultipart: Boolean
) {
    val isBlocking = !isSuspend && !returnTypeIsJob
    val functionLevelPathIsAbsolute = functionLevelPath.startsWith('/')
    private val functionLevelPathAction: URLBuilder.() -> Unit = if (functionLevelPathIsAbsolute) {
        { encodedPath = functionLevelPath }
    } else {
        { encodedPath += functionLevelPath }
    }
    private val functionLevelPathAsUrlBuilder by lazy {
        runCatching { URLBuilder(functionLevelPath) }.getOrNull()
    }
    private val preAction: HttpRequestBuilder.() -> Unit
    private val postAction: HttpRequestBuilder.() -> Unit

    init {
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

    fun generateHttpRequestBuilder(baseUrl: String? = null, args: Array<out Any?>) =
        HttpRequestBuilder().apply {
            //init
            functionLevelAttributes.allKeys.forEach {
                @Suppress("UNCHECKED_CAST")
                this.attributes.put(it as AttributeKey<Any>, attributes[it])
            }
            headers.appendAll(functionLevelHeaders)
            method = httpMethod
            //set baseUrl before execute actions, so that @Url can override it
            //programmatically baseUrl > @BaseUrl
            //if no url set, ktor will use localhost as host
            val realBaseUrl = (baseUrl ?: classLevelBaseUrl)
            if (realBaseUrl != null) {
                url.takeFrom(realBaseUrl).apply(functionLevelPathAction)
            } else {
                //no programmatically baseUrl and @BaseUrl
                //try to use function level path as full url
                //if function level not legal, hope for there a method argument with annotation @Url
                functionLevelPathAsUrlBuilder?.let { url.takeFrom(it) }
            }
            //execute actions
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
