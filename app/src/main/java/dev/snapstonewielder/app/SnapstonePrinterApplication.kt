package dev.snapstonewielder.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dev.snapstonewielder.app.data.api.ScryfallHeaderInterceptor
import okhttp3.OkHttpClient

/**
 * Registers a Coil singleton whose OkHttpClient sends Scryfall's required User-Agent header;
 * without it, cards.scryfall.io rejects image requests with HTTP 400 (rule=generic_user_agent).
 * This backs every AsyncImage in the app that doesn't supply its own ImageLoader - see
 * ArtDownloader for the print-pipeline's separate client, which needs the same header.
 */
class SnapstonePrinterApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            // Only the User-Agent, not the full ScryfallHeaderInterceptor: that interceptor also
            // forces Accept: application/json, which is wrong for an image CDN request.
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", ScryfallHeaderInterceptor.DEFAULT_USER_AGENT)
                        .build()
                )
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .build()
    }
}
