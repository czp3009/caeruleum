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
    val functionLevelAttributes: Iterable<Pair<AttributeKey<String>, String>>,
    val functionLevelHeaders: HeadersBuilder,
    val functionLevelPath: String,
    val httpMethod: HttpMethod,
    val isFormUrlEncoded: Boolean,
    val isMultipart: Boolean,
    val programmaticallyBaseUrl: String?
) {
    val isBlocking = !isSuspend && !returnTypeIsJob
    val functionLevelPathIsAbsolute = functionLevelPath.startsWith('/')
    private val baseUrlAction: URLBuilder.() -> Unit
    private val preAction: HttpRequestBuilder.() -> Unit
    private val postAction: HttpRequestBuilder.() -> Unit

    init {
        //baseUrl
        //set baseUrl before execute actions, so that @Url can override it
        //programmatically baseUrl > @BaseUrl
        //if no url set, ktor will use localhost as host
        val baseUrl = programmaticallyBaseUrl ?: classLevelBaseUrl
        baseUrlAction = if (baseUrl != null) {
            val functionLevelPathAction: URLBuilder.() -> Unit = if (functionLevelPathIsAbsolute) {
                { encodedPath = functionLevelPath }
            } else {
                { encodedPath += functionLevelPath }
            }
            { takeFrom(baseUrl).apply(functionLevelPathAction) }
        } else {
            //no programmatically baseUrl and @BaseUrl
            //try to use function level path as full url
            //if function level not legal, hope for there a method argument with annotation @Url
            runCatching { URLBuilder(functionLevelPath) }.getOrNull()?.let { { takeFrom(it) } } ?: {}
        }
        //preAction and postAction
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

    fun generateHttpRequestBuilder(args: Array<out Any?>) =
        HttpRequestBuilder().apply {
            //init
            functionLevelAttributes.forEach { (key, value) ->
                attributes.put(key, value)
            }
            headers.appendAll(functionLevelHeaders)
            method = httpMethod
            //execute actions
            url.apply(baseUrlAction)
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
