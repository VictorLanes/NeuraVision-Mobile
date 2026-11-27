package com.victorlanes.neuravision.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Salva fotos e gravações na GALERIA do celular (álbum NeuraVision), visível no app de Arquivos. */
object MediaSaver {

    const val ALBUM = "NeuraVision"

    data class Rec(val uri: Uri, val name: String, val size: Long, val date: Long)

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Pasta temporária onde o libVLC grava antes de publicar na galeria (interna do app). */
    fun recordingsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "temp_rec")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ---------------------------------------------------------------- fotos
    fun saveSnapshot(context: Context, bmp: Bitmap, camera: String): String? {
        val name = "NeuraVision_${camera}_${stamp()}.jpg".replace(" ", "_")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                name
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, name)
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                name
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- gravações
    private fun newestTemp(context: Context): File? =
        recordingsDir(context).listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }

    /**
     * Move a gravação que o libVLC acabou de fechar (na pasta temp) para a GALERIA
     * (Movies/NeuraVision). Devolve o nome publicado, ou null se nada havia para publicar.
     */
    fun publishNewestRecording(context: Context): String? {
        val src = newestTemp(context) ?: return null
        val name = "NeuraVision_${stamp()}.mp4"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + ALBUM)
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                src.delete()
                name
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), ALBUM)
                if (!dir.exists()) dir.mkdirs()
                val dst = File(dir, name)
                src.inputStream().use { input -> dst.outputStream().use { input.copyTo(it) } }
                src.delete()
                MediaScannerConnection.scanFile(context, arrayOf(dst.absolutePath), arrayOf("video/mp4"), null)
                name
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Lista as gravações do álbum NeuraVision (galeria). */
    fun listRecordings(context: Context): List<Rec> {
        val out = mutableListOf<Rec>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val proj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_ADDED)
            val sel = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            val args = arrayOf("%${Environment.DIRECTORY_MOVIES}/$ALBUM%")
            context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, sel, args,
                "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(iId).toString())
                    out.add(Rec(uri, c.getString(iName) ?: "gravação", c.getLong(iSize), c.getLong(iDate) * 1000))
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), ALBUM)
            dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.forEach {
                out.add(Rec(Uri.fromFile(it), it.name, it.length(), it.lastModified()))
            }
        }
        return out
    }

    fun delete(context: Context, rec: Rec): Boolean = try {
        if (rec.uri.scheme == "file") File(rec.uri.path!!).delete()
        else context.contentResolver.delete(rec.uri, null, null) > 0
    } catch (e: Exception) {
        false
    }
}
