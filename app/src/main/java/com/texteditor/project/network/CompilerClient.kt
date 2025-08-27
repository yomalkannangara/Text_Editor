package com.texteditor.project.network

import com.texteditor.project.data.CompileResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class CompilerClient {

    // Default: real device via USB with adb reverse
    // For emulator, change to: "http://10.0.2.2:5000/compile"
    private val endpoint = "http://127.0.0.1:5000/compile"

    private fun postOnce(urlStr: String, code: String): CompileResponse {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection)
        return try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(code.toByteArray(Charsets.UTF_8)) }

            val stream = if (conn.responseCode in 200..299) conn.inputStream
            else (conn.errorStream ?: conn.inputStream)

            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            Gson().fromJson(body, CompileResponse::class.java)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun compile(code: String): CompileResponse = withContext(Dispatchers.IO) {
        try {
            postOnce(endpoint, code)
        } catch (e: Exception) {
            CompileResponse(
                ok = false,
                phase = "client",
                stdout = "",
                stderr = "Failed to connect to $endpoint\n${e.message ?: "Unknown error"}\n\n" +
                        "USB (real phone) steps:\n" +
                        "1) Run: adb reverse tcp:5000 tcp:5000\n" +
                        "2) Start your Flask server on your PC at 127.0.0.1:5000\n" +
                        "3) Ensure INTERNET permission & cleartext HTTP are enabled.",
                exitCode = -1
            )
        }
    }
}
