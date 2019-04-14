# Caeruleum
Retrofit inspired Http client base on CIO.

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

runBlocking {
    githubSerivce.listRepos("czp3009").await()
}
```

# License
Apache License 2.0
