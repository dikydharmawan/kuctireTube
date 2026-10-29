package com.kutir.drtube.data.api

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

class NewPipeDownloader : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val headers = request.headers()
        val contentType = headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.let { MediaType.parse(it) }

        var body = request.dataToSend()?.let { RequestBody.create(contentType, it) }
        if (request.httpMethod() == "POST" && body == null) {
            body = RequestBody.create(contentType, ByteArray(0))
        }

        val okHttpRequest = OkHttpRequest.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .apply {
                headers.forEach { (name, values) ->
                    if (!name.equals("Content-Type", ignoreCase = true)) {
                        values.forEach { addHeader(name, it) }
                    }
                }
            }
            .build()

        client.newCall(okHttpRequest).execute().use { response ->
            if (response.code() == 429) {
                throw ReCaptchaException("ReCaptcha challenge requested", request.url())
            }
            return Response(
                response.code(),
                response.message(),
                response.headers().toMultimap(),
                response.body()?.string().orEmpty(),
                response.request().url().toString()
            )
        }
    }
}
