package com.xcode.app.editor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

data class RepoConfig(
    val name: String,
    val ownerRepo: String,
    val token: String
)

data class GitFile(
    val path: String,
    val sha: String,
    val type: String, // "blob" | "tree"
    val size: Int = 0
)

data class GitFileContent(
    val path: String,
    val sha: String,
    val content: String, // decoded UTF-8
    val encoding: String = "base64",
    val isBinary: Boolean = false,
    val rawBase64: String = ""
)

class GitManager(private var config: RepoConfig) {

    private val base get() = "https://api.github.com/repos/${config.ownerRepo}"

    fun updateConfig(newConfig: RepoConfig) {
        config = newConfig
    }

    private fun connection(urlStr: String, method: String = "GET"): HttpURLConnection {
        val url = URL(urlStr)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "token ${config.token}")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15000
            readTimeout = 20000
        }
    }

    private fun readResponse(conn: HttpURLConnection): String {
        return try {
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
    }

    suspend fun getTree(branch: String): List<GitFile> = withContext(Dispatchers.IO) {
        val conn = connection("$base/git/trees/$branch?recursive=1")
        val response = readResponse(conn)
        val json = JSONObject(response)
        val tree = json.getJSONArray("tree")
        val result = mutableListOf<GitFile>()
        for (i in 0 until tree.length()) {
            val item = tree.getJSONObject(i)
            result.add(
                GitFile(
                    path = item.getString("path"),
                    sha = item.optString("sha", ""),
                    type = item.getString("type"),
                    size = item.optInt("size", 0)
                )
            )
        }
        result
    }

    suspend fun getBranches(): List<String> = withContext(Dispatchers.IO) {
        val conn = connection("$base/branches?per_page=100")
        val response = readResponse(conn)
        val arr = JSONArray(response)
        val result = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            result.add(arr.getJSONObject(i).getString("name"))
        }
        result
    }

    suspend fun getFileContent(path: String, branch: String): GitFileContent =
        withContext(Dispatchers.IO) {
            val encoded = java.net.URLEncoder.encode(path, "UTF-8").replace("+", "%20")
            val conn = connection("$base/contents/$encoded?ref=$branch")
            val response = readResponse(conn)
            val json = JSONObject(response)
            val sha = json.getString("sha")
            val rawB64 = json.getString("content").replace("\n", "")
            val bytes = Base64.getDecoder().decode(rawB64)

            // Detect binary
            val isBinary = isBinaryContent(path, bytes)
            val content = if (isBinary) "" else String(bytes, Charsets.UTF_8)

            GitFileContent(
                path = path,
                sha = sha,
                content = content,
                isBinary = isBinary,
                rawBase64 = rawB64
            )
        }

    suspend fun putFile(
        path: String,
        content: String,
        sha: String?,
        message: String,
        branch: String,
        isBinary: Boolean = false,
        rawBase64: String = ""
    ): String = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(path, "UTF-8").replace("+", "%20")
        val conn = connection("$base/contents/$encoded", "PUT")
        conn.doOutput = true

        val b64 = if (isBinary && rawBase64.isNotEmpty()) rawBase64
        else Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))

        val body = JSONObject().apply {
            put("message", message)
            put("content", b64)
            put("branch", branch)
            if (sha != null) put("sha", sha)
        }

        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(body.toString())
        writer.flush()

        val response = readResponse(conn)
        val json = JSONObject(response)
        json.getJSONObject("content").getString("sha")
    }

    suspend fun deleteFile(
        path: String,
        sha: String,
        message: String,
        branch: String
    ): Boolean = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(path, "UTF-8").replace("+", "%20")
        val conn = connection("$base/contents/$encoded", "DELETE")
        conn.doOutput = true

        val body = JSONObject().apply {
            put("message", message)
            put("sha", sha)
            put("branch", branch)
        }

        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(body.toString())
        writer.flush()

        conn.responseCode in 200..299
    }

    private fun isBinaryContent(path: String, bytes: ByteArray): Boolean {
        val binaryExts = setOf(
            "png","jpg","jpeg","gif","webp","ico","bmp",
            "woff","woff2","ttf","eot","otf",
            "pdf","zip","gz","jar","class","apk","aar","keystore",
            "mp4","mp3","wav","ogg","webm"
        )
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext in binaryExts) return true
        // Check for null bytes (binary heuristic)
        return bytes.take(512).any { it == 0.toByte() }
    }
}