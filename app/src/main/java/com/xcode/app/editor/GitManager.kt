package com.xcode.app.editor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

data class RepoConfig(val name: String, val ownerRepo: String, val token: String)
data class GitFile(val path: String, val sha: String, val type: String, val size: Int = 0)
data class GitFileContent(
    val path: String,
    val sha: String,
    val content: String,
    val isBinary: Boolean = false,
    val rawBase64: String = ""
)

class GitManager(private var config: RepoConfig) {

    private val base get() = "https://api.github.com/repos/${config.ownerRepo}"

    fun updateConfig(c: RepoConfig) { config = c }

    private fun conn(urlStr: String, method: String = "GET"): HttpURLConnection =
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "token ${config.token}")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15_000
            readTimeout = 20_000
        }

    private fun read(c: HttpURLConnection): String = try {
        c.inputStream.bufferedReader().readText()
    } catch (e: Exception) {
        c.errorStream?.bufferedReader()?.readText() ?: throw e
    }

    suspend fun getTree(branch: String): List<GitFile> = withContext(Dispatchers.IO) {
        val c = conn("$base/git/trees/$branch?recursive=1")
        val json = JSONObject(read(c))
        val tree = json.getJSONArray("tree")
        (0 until tree.length()).map {
            val o = tree.getJSONObject(it)
            GitFile(
                path = o.getString("path"),
                sha = o.optString("sha", ""),
                type = o.getString("type"),
                size = o.optInt("size", 0)
            )
        }
    }

    suspend fun getBranches(): List<String> = withContext(Dispatchers.IO) {
        val c = conn("$base/branches?per_page=100")
        val arr = JSONArray(read(c))
        (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }
    }

    suspend fun getFileContent(path: String, branch: String): GitFileContent =
        withContext(Dispatchers.IO) {
            val encoded = java.net.URLEncoder.encode(path, "UTF-8").replace("+", "%20")
            val c = conn("$base/contents/$encoded?ref=$branch")
            val json = JSONObject(read(c))
            val sha = json.getString("sha")
            val rawB64 = json.getString("content").replace("\n", "")
            val bytes = Base64.getDecoder().decode(rawB64)
            val isBinary = isBinary(path, bytes)
            GitFileContent(
                path = path,
                sha = sha,
                content = if (isBinary) "" else String(bytes, Charsets.UTF_8),
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
        val c = conn("$base/contents/$encoded", "PUT")
        c.doOutput = true
        val b64 = if (isBinary && rawBase64.isNotEmpty()) rawBase64
        else Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
        val body = JSONObject().apply {
            put("message", message)
            put("content", b64)
            put("branch", branch)
            if (sha != null) put("sha", sha)
        }
        OutputStreamWriter(c.outputStream).use { it.write(body.toString()) }
        JSONObject(read(c)).getJSONObject("content").getString("sha")
    }

    suspend fun deleteFile(path: String, sha: String, message: String, branch: String): Boolean =
        withContext(Dispatchers.IO) {
            val encoded = java.net.URLEncoder.encode(path, "UTF-8").replace("+", "%20")
            val c = conn("$base/contents/$encoded", "DELETE")
            c.doOutput = true
            val body = JSONObject().apply {
                put("message", message)
                put("sha", sha)
                put("branch", branch)
            }
            OutputStreamWriter(c.outputStream).use { it.write(body.toString()) }
            c.responseCode in 200..299
        }

    private fun isBinary(path: String, bytes: ByteArray): Boolean {
        val binaryExts = setOf(
            "png","jpg","jpeg","gif","webp","ico","bmp","svg",
            "woff","woff2","ttf","eot","otf","pdf","zip","gz",
            "jar","class","apk","aar","keystore","mp4","mp3","wav","ogg"
        )
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext in binaryExts) return true
        return bytes.take(512).any { it == 0.toByte() }
    }
}