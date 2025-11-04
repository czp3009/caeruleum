# Caeruleum

Retrofit inspired Http client base on ktor-client.

Note: Please first consider using the [Resources](https://ktor.io/docs/type-safe-request.html) plugin maintained by
Jetbrains.

Warning: Caeruleum is a side project that may contain critical bugs, and its API may be unstable. Use at your own risk.

Suggestion: More powerful, stable, and multiplatform Retrofit-like HTTP
client: [Ktorfit](https://github.com/Foso/Ktorfit)

# Gradle

```groovy
// https://mvnrepository.com/artifact/com.hiczp/caeruleum
implementation group: 'com.hiczp', name: 'caeruleum', version: '2.0.0'
```

# Usage

```kotlin
//define HTTP API in interface with annotation
@BaseUrl("https://api.github.com")
interface GitHubService {
    @Get("users/{user}/repos")
    suspend fun listRepos(@Path user: String): JsonElement
}

fun main() {
    //create closeable HttpClient
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }
    //get implementation of interface
    val githubService = Caeruleum().create<GitHubService>(httpClient)
    runBlocking {
        //send http request
        githubService.listRepos("czp3009").run(::println)
    }
    //cleanup
    httpClient.close()
}
```

Engine is optional, example above use CIO engine.

see all available engine here https://ktor.io/docs/http-client-engines.html

# License

Apache License 2.0
