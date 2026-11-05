package io.github.radixtrator.resync

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient

class ResyncApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * Animated GIF/WebP support (platform decoder on API 28+, Coil's own below), and a descriptive
     * User-Agent: some image hosts (e.g. Wikimedia) answer 403 to OkHttp's generic "okhttp/x.y".
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
                add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient() }))
            }
            .crossfade(true)
            .build()

    private fun imageHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Resync/${BuildConfig.VERSION_NAME} (Android)").build())
        }
        .build()
}
