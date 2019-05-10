# Caeruleum
Retrofit inspired Http client base on CIO.

# Gradle
```groovy
// https://mvnrepository.com/artifact/com.hiczp/caeruleum
compile group: 'com.hiczp', name: 'caeruleum', version: '1.0.0'
```

# Usage
```kotlin
@BaseUrl("https://api.github.com/")
interface GitHubService {
    @Get("users/{user}/repos")
    fun listRepos(@Path user: String): Deferred<JsonElement>
}

val httpClient = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.ALL
    }
}

val githubService = httpClient.create<GitHubService>()

fun main() {
    runBlocking {
        githubService.listRepos("czp3009").await().run(::println)
    }
}
```

Keep moving with Kotlin Coroutines, don't blocking!

# License
Apache License 2.0
