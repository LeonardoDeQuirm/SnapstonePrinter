package dev.snapstonewielder.app.data.api

import dev.snapstonewielder.app.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Single shared Retrofit/OkHttp stack for the Scryfall API.
 *
 * Everything non-trivial is built inside [scryfallApiService]'s `by lazy` block rather than in
 * eager `object` properties. That is deliberate: eager properties run in the class initialiser, so
 * if any builder throws (an R8-stripped converter, a malformed base URL) the failure escapes as
 * `ExceptionInInitializerError` and permanently poisons the class - every later access then throws
 * a `NoClassDefFoundError` that no longer names the real cause. Both are `Error`s, so the
 * `catch (e: Exception)` handlers in the repository/ViewModel would not stop them either, and the
 * app would simply die.
 *
 * Built lazily, the exact same failure instead surfaces as an ordinary exception at the call site,
 * inside a coroutine, where it can be caught and shown as a normal error state.
 *
 * ## No `KotlinJsonAdapterFactory`
 *
 * Every model in `data.model` is annotated `@JsonClass(generateAdapter = true)` and Moshi's KSP
 * codegen is active, so a generated `*JsonAdapter` exists for each one and Moshi finds it through
 * its built-in class-name lookup. Adding the reflective factory on top was pure overhead: it
 * dragged in `kotlin-reflect` and, because a factory added with `.add()` is consulted BEFORE the
 * generated-adapter lookup, it also shadowed the codegen adapters at runtime.
 */
object RetrofitClient {
    private const val BASE_URL = "https://api.scryfall.com/"

    val scryfallApiService: ScryfallApiService by lazy {
        // Codegen adapters only - see the class doc.
        val moshi = Moshi.Builder().build()

        val httpClient = OkHttpClient.Builder()
            // Scryfall API etiquette: identify ourselves, declare what we accept, and throttle.
            .addInterceptor(ScryfallHeaderInterceptor())
            .addInterceptor(ScryfallThrottleInterceptor())
            .apply {
                // Request/response logging is a debug-only diagnostic. In release it is dead
                // weight at best and a way to leak request contents into logcat at worst.
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        }
                    )
                }
            }
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(httpClient)
            .build()
            .create(ScryfallApiService::class.java)
    }
}
