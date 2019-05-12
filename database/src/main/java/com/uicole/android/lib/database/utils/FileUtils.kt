package com.uicole.android.lib.database.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FileUtils {

    companion object {

        fun writeFile(filePath: String, content: String) {
            var file = File(filePath)
            if (!file.exists()) {
                file.createNewFile()
            }
            var outputStream = FileOutputStream(file)
            outputStream.write(content.toByteArray())
            outputStream.close()
        }

        fun readFile(filePath: String): String? {
            var file = File(filePath)
            if (!file.exists() || !file.isFile) {
                return null
            }
            var inputStream = FileInputStream(file)
            var fileLength = inputStream.available()
            if (fileLength <= 0) {
                return null
            }
            var byteArray = inputStream.readBytes(fileLength)
            inputStream.close()
            return String(byteArray)

        }
    }
}