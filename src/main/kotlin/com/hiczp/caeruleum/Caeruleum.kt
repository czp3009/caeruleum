package com.hiczp.caeruleum

import io.ktor.client.*
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses

@Suppress("MemberVisibilityCanBePrivate")
class Caeruleum(
    val httpClient: HttpClient = HttpClient(),
    val baseUrl: String? = null
) {
    private val cachedServiceFunctions = ConcurrentHashMap<Method, ServiceFunction>()

    fun <T : Any> create(serviceInterfaceKClass: KClass<T>): T {
        val serviceInterfaceJClass = serviceInterfaceKClass.java
        //check if interface
        require(serviceInterfaceJClass.isInterface) { "API declarations must be interfaces" }
        //check generic type
        require(serviceInterfaceKClass.typeParameters.isEmpty()) { "Type parameters are unsupported on ${serviceInterfaceKClass.qualifiedName}" }
        //check generic type in super interface
        val superInterfaceWithTypeParameters = serviceInterfaceKClass.allSuperclasses.find {
            it.typeParameters.isNotEmpty()
        }
        require(superInterfaceWithTypeParameters == null) {
            "Type parameters are unsupported on ${superInterfaceWithTypeParameters!!.qualifiedName} which is an interface of ${serviceInterfaceKClass.qualifiedName}"
        }

        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(
            serviceInterfaceJClass.classLoader,
            arrayOf(serviceInterfaceJClass)
        ) { proxy, method, args: Array<Any?>? ->
            val serviceFunction = cachedServiceFunctions[method] ?: run {
                synchronized(cachedServiceFunctions) {
                    //prevent parse more than once
                    cachedServiceFunctions[method] ?: run {
                        parseServiceFunction(serviceInterfaceKClass, method, httpClient, baseUrl).also {
                            cachedServiceFunctions[method] = it
                        }
                    }
                }
            }
            serviceFunction(proxy, method, args)
        } as T
    }
}

inline fun <reified T : Any> HttpClient.create(baseUrl: String? = null) =
    Caeruleum(this, baseUrl).create(T::class)
