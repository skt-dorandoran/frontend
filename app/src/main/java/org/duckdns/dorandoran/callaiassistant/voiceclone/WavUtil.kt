package org.duckdns.dorandoran.callaiassistant.voiceclone

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object WavUtil {
    fun mergeWavFiles(inputFiles: List<File>, outputFile: File) {
        if (inputFiles.isEmpty()) return
        FileOutputStream(outputFile).use { out ->
            var headerWritten = false
            inputFiles.forEach { file ->
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(4096)
                    if (!headerWritten) {
                        var read = input.read(buffer)
                        while (read != -1) {
                            out.write(buffer, 0, read)
                            read = input.read(buffer)
                        }
                        headerWritten = true
                    } else {
                        input.skip(44) // skip header
                        var read = input.read(buffer)
                        while (read != -1) {
                            out.write(buffer, 0, read)
                            read = input.read(buffer)
                        }
                    }
                }
            }
        }
    }
}
