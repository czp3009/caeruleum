# Caeruleum
Retrofit inspired Http client base on ktor-client.

# Gradle
```groovy
// https://mvnrepository.com/artifact/com.hiczp/caeruleum
implementation group: 'com.hiczp', name: 'caeruleum', version: '1.3.0'
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

# FAQs
## How to set SerializedName for Enum in @Query and @FormUrlEncoded?
```kotlin
enum class SomeEnum {
    @EncodeName("value2")
    VALUE1
}
```

## How to set default ContentType for all api in a interface?
```kotlin
@DefaultContentType("application/something")
interface GitHubService
```

## Is array available in @Query?
```kotlin
@Get
suspend fun queryParamWithArray(@Query key: IntArray = intArrayOf(1, 2, 3))
```

Example above will generate QueryParam as "args=1&args=2&args=3".

Array, Iterable and varargs are equivalent.

If element in Array is null, empty String will be used. e.g "args=1&args=&args=2"

Same with `@Field`.

## Can i use @Query and @Field in same argument?
Of course.
```kotlin
@Post
@FormUrlEncoded
suspend fun multiAnnotation(@Query @Field arg: String)
```

## Can i use one Kotlin argument for multi http queryParams or formFields?
```kotlin
@Post
@FormUrlEncoded
suspend fun multiAnnotation(@Query("arg1", "arg2") @Field("field1", "field2") arg: String)
```

## How to skip body Serializers?
```kotlin
@Post
suspend fun postWithTextBody(
    @Body body: TextContent = TextContent("Testing123", ContentType.Text.Plain)
)
```

Just use ktor provided type `OutgoingContent` as body.

## How to transfer extra Attribute to custom ktor feature?
```kotlin
@Attribute("key", "value")
@Get
suspend fun someFunc()
```

Important: DON'T use `Attributes.contains` to check if a `AttributeKey` exists, this method only compare reference. Get
all keys in it and use `findAny` instead.

# License
Apache License 2.0
