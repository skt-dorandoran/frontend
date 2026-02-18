package org.duckdns.dorandoran.callaiassistant.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object AssetCopyUtil {
    /**
     * assets 하위의 전체 디렉토리/파일을 targetDir로 복사
     * @param context Context
     * @param assetDir assets/ 하위 경로 (예: "sherpa-onnx/sherpa-onnx-streaming-zipformer-korean-2024-06-16")
     * @param targetDir 복사 대상 디렉토리 (예: context.filesDir/sherpa-onnx/...)
     */
    fun copyAssetFolder(context: Context, assetDir: String, targetDir: File) {
        val assetManager = context.assets
        val files = assetManager.list(assetDir) ?: return
        if (!targetDir.exists()) targetDir.mkdirs()
        for (file in files) {
            val assetPath = if (assetDir.isEmpty()) file else "$assetDir/$file"
            val outFile = File(targetDir, file)
            if (assetManager.list(assetPath)?.isNotEmpty() == true) {
                // 디렉토리
                copyAssetFolder(context, assetPath, outFile)
            } else {
                // 파일
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
