package com.reactnativetranscription

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import org.mozilla.deepspeech.libdeepspeech.DeepSpeechModel
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.reactnativetranscription.WavHeaderFunctions.*


class FileTranscriptionModule(reactContext: ReactApplicationContext) {

  private val reactContext: ReactApplicationContext = reactContext

  var model: DeepSpeechModel? = null
  val BEAM_WIDTH = 50

  fun transcribeWav(audioFile: String, modelPath: String, scorerPath: String) {
    //var inferenceExecTime: Long = 0

    if (model == null) {
      for (path in listOf(modelPath, scorerPath)) {
        if (!File(path).exists()) {
          throw Exception("Model creation failed: $path does not exist.\n")
        }
      }


      model = DeepSpeechModel(modelPath)
      model?.enableExternalScorer(scorerPath)
      model!!.setBeamWidth(BEAM_WIDTH.toLong())
      Log.d("transcription","Created model.\n")
    }

    //this._startInference.setEnabled(false)
    //newModel(this._tfliteModel.getText().toString())
    //this._tfliteStatus.setText("Extracting audio features ...")
    Log.d("transcription","Extracting audio features\n")
    try {
      val wave = RandomAccessFile(audioFile, "r")
      wave.seek(20)
      val audioFormat: Char = readLEChar(wave)
      if(audioFormat.toInt() != 1 // 1 is PCM
      ) throw Exception("File isn't PCM")
      wave.seek(22)
      val numChannels: Char = readLEChar(wave)
      if(numChannels.toInt() != 1 // MONO
      ) throw Exception("File isn't mono")
      wave.seek(24)
      val sampleRate: Int = readLEInt(wave)
      if(sampleRate != model!!.sampleRate() // desired sample rate
      ) throw Exception("File isn't 16000hz")
      wave.seek(34)
      val bitsPerSample: Char = readLEChar(wave)
      if(bitsPerSample.toInt() != 16 // 16 bits per sample
      ) throw Exception("File isn't 16 bit")
      // tv_bitsPerSample.setText("bitsPerSample=" + (bitsPerSample == 16 ? "16-bits" : "!16-bits" ));
      wave.seek(40)
      val bufferSize: Int = readLEInt(wave)
      if(bufferSize <= 0) throw Exception("Buffer size is wrong")
      wave.seek(44)
      val bytes = ByteArray(bufferSize)
      wave.readFully(bytes)
      val shorts = ShortArray(bytes.size / 2)
      // to turn bytes to shorts as either big endian or little endian.
      ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()[shorts]
      //this._tfliteStatus.setText("Running inference ...")
      Log.d("transcription","Running inference\n")
      //val inferenceStartTime = System.currentTimeMillis()

      // sphinx-doc: java_ref_inference_start
      val decoded = model!!.sttWithMetadata(shorts, shorts.size, 1)
      Log.d("transcription","Inference complete\n")
      // sphinx-doc: java_ref_inference_stop
      //inferenceExecTime = System.currentTimeMillis() - inferenceStartTime
      //this._decodedString.setText(decoded)
      val map = packageTranscription(decoded)
      emitDeviceEvent("onWavTranscribed", map)
      if (model != null) {
        model?.freeModel()
      }

    } catch (ex: FileNotFoundException) {
    } catch (ex: IOException) {
    } finally {
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

}
