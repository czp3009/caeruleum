# Caeruleum
Retrofit inspired Http client base on ktor-client.

# Gradle
```groovy
// https://mvnrepository.com/artifact/com.hiczp/caeruleum
compile group: 'com.hiczp', name: 'caeruleum', version: '1.2.6'
```

# Usage
```kotlin
//Define HTTP API in interface with annotation
@BaseUrl("https://api.github.com/")
interface GitHubService {
    @Get("users/{user}/repos")
    fun listReposAsync(@Path user: String): Deferred<JsonElement>

    @Get("users/{user}/repos")
    suspend fun listRepos(@Path user: String): JsonElement
}

//create closeable HttpClient
val httpClient = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
    install(Logging) {
        level = LogLevel.ALL
    }
}

//get implement of interface
fun main() {
    val githubService = httpClient.create<GitHubService>()
    runBlocking {
        githubService.listReposAsync("czp3009").await().run(::println)
        githubService.listRepos("czp3009").run(::println)
    }
}
```

Engine is optional, example above use CIO engine.

see all available engine here https://ktor.io/clients/http-client/engines.html

Keep moving, don't blocking!

# License
Apache License 2.0
