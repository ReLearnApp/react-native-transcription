package com.reactnativetranscription

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder.OutputFormat
import android.util.Log
import java.io.IOException
import java.io.File
import java.nio.ByteBuffer

class AudioEncoder(sampleRate: Int, outputURI: String) {

    val NUM_CHANNELS = 1
    val SAMPLE_RATE = sampleRate
    val FRAME_SIZE = 16 // 16 bit pcm. is FRAME_SIZE bits or bytes?
    val outputURI = outputURI
    val mcl = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    val format = MediaFormat()

    init {
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm")
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, 2); // TODO: or 39?
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64000)
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE)
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, NUM_CHANNELS)
    }

    val encBufSize = 10240 // why? research mediacodec

    val codecname = mcl.findEncoderForFormat(format)
    var codec: MediaCodec? = null

    init {
        Log.d("ENCODER:", "Codec: " + codecname)
        try {
          codec = MediaCodec.createByCodecName(codecname)
        } catch (e: IOException) {
          e.printStackTrace()
        }
        codec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }
    var outputFormat = codec!!.getOutputFormat() // option B
    val usec = 1000000000L * FRAME_SIZE / SAMPLE_RATE
    val bufinfo = MediaCodec.BufferInfo()
    init {
        bufinfo.set(0, FRAME_SIZE * NUM_CHANNELS * 2, usec, 0)
    }
    val inBuf = ByteArray(FRAME_SIZE * NUM_CHANNELS * 2)
    val encBuf = ByteArray(encBufSize)
    var encodedBuffer = ByteBuffer.allocate(encBufSize)


    // MediaMuxer
    //val muxer = MediaMuxer((filePath + "/temp.mp4"), OutputFormat.MPEG_4)

    
    
    val muxer = MediaMuxer(outputURI, OutputFormat.MPEG_4)
    //val audioFormat = MediaFormat()
    val audioTrackIndex = muxer.addTrack(outputFormat)
    val inputBuffer = ByteBuffer.allocate(encBufSize)
    //val bufferInfo = BufferInfo()

    fun startEncoding() {
        val file = File(outputURI)
            if (!file.parentFile.exists()) {

                file.parentFile.mkdirs()
            }
            if (file.exists()) {
                file.delete()
            }
        codec!!.start()
        muxer.start()
    }

    fun stopEncoding() {
        codec!!.stop()
        muxer.stop()
    }

    fun feedAudio(audioData: ShortArray, audioBufferSize: Int) {
        Log.d("ENCODING: ", "feeding audio")
        var encoded = 0
        val inputBufferId = codec!!.dequeueInputBuffer(1000)
        if (inputBufferId >= 0) {
          val inputBuffer = codec!!.getInputBuffer(inputBufferId)
          // fill inputBuffer with valid data
          //inputBuffer!!.put(inBuf, 0, inBuf.size)
          for(s in audioData){
            inputBuffer!!.put(byteArrayOf((s.toInt() and 0x00FF).toByte(), ((s.toInt() and 0xFF00) shr (8)).toByte()), 0, 2)
          }
          //inputBuffer!!.put(audioData, 0, audioBufferSize)
          //codec!!.queueInputBuffer(inputBufferId, 0, inBuf.size, usec, 0)
          codec!!.queueInputBuffer(inputBufferId, 0, audioBufferSize, usec, 0)
        }
        val outputBufferId = codec!!.dequeueOutputBuffer(bufinfo, 1000)
        if (outputBufferId >= 0) {
          val outputBuffer = codec!!.getOutputBuffer(outputBufferId)
          val bufferFormat = codec!!.getOutputFormat(outputBufferId) // option A
          // bufferFormat is identical to outputFormat
          // outputBuffer is ready to be processed or rendered.
          outputBuffer!!.rewind()
          encoded = outputBuffer.remaining()
          outputBuffer.get(encBuf, 0, encoded)
          encodedBuffer = outputBuffer.duplicate()
          codec!!.releaseOutputBuffer(outputBufferId, false)
        } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          // Subsequent data will conform to new format.
          // Can ignore if using getOutputFormat(outputBufferId)
          outputFormat = codec!!.getOutputFormat() // option B
        }
        if (encoded > 0) {
          // Process data in encBuf

          //val finished = getInputBuffer(inputBuffer, isAudioSample, bufferInfo)
          if (/*!finished*/ true)
          {
            val currentTrackIndex = audioTrackIndex
            muxer.writeSampleData(currentTrackIndex, encodedBuffer, bufinfo)
          }
          
          Log.d("ENCODING: ", "processing audio")
        }
    }
}
