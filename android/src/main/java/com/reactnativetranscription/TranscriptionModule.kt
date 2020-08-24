package com.reactnativetranscription

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.core.DeviceEventManagerModule


import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.mozilla.deepspeech.libdeepspeech.DeepSpeechModel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class TranscriptionModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {



    override fun getName(): String {
        return "Transcription"
    }

    private var reactContext = reactContext
    private var model: DeepSpeechModel? = null

    private var transcriptionThread: Thread? = null
    private var isRecording: AtomicBoolean = AtomicBoolean(false)

    private var lastTranscription: String = ""

    private fun transcribe() {
        // We read from the recorder in chunks of 2048 shorts. With a model that expects its input
        // at 16000Hz, this corresponds to 2048/16000 = 0.128s or 128ms.
        val audioBufferSize = 2048
        val audioData = ShortArray(audioBufferSize)

        model?.let { model ->
            val streamContext = model.createStream()

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                model.sampleRate(),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioBufferSize
            )
            recorder.startRecording()

            while (isRecording.get()) {
                recorder.read(audioData, 0, audioBufferSize)
                model.feedAudioContent(streamContext, audioData, audioData.size)
                val decoded = model.intermediateDecodeWithMetadata(streamContext, 1)
                val decodedString = model.intermediateDecode(streamContext)
                if(decodedString != lastTranscription){
                    lastTranscription = decodedString
                    val map = packageTranscription(decoded)
                    emitDeviceEvent("onRecordingChange", map)
                    //Log.d("transcription", decodedString)
                }
                
            }

            val decoded = model.finishStreamWithMetadata(streamContext, 1)
            recorder.stop()
            recorder.release()
            val map = packageTranscription(decoded)
            emitDeviceEvent("onRecordingCompletion", map)
        }
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

    private fun createModel(modelPath: String, scorerPath: String): Boolean {
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

    private fun startListening() {
        if (isRecording.compareAndSet(false, true)) {
            transcriptionThread = Thread(Runnable { transcribe() }, "Transcription Thread")
            transcriptionThread?.start()
        }
    }

    @ReactMethod
    private fun stopRecording() {
        isRecording.set(false)
    }
    @ReactMethod
    fun startRecording(modelPath: String, scorerPath: String) {
        if (model == null) {
            if (!createModel(modelPath, scorerPath)) {
                return
            }
          Log.d("transcription","Created model.\n")
        }
        startListening()
    }

    @ReactMethod
    fun freeModel() {
        if (model != null) {
            model?.freeModel()
        }
    }

    private fun emitDeviceEvent(eventName: String, eventData: WritableMap?) {
            // A method for emitting from the native side to JS
            // https://facebook.github.io/react-native/docs/native-modules-android.html#sending-events-to-javascript
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(eventName, eventData)
    }


}
