package com.elenikivi.videocaptureapiproject.shared.utils

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class FileManager @Inject constructor() {

    private fun getPrivateFileDirectory(): File? {
        val directory =
            File("${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)}${File.separator}VideoCaptureAPI/")
        return if (directory.exists() || directory.mkdirs()) {
            directory
        } else null
    }

    suspend fun createFile(ext: String): String {
        return withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat(
                FILE_TIMESTAMP_FORMAT,
                Locale.getDefault()
            ).format(System.currentTimeMillis())
            return@withContext File(getPrivateFileDirectory(), "$timestamp.$ext").canonicalPath
        }
    }

    companion object {
        const val FILE_TIMESTAMP_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}