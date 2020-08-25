package com.reactnativetranscription

//import kotlin.Throws

interface OnAudioRecordListener {
  fun onRecordFinished(recordingItem: RecordingItem?)
  fun onError(errorCode: Int)
  fun onRecordingStarted()
}
