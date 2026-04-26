package com.doction.webviewapp.services

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

// ─── Modelos ──────────────────────────────────────────────────────────────────

data class DownloadedItem(
    val id:           String,
    val name:         String,
    val localPath:    String,
    val type:         String,
    val downloadedAt: Long,
    val sizeBytes:    Long,
    val thumbUrl:     String? = null,
    val sourceUrl:    String? = null,
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("localPath", localPath)
        put("type", type); put("downloadedAt", downloadedAt)
        put("sizeBytes", sizeBytes)
        thumbUrl?.let { put("thumbUrl", it) }
        sourceUrl?.let { put("sourceUrl", it) }
    }
    companion object {
        fun fromJson(j: JSONObject) = DownloadedItem(
            id          = j.getString("id"),
            name        = j.getString("name"),
            localPath   = j.getString("localPath"),
            type        = j.getString("type"),
            downloadedAt= j.optLong("downloadedAt"),
            sizeBytes   = j.optLong("sizeBytes"),
            thumbUrl    = j.optString("thumbUrl").ifEmpty { null },
            sourceUrl   = j.optString("sourceUrl").ifEmpty { null },
        )
    }
}

enum class DownloadStatus { DOWNLOADING, DONE, ERROR, CANCELLED }

data class ActiveDownload(
    val id:       String,
    val title:    String,
    val thumbUrl: String? = null,
    @Volatile var progress: Float = 0f,
    @Volatile var status: DownloadStatus = DownloadStatus.DOWNLOADING,
)

// ─── Service ──────────────────────────────────────────────────────────────────

class DownloadService private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)

    private val _active = mutableMapOf<String, ActiveDownload>()
    private val _items  = mutableListOf<DownloadedItem>()
    private val _savedIds = mutableListOf<String>()

    val activeList: List<ActiveDownload> get() = _active.values.toList()
    val activeCount: Int get() = _active.size
    val items: List<DownloadedItem> get() = _items.toList()
    val savedVideos: List<DownloadedItem> get() = _items.filter { _savedIds.contains(it.id) }

    companion object {
        @Volatile private var _instance: DownloadService? = null
        fun init(context: Context) {
            if (_instance == null) {
                _instance = DownloadService(context)
                _instance!!.loadSaved()
            }
        }
        val instance: DownloadService get() = _instance!!
    }

    fun loadSaved() {
        val raw = prefs.getString("downloads_index", null) ?: return
        try {
            val arr = JSONArray(raw)
            _items.clear()
            for (i in 0 until arr.length()) {
                val item = DownloadedItem.fromJson(arr.getJSONObject(i))
                if (File(item.localPath).exists()) _items.add(item)
            }
        } catch (_: Exception) {}
        _savedIds.addAll(prefs.getStringSet("saved_ids", emptySet()) ?: emptySet())
    }

    private fun saveIndex() {
        val arr = JSONArray()
        _items.forEach { arr.put(it.toJson()) }
        prefs.edit()
            .putString("downloads_index", arr.toString())
            .putStringSet("saved_ids", _savedIds.toSet())
            .apply()
    }

    private fun downloadsDir(): File {
        val dir = File(context.filesDir, "downloads")
        dir.mkdirs()
        return dir
    }

    fun startDownload(
        url:       String,
        title:     String,
        type:      String    = "video",
        thumbUrl:  String?   = null,
        sourceUrl: String?   = null,
        onDone:    ((DownloadedItem) -> Unit)? = null,
        onError:   (() -> Unit)? = null,
    ) {
        val id     = System.currentTimeMillis().toString()
        val active = ActiveDownload(id = id, title = title, thumbUrl = thumbUrl)
        _active[id] = active

        thread {
            try {
                val dir  = downloadsDir()
                val ext  = guessExtension(url, type)
                val safe = title.replace(Regex("[^\\w\\s-]"), "")
                    .replace(Regex("\\s+"), "_")
                    .take(40)
                val path = "${dir.absolutePath}/${safe}_$id.$ext"

                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout    = 0
                conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                conn.setRequestProperty("Referer", sourceUrl ?: url)
                conn.connect()

                val total = conn.contentLengthLong
                var downloaded = 0L
                conn.inputStream.use { input ->
                    File(path).outputStream().use { output ->
                        val buf = ByteArray(8192)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            output.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) active.progress = downloaded.toFloat() / total
                        }
                    }
                }
                conn.disconnect()

                val file = File(path)
                val item = DownloadedItem(
                    id           = id,
                    name         = title,
                    localPath    = path,
                    type         = type,
                    downloadedAt = System.currentTimeMillis(),
                    sizeBytes    = if (file.exists()) file.length() else 0L,
                    thumbUrl     = thumbUrl,
                    sourceUrl    = sourceUrl,
                )
                synchronized(_items) { _items.add(0, item) }
                saveIndex()
                active.status = DownloadStatus.DONE
                onDone?.invoke(item)
            } catch (_: Exception) {
                active.status = DownloadStatus.ERROR
                onError?.invoke()
            } finally {
                Thread.sleep(3000)
                _active.remove(id)
            }
        }
    }

    fun delete(id: String) {
        val idx = _items.indexOfFirst { it.id == id }
        if (idx == -1) return
        val item = _items[idx]
        if (File(item.localPath).exists()) File(item.localPath).delete()
        _items.removeAt(idx)
        _savedIds.remove(id)
        saveIndex()
    }

    fun toggleSaved(id: String) {
        if (_savedIds.contains(id)) _savedIds.remove(id) else _savedIds.add(id)
        saveIndex()
    }

    fun isSaved(id: String) = _savedIds.contains(id)

    fun cancel(id: String) {
        _active[id]?.status = DownloadStatus.CANCELLED
    }

    private fun guessExtension(url: String, type: String): String {
        val lower = url.lowercase().split("?").first()
        listOf("mp4","webm","ogg","mov","mkv").forEach { if (lower.endsWith(".$it")) return it }
        listOf("jpg","jpeg","png","gif","webp").forEach { if (lower.endsWith(".$it")) return it }
        return if (type == "video") "mp4" else "jpg"
    }
}