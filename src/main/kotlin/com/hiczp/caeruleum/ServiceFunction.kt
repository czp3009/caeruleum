package com.hiczp.caeruleum

import com.hiczp.caeruleum.annotation.*
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType

private val jobType = Job::class.createType()
private val deferredType = Deferred::class.starProjectedType

class ServiceFunction(kClass: KClass<*>, kFunction: KFunction<*>) {
    lateinit var httpMethod: HttpMethod
    lateinit var url: String
    var headers = emptyArray<String>()
    var isFormUrlEncoded = false
    var isMultipart = false
    val isSuspend = kFunction.isSuspend
    val returnType = kFunction.returnType
    val isReturnJob = returnType.isSubtypeOf(jobType).also {
        if (!it) throw IllegalArgumentException("Service functions must return $jobType")
    }
    val isReturnDeferred = returnType.isSubtypeOf(deferredType)

    init {
        var particlePath: String
        kFunction.annotations.forEach {
            when (it) {
                is Get -> {
                    httpMethod = HttpMethod.Get;particlePath = it.value
                }
                is Post -> {
                    httpMethod = HttpMethod.Post;particlePath = it.value
                }
                is Put -> {
                    httpMethod = HttpMethod.Put;particlePath = it.value
                }
                is Patch -> {
                    httpMethod = HttpMethod.Patch;particlePath = it.value
                }
                is Delete -> {
                    httpMethod = HttpMethod.Delete;particlePath = it.value
                }
                is Head -> {
                    httpMethod = HttpMethod.Head;particlePath = it.value
                }
                is Options -> {
                    httpMethod = HttpMethod.Options;particlePath = it.value
                }
                is Headers -> headers = it.value
                is FormUrlEncoded -> isFormUrlEncoded = true
                is Multipart -> isMultipart = true
            }
        }
        if (!::httpMethod.isInitialized)
            throw IllegalArgumentException("No HttpMethod Designated")
        if (isFormUrlEncoded && isMultipart)
            throw IllegalArgumentException("Body cannot be FormUrlEncoded and Multipart at same time")

        val baseUrl = kClass.findAnnotation<BaseUrl>()?.value ?: ""
    }

    fun httpRequestBuilder() =
        HttpRequestBuilder()
}
