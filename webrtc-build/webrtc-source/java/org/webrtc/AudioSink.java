package org.webrtc;

/** Interface for receiving audio data from an AudioTrack. */
public interface AudioSink {
  /**
   * Called when audio data is received.
   * @param data PCM audio data, 16-bit signed, little endian.
   * @param bitsPerSample Usually 16.
   * @param sampleRateHz Usually 48000.
   * @param numberOfChannels Usually 1 or 2.
   * @param numberOfFrames Number of frames in this buffer.
   */
  void onData(byte[] data, int bitsPerSample, int sampleRateHz, int numberOfChannels, int numberOfFrames);
}
