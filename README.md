# Caeruleum
Retrofit inspired Http client base on CIO.

# Gradle
```groovy
// https://mvnrepository.com/artifact/com.hiczp/caeruleum
compile group: 'com.hiczp', name: 'caeruleum', version: '1.2.1'
```

# Usage
```kotlin
@BaseUrl("https://api.github.com/")
interface GitHubService {
    @Get("users/{user}/repos")
    fun listReposAsync(@Path user: String): Deferred<JsonElement>

    @Get("users/{user}/repos")
    suspend fun listRepos(@Path user: String): JsonElement
}

val httpClient = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
    install(Logging) {
        level = LogLevel.ALL
    }
}

fun main() {
    val githubService = httpClient.create<GitHubService>()
    runBlocking {
        githubService.listReposAsync("czp3009").await().run(::println)
        githubService.listRepos("czp3009").run(::println)
    }
}
```

Keep moving, don't blocking!

# License
Apache License 2.0
