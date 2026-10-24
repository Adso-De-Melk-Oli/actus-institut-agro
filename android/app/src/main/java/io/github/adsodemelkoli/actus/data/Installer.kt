package io.github.adsodemelkoli.actus.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mise à jour sans passer par le navigateur : télécharge le nouvel APK dans le cache de l'application,
 * vérifie que c'est bien la version attendue de cette application, puis ouvre l'installateur d'Android.
 * Android demande une seule fois l'autorisation « Installer des applis inconnues » pour cette application.
 */
object Installer {
    private fun dir(context: Context) = File(context.cacheDir, "maj").apply { mkdirs() }

    suspend fun download(context: Context, url: String, expectedCode: Int, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val target = File(dir(context), "ActusInstitutAgro-$expectedCode.apk")
            if (target.exists() && isValid(context, target, expectedCode)) return@withContext target
            dir(context).listFiles()?.forEach { it.delete() }
            val part = File(dir(context), "telechargement.part")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            try {
                if (conn.responseCode !in 200..299) throw HttpStatusException(conn.responseCode)
                val total = conn.contentLengthLong
                var done = 0L
                var lastStep = -1
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val step = (done * 100 / total).toInt()
                                if (step != lastStep) { lastStep = step; onProgress(done.toFloat() / total) }
                            }
                        }
                    }
                }
                if (total > 0 && done != total) throw BadDownloadException()
            } finally {
                conn.disconnect()
            }
            if (!part.renameTo(target) || !isValid(context, target, expectedCode)) {
                target.delete(); part.delete()
                throw BadDownloadException()
            }
            target
        }

    /** Le fichier est-il bien cette application, dans une version au moins égale à celle annoncée ? */
    fun isValid(context: Context, f: File, expectedCode: Int): Boolean {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(f.path, 0) ?: return false
        return info.packageName == context.packageName && PackageInfoCompat.getLongVersionCode(info) >= expectedCode
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(context: Context, f: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fichiers", f)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Une fois la mise à jour installée, l'ancien fichier ne sert plus. */
    fun cleanup(context: Context, installedCode: Int) {
        dir(context).listFiles()?.forEach { f ->
            val code = Regex("""-(\d+)\.apk$""").find(f.name)?.groupValues?.get(1)?.toIntOrNull()
            if (code == null || code <= installedCode) f.delete()
        }
    }
}
