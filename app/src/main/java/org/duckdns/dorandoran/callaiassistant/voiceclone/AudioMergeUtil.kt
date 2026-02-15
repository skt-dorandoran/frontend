package org.duckdns.dorandoran.callaiassistant.voiceclone

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

object AudioMergeUtil {
    /**
     * 여러 m4a 파일을 하나로 합치는 유틸리티 (MediaExtractor + MediaMuxer)
     * @throws Exception 실패 시 예외 발생
     */
    @Throws(Exception::class)
    fun mergeM4aFiles(inputFiles: List<File>, outputFile: File) {
        if (inputFiles.isEmpty()) throw IllegalArgumentException("inputFiles is empty")
        if (outputFile.exists()) outputFile.delete()
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var totalAudioTrackIndex = -1
        var presentationTimeUsOffset = 0L
        val buffer = ByteArray(1024 * 1024)
        for ((fileIdx, inputFile) in inputFiles.withIndex()) {
            val extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    if (totalAudioTrackIndex == -1) {
                        totalAudioTrackIndex = muxer.addTrack(format)
                    }
                    break
                }
            }
            if (audioTrackIndex == -1) throw IllegalArgumentException("No audio track in ${inputFile.name}")
            extractor.selectTrack(audioTrackIndex)
            if (fileIdx == 0) muxer.start()
            val info = android.media.MediaCodec.BufferInfo()
            var firstSampleTimeUs: Long? = null
            var lastWrittenPresentationTimeUs: Long = 0L
            while (true) {
                val sampleSize = extractor.readSampleData(java.nio.ByteBuffer.wrap(buffer), 0)
                if (sampleSize < 0) break
                val sampleTimeUs = extractor.sampleTime
                if (firstSampleTimeUs == null) firstSampleTimeUs = sampleTimeUs
                info.offset = 0
                info.size = sampleSize
                info.flags = extractor.sampleFlags
                // 각 파일의 첫 sampleTime을 0으로 맞추고, 누적 offset 적용
                info.presentationTimeUs = (sampleTimeUs - (firstSampleTimeUs ?: 0L)) + presentationTimeUsOffset
                muxer.writeSampleData(totalAudioTrackIndex, java.nio.ByteBuffer.wrap(buffer, 0, sampleSize), info)
                lastWrittenPresentationTimeUs = info.presentationTimeUs
                extractor.advance()
            }
            // 다음 파일의 offset은 마지막으로 기록한 presentationTimeUs + 1 (또는 0)
            presentationTimeUsOffset = lastWrittenPresentationTimeUs + 1L
            extractor.release()
        }
        muxer.stop()
        muxer.release()
    }
}
