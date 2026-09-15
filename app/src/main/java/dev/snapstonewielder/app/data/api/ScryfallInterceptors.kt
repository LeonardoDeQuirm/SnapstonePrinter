package dev.snapstonewielder.app.data.api

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Scryfall requires every client to identify itself with a descriptive `User-Agent` and to send an
 * explicit `Accept` header. Requests without them are rejected with HTTP 403 in the wild.
 */
class ScryfallHeaderInterceptor(
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val accept: String = DEFAULT_ACCEPT
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgent)
            .header("Accept", accept)
            .build()
        return chain.proceed(request)
    }

    companion object {
        const val DEFAULT_USER_AGENT = "SnapstonePrinter/1.0"
        const val DEFAULT_ACCEPT = "application/json"
    }
}

/**
 * Scryfall asks for 50-100ms of delay between requests. This interceptor keeps a single
 * last-request timestamp behind a lock and sleeps just long enough to honour [minIntervalMs].
 *
 * It intentionally blocks the calling OkHttp dispatcher thread - never the main thread.
 */
class ScryfallThrottleInterceptor(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS
) : Interceptor {

    private val lock = ReentrantLock()
    private var lastRequestAtMs: Long = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        lock.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestAtMs
            if (lastRequestAtMs != 0L && elapsed in 0 until minIntervalMs) {
                try {
                    Thread.sleep(minIntervalMs - elapsed)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            lastRequestAtMs = System.currentTimeMillis()
        }
        return chain.proceed(chain.request())
    }

    companion object {
        /** Top of Scryfall's recommended 50-100ms window. */
        const val DEFAULT_MIN_INTERVAL_MS = 100L
    }
}
