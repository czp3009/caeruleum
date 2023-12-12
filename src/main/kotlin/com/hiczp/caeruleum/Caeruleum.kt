package com.hiczp.caeruleum

import io.ktor.client.*
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses

/**
 * Share Caeruleum instance between multi HttpClients to make full use of ServiceFunction cache
 * @param useCache save resolved ServiceFunction and reuse it, default is true
 */
class Caeruleum(useCache: Boolean = true) {
    private val resolveServiceFunction: (method: Method, parse: (method: Method) -> ServiceFunction) -> ServiceFunction =
        if (useCache) {
            val cachedServiceFunctions = ConcurrentHashMap<Method, ServiceFunction>()
            ({ method, parse -> cachedServiceFunctions.computeIfAbsent(method) { parse(it) } })
        } else {
            { method, parse -> parse(method) }
        }

    /**
     * Create ServiceInterface implementation
     * @param serviceInterfaceKClass ServiceInterface
     * @param httpClient the HttpClient which HttpStatement bind to
     * @param baseUrl Programmatically baseUrl, if not null this value override class level @BaseUrl
     * @throws IllegalArgumentException If ServiceInterface is not interface or has type parameters
     */
    fun <T : Any> create(
            serviceInterfaceKClass: KClass<T>,
            httpClient: HttpClient,
            baseUrl: String? = null
    ): T {
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
        ) { proxy, method, args ->
            resolveServiceFunction(method) {
                parseServiceFunction(
                        kClass = serviceInterfaceKClass,
                        method = it,
                        httpClient = httpClient,
                        baseUrl = baseUrl
                )
            }(proxy, method, args)
        } as T
    }

    inline fun <reified T : Any> create(
            httpClient: HttpClient,
            baseUrl: String? = null
    ) = create(T::class, httpClient, baseUrl)
}
