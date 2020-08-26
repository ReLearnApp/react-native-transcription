package com.reactnativetranscription

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.core.DeviceEventManagerModule


import android.media.*
import android.media.MediaCodec.BufferInfo
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.experimental.or
import java.io.File

import org.mozilla.deepspeech.libdeepspeech.DeepSpeechModel
import com.reactnativetranscription.AdtsHeaderBuilder.createAdtsHeader
import org.mozilla.deepspeech.libdeepspeech.DeepSpeechStreamingState

//import kotlin.Throws

class AudioRecordThread internal constructor(outputStream: OutputStream, onRecorderFailedListener: OnRecorderFailedListener?, modelPath: String, scorerPath: String, reactContext: ReactApplicationContext) : Runnable {

  private var reactContext = reactContext
  private var model: DeepSpeechModel? = null
  private var streamContext: DeepSpeechStreamingState? = null
  private var modelPath: String = modelPath
  private var scorerPath: String = scorerPath

  private var transcriptionThread: Thread? = null

  private var lastTranscription: String = ""

  private val bufferSize: Int
  private val mediaCodec: MediaCodec
  private val audioRecord: AudioRecord
  private val outputStream: OutputStream
  private val onRecorderFailedListener: OnRecorderFailedListener?

  fun createModel(): Boolean {
    for (path in listOf(modelPath, scorerPath)) {
      if (!File(path).exists()) {
        throw Exception("Model creation failed: $path does not exist.\n")
        return false
      }
    }

    model = DeepSpeechModel(modelPath)
    model?.enableExternalScorer(scorerPath)

    return true
  }

  override fun run() {
    if (onRecorderFailedListener != null) {
      Log.d(TAG, "onRecorderStarted")
      onRecorderFailedListener.onRecorderStarted()
    }
    if (model == null) {
      if (!createModel()) {
        return
      }
      Log.d("transcription","Created model.\n")
    }
    model?.let{ model -> streamContext = model.createStream() }
    val bufferInfo = BufferInfo()
    val codecInputBuffers = mediaCodec.inputBuffers
    val codecOutputBuffers = mediaCodec.outputBuffers
    try {
      while (!Thread.interrupted()) {
        val success = handleCodecInput(audioRecord, mediaCodec, codecInputBuffers, Thread.currentThread().isAlive)
        if (success) handleCodecOutput(mediaCodec, codecOutputBuffers, bufferInfo, outputStream)
      }
    } catch (e: IOException) {
      Log.w(TAG, e)
    } finally {
      model?.let{ model ->
        val decoded = model.finishStreamWithMetadata(streamContext, 1)
        val map = packageTranscription(decoded)
        emitDeviceEvent("onRecordingCompletion", map)
        if (model != null) {
          model?.freeModel()
        }
      }

      mediaCodec.stop()
      audioRecord.stop()
      mediaCodec.release()
      audioRecord.release()
      try {
        outputStream.close()
      } catch (e: IOException) {
        e.printStackTrace()
      }
    }
  }

  @Throws(IOException::class)
  private fun handleCodecInput(audioRecord: AudioRecord,
                               mediaCodec: MediaCodec, codecInputBuffers: Array<ByteBuffer>,
                               running: Boolean): Boolean {
    val audioRecordData = ByteArray(bufferSize)
    val length = audioRecord.read(audioRecordData, 0, audioRecordData.size)

    model?.let { model ->
      streamContext?.let { streamContext ->
        val shortArray = ShortArray(audioRecordData.size / 2) {
          (audioRecordData[it * 2].toUByte().toInt() + (audioRecordData[(it * 2) + 1].toInt() shl 8)).toShort()
        }
        model.feedAudioContent(streamContext, shortArray, shortArray.size)
        val decoded = model.intermediateDecodeWithMetadata(streamContext, 1)
        val decodedString = model.intermediateDecode(streamContext)

        if(decodedString != lastTranscription){
          lastTranscription = decodedString
          val map = packageTranscription(decoded)
          emitDeviceEvent("onRecordingChange", map)
          Log.d("transcription", decodedString)
        }
      }
    }
    if (length == AudioRecord.ERROR_BAD_VALUE || length == AudioRecord.ERROR_INVALID_OPERATION || length != bufferSize) {
      if (length != bufferSize) {
        if (onRecorderFailedListener != null) {
          Log.d(TAG, "length != BufferSize calling onRecordFailed")
          onRecorderFailedListener.onRecorderFailed()
        }
        return false
      }
    }
    val codecInputBufferIndex = mediaCodec.dequeueInputBuffer(10 * 1000.toLong())
    if (codecInputBufferIndex >= 0) {
      val codecBuffer = codecInputBuffers[codecInputBufferIndex]
      codecBuffer.clear()
      codecBuffer.put(audioRecordData)
      mediaCodec.queueInputBuffer(codecInputBufferIndex, 0, length, 0, if (running) 0 else MediaCodec.BUFFER_FLAG_END_OF_STREAM)
    }
    return true
  }

  @Throws(IOException::class)
  private fun handleCodecOutput(mediaCodec: MediaCodec,
                                codecOutputBuffers: Array<ByteBuffer>,
                                bufferInfo: BufferInfo,
                                outputStream: OutputStream) {
    var codecOutputBuffers = codecOutputBuffers
    var codecOutputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
    while (codecOutputBufferIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
      if (codecOutputBufferIndex >= 0) {
        val encoderOutputBuffer = codecOutputBuffers[codecOutputBufferIndex]
        encoderOutputBuffer.position(bufferInfo.offset)
        encoderOutputBuffer.limit(bufferInfo.offset + bufferInfo.size)
        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
          val header = createAdtsHeader(bufferInfo.size - bufferInfo.offset, SAMPLE_RATE_INDEX, CHANNELS)
          outputStream.write(header)
          val data = ByteArray(encoderOutputBuffer.remaining())
          encoderOutputBuffer[data]
          outputStream.write(data)
        }
        encoderOutputBuffer.clear()
        mediaCodec.releaseOutputBuffer(codecOutputBufferIndex, false)
      } else if (codecOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
        codecOutputBuffers = mediaCodec.outputBuffers
      }
      codecOutputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
    }
  }
  private fun emitDeviceEvent(eventName: String, eventData: WritableMap?) {
    // A method for emitting from the native side to JS
    // https://facebook.github.io/react-native/docs/native-modules-android.html#sending-events-to-javascript
    reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(eventName, eventData)
  }
  private fun packageTranscription(decoded: org.mozilla.deepspeech.libdeepspeech.Metadata): com.facebook.react.bridge.WritableMap {
    var map = Arguments.createMap()
    val transcriptCandidate = decoded.getTranscript(0)
    val words = Arguments.createArray()
    val timestamps = Arguments.createArray()

    var workingString = StringBuilder()
    var lastTimestamp = -1
    for (x in 0..(transcriptCandidate.getNumTokens() - 1)){
      val token = transcriptCandidate.getToken(x.toInt())
      val text = token.getText()
      val timestamp = token.getStartTime()
      if(lastTimestamp == -1){
        lastTimestamp = timestamp.toInt()
      }
      if(text == " " || x == (transcriptCandidate.getNumTokens() - 1)){
        // Append timestamp, reset string, reset timestamp to -1
        words.pushString(workingString.toString())
        timestamps.pushInt(lastTimestamp)
        workingString = StringBuilder()
        lastTimestamp = -1
      }else{
        workingString.append(text)
      }
    }
    map.putArray("words", words)
    map.putArray("timestamps", timestamps)
    return map;
  }


  private fun createAudioRecord(bufferSize: Int): AudioRecord {
    val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT, bufferSize * 10)
    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
      Log.d(TAG, "Unable to initialize AudioRecord")
      throw RuntimeException("Unable to initialize AudioRecord")
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      if (NoiseSuppressor.isAvailable()) {
        val noiseSuppressor = NoiseSuppressor
          .create(audioRecord.audioSessionId)
        if (noiseSuppressor != null) {
          noiseSuppressor.enabled = true
        }
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      if (AutomaticGainControl.isAvailable()) {
        val automaticGainControl = AutomaticGainControl
          .create(audioRecord.audioSessionId)
        if (automaticGainControl != null) {
          automaticGainControl.enabled = true
        }
      }
    }
    return audioRecord
  }

  @Throws(IOException::class)
  private fun createMediaCodec(bufferSize: Int): MediaCodec {
    val mediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm")
    val mediaFormat = MediaFormat()
    mediaFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm")
    mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE)
    mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CHANNELS)
    mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
    mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
    try {
      mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    } catch (e: Exception) {
      Log.w(TAG, e)
      mediaCodec.release()
      throw IOException(e)
    }
    return mediaCodec
  }

  internal interface OnRecorderFailedListener {
    fun onRecorderFailed()
    fun onRecorderStarted()
  }

  companion object {
    private val TAG = AudioRecordThread::class.java.simpleName
    //private const val SAMPLE_RATE = 44100
    private const val SAMPLE_RATE = 16000
    //private const val SAMPLE_RATE_INDEX = 4
    private const val SAMPLE_RATE_INDEX = 8
    private const val CHANNELS = 1
    private const val BIT_RATE = 32000
  }

  init {
    bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    audioRecord = createAudioRecord(bufferSize)
    mediaCodec = createMediaCodec(bufferSize)
    this.outputStream = outputStream
    this.onRecorderFailedListener = onRecorderFailedListener
    mediaCodec.start()
    try {
      audioRecord.startRecording()
    } catch (e: Exception) {
      Log.w(TAG, e)
      mediaCodec.release()
      throw IOException(e)
    }
  }
}
