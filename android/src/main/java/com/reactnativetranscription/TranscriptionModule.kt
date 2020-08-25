package com.reactnativetranscription

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.core.DeviceEventManagerModule


import android.util.Log
import com.reactnativetranscription.AudioRecordThread.OnRecorderFailedListener
import java.io.*
//import kotlin.Throws

class TranscriptionModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  private var reactContext = reactContext
  private var file: File? = null
  private var onAudioRecordListener: OnAudioRecordListener? = null
  private var mStartingTimeMillis: Long = 0
  private var mRecordingThread: Thread? = null

  override fun getName(): String {
    return "Transcription"
  }

  fun setOnAudioRecordListener(onAudioRecordListener: OnAudioRecordListener?) {
    this.onAudioRecordListener = onAudioRecordListener
  }

  /*fun setFile(filePath: String?) {
    file = File(filePath)
  }*/

  // Call this method from Activity onStartButton Click to start recording
  @Synchronized
  @ReactMethod
  fun startRecording(filePath: String?, modelPath: String, scorerPath: String) {
    file = File(filePath)
    if (file == null) {
      //onAudioRecordListener!!.onError(FILE_NULL)
      return
    }
    mStartingTimeMillis = System.currentTimeMillis()
    try {
      if (mRecordingThread != null) stopRecording(true)
      mRecordingThread = Thread(AudioRecordThread(outputStream(file), object : OnRecorderFailedListener {
        override fun onRecorderFailed() {
          //onAudioRecordListener!!.onError(RECORDER_ERROR)
          stopRecording(true)
        }

        override fun onRecorderStarted() {
          //onAudioRecordListener!!.onRecordingStarted()
        }
      }, modelPath, scorerPath, reactContext))
      mRecordingThread!!.name = "AudioRecordingThread"
      mRecordingThread!!.start()
    } catch (e: IOException) {
      e.printStackTrace()
    }
  }

  // Call this method from Activity onStopButton Click to stop recording
  @Synchronized
  @ReactMethod
  fun stopRecording(delete: Boolean?) {
    Log.d(TAG, "Recording stopped ")
    if (mRecordingThread != null) {
      mRecordingThread!!.interrupt()
      mRecordingThread = null
      if (file!!.length() == 0L) {
        //onAudioRecordListener!!.onError(IO_ERROR)
        return
      }
      val mElapsedMillis = System.currentTimeMillis() - mStartingTimeMillis
      val recordingItem = RecordingItem()
      recordingItem.filePath = file!!.absolutePath
      recordingItem.name = file!!.name
      recordingItem.length = mElapsedMillis.toInt()
      recordingItem.time = System.currentTimeMillis()
      if (!delete!!) {
        //onAudioRecordListener!!.onRecordFinished(recordingItem)
      } else {
        deleteFile()
      }
    }
  }

  private fun deleteFile() {
    if (file != null && file!!.exists()) Log.d(TAG, String.format("deleting file success %b ", file!!.delete()))
  }

  private fun outputStream(file: File?): OutputStream {
    if (file == null) {
      throw RuntimeException("file is null !")
    }
    val outputStream: OutputStream
    outputStream = try {
      FileOutputStream(file)
    } catch (e: FileNotFoundException) {
      throw RuntimeException(
        "could not build OutputStream from" + " this file " + file.name, e)
    }
    return outputStream
  }

  companion object {
    private const val TAG = "AudioRecording"
    private const val IO_ERROR = 1
    private const val RECORDER_ERROR = 2
    const val FILE_NULL = 3
  }
}
