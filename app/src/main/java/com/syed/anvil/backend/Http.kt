package com.syed.anvil.backend

import java.net.HttpURLConnection
import java.net.URL

class HttpResult(val code: Int, val body: String, private val headers: Map<String?, List<String>>) {
    val ok get() = code in 200..299
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()
}

class HttpException(val code: Int, message: String) : Exception(message)

/** Raw HttpURLConnection, like Magpie — no networking library needed for a few JSON calls. */
object Http {
    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = 30_000,
    ): HttpResult {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = timeoutMs
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..399) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpResult(code, text, c.headerFields)
        } finally {
            c.disconnect()
        }
    }
}
