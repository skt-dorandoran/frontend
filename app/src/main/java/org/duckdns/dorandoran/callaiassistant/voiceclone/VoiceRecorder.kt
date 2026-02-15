package org.duckdns.dorandoran.callaiassistant.voiceclone

import android.media.MediaRecorder
import java.io.File

class VoiceRecorder(private val outputFile: File) {
    private var recorder: MediaRecorder? = null

    fun start() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
    }

    fun stop() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
    }
}
