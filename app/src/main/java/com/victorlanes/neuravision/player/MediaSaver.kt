package com.victorlanes.neuravision.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Salva fotos na galeria e organiza as gravações do app. */
object MediaSaver {

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Pasta onde o libVLC grava os vídeos (app-specific, sem precisar de permissão). */
    fun recordingsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "Gravacoes")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Salva um quadro (snapshot) na galeria em Pictures/NeuraVision. Devolve o nome ou null. */
    fun saveSnapshot(context: Context, bmp: Bitmap, camera: String): String? {
        val name = "NeuraVision_${camera}_${stamp()}.jpg".replace(" ", "_")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NeuraVision")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                name
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "NeuraVision")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, name)
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.insertImage(context.contentResolver, file.absolutePath, name, "NeuraVision")
                name
            }
        } catch (e: Exception) {
            null
        }
    }

    /** O arquivo mais novo dentro da pasta de gravações (o que acabou de ser gravado). */
    fun newestRecording(context: Context): File? =
        recordingsDir(context).listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }

    fun listMedia(context: Context): List<File> {
        val recs = recordingsDir(context).listFiles()?.filter { it.isFile }?.toList() ?: emptyList()
        return recs.sortedByDescending { it.lastModified() }
    }
}
