package com.questline.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Механизм обновления с GitHub Releases (порт из NutriLens):
 * проверить последний релиз → скачать APK → запустить установку.
 */
object UpdateChecker {
    private val client = OkHttpClient()

    /** Последний релиз или null, если релизов нет (404). */
    suspend fun checkLatest(repo: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "QuestlineAndroid")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw RuntimeException("GitHub HTTP ${response.code}")
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val version = json.optString("tag_name", "").removePrefix("v")
            val notes = json.optString("body", "")
            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name", "").endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "").ifEmpty { null }
                        break
                    }
                }
            }
            ReleaseInfo(version, apkUrl ?: "", notes)
        }
    }

    /** true если latest строго новее current ("1.2.3") */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val parse = { s: String ->
            val parts = s.split('.').map { it.toIntOrNull() ?: 0 }
            (parts + List(3) { 0 }).take(3)
        }
        val latestParts = parse(latest)
        val currentParts = parse(current)
        for (i in 0 until 3) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return false
    }

    /** Стримит APK в cacheDir/updates/questline-update.apk; onProgress 0..100 (-1 = неизвестно). */
    suspend fun downloadApk(context: Context, url: String, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val updateDir = File(context.cacheDir, "updates")
            updateDir.mkdirs()
            val target = File(updateDir, "questline-update.apk")

            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw RuntimeException("Download HTTP ${response.code}")
                val body = response.body ?: throw RuntimeException("Empty response body")
                val contentLength = body.contentLength()
                if (contentLength <= 0) onProgress(-1)
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            total += read
                            if (contentLength > 0) {
                                onProgress(((total * 100) / contentLength).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
            }
            target
        }

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallUnknownAppsSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.packageName),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "com.questline.app.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
