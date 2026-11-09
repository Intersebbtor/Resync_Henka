package io.github.radixtrator.resync.data

import okhttp3.OkHttpClient

/**
 * The one OkHttp client the app's others are built from (with newBuilder), so API calls, images and downloads
 * share a connection pool and thread pool: an image from i.redd.it reuses the connection a download opened,
 * and the process starts one set of threads rather than three.
 */
object Http {
    val base: OkHttpClient by lazy { OkHttpClient() }
}
