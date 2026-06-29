package com.cambrian.masv_dev.utils

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer

class ProgressRequestBody(
    private val body: RequestBody,
    private val callback: (progress: Long, total: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = body.contentType()

    override fun contentLength(): Long = body.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        val countingSink = CountingSink(sink, total, callback)
        val bufferedSink = countingSink.buffer()  // changed from Okio.buffer()
        body.writeTo(bufferedSink)
        bufferedSink.flush()
    }

    private class CountingSink(
        sink: Sink,
        private val totalBytes: Long,
        private val callback: (progress: Long, total: Long) -> Unit
    ) : ForwardingSink(sink) {

        private var bytesWritten = 0L

        override fun write(source: okio.Buffer, byteCount: Long) {
            super.write(source, byteCount)
            bytesWritten += byteCount
            callback(bytesWritten, totalBytes)
        }
    }
}