import deepspeech_ios
import Foundation
import AVFoundation
import AudioToolbox
import Accelerate

@objc(Transcription)
class Transcription: RCTEventEmitter {

    @objc(multiply:withB:withResolver:withRejecter:)
    func multiply(a: Float, b: Float, resolve:RCTPromiseResolveBlock,reject:RCTPromiseRejectBlock) -> Void {
        resolve(a*b)
    }
    private var model: DeepSpeechModel?
    private var stream: DeepSpeechStream?
    
    @objc(transcribeWav:withB:withC:)
    func transcribeWav(wavPath: String, modelPath: String, scorerPath: String) {
        //let modelPath = Bundle.main.path(forResource: "deepspeech-0.8.0-models", ofType: "tflite")!
        //let scorerPath = Bundle.main.path(forResource: "deepspeech-0.8.0-models", ofType: "scorer")!
        
        model = try! DeepSpeechModel(modelPath: modelPath)
        try! model!.enableExternalScorer(scorerPath: scorerPath)
        
        recognizeFile(audioPath: wavPath)
    }
    
    // MARK: Audio file recognition
    
    private func render(audioContext: AudioContext?, stream: DeepSpeechStream) {
        guard let audioContext = audioContext else {
            fatalError("Couldn't create the audioContext")
        }
        
        let sampleRange: CountableRange<Int> = 0..<audioContext.totalSamples
        
        guard let reader = try? AVAssetReader(asset: audioContext.asset)
            else {
                fatalError("Couldn't initialize the AVAssetReader")
        }
        
        reader.timeRange = CMTimeRange(start: CMTime(value: Int64(sampleRange.lowerBound), timescale: audioContext.asset.duration.timescale),
                                       duration: CMTime(value: Int64(sampleRange.count), timescale: audioContext.asset.duration.timescale))
        
        let outputSettingsDict: [String : Any] = [
            AVFormatIDKey: Int(kAudioFormatLinearPCM),
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsBigEndianKey: false,
            AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsNonInterleaved: false
        ]
        
        let readerOutput = AVAssetReaderTrackOutput(track: audioContext.assetTrack,
                                                    outputSettings: outputSettingsDict)
        readerOutput.alwaysCopiesSampleData = false
        reader.add(readerOutput)
        
        var sampleBuffer = Data()
        
        // 16-bit samples
        reader.startReading()
        defer { reader.cancelReading() }
        
        while reader.status == .reading {
            guard let readSampleBuffer = readerOutput.copyNextSampleBuffer(),
                let readBuffer = CMSampleBufferGetDataBuffer(readSampleBuffer) else {
                    break
            }
            // Append audio sample buffer into our current sample buffer
            var readBufferLength = 0
            var readBufferPointer: UnsafeMutablePointer<Int8>?
            CMBlockBufferGetDataPointer(readBuffer,
                                        atOffset: 0,
                                        lengthAtOffsetOut: &readBufferLength,
                                        totalLengthOut: nil,
                                        dataPointerOut: &readBufferPointer)
            sampleBuffer.append(UnsafeBufferPointer(start: readBufferPointer, count: readBufferLength))
            CMSampleBufferInvalidate(readSampleBuffer)
            
            let totalSamples = sampleBuffer.count / MemoryLayout<Int16>.size
            print("read \(totalSamples) samples")
            
            sampleBuffer.withUnsafeBytes { (samples: UnsafeRawBufferPointer) in
                let unsafeBufferPointer = samples.bindMemory(to: Int16.self)
                stream.feedAudioContent(buffer: unsafeBufferPointer)
            }
            
            sampleBuffer.removeAll()
        }
        
        // if (reader.status == AVAssetReaderStatusFailed || reader.status == AVAssetReaderStatusUnknown)
        guard reader.status == .completed else {
            fatalError("Couldn't read the audio file")
        }
    }
    
    private func recognizeFile(audioPath: String) {
        let url = URL(fileURLWithPath: audioPath)
        
        let stream = try! model!.createStream()
        print("\(audioPath)")
        let start = CFAbsoluteTimeGetCurrent()
        AudioContext.load(fromAudioURL: url, completionHandler: { audioContext in
            guard let audioContext = audioContext else {
                fatalError("Couldn't create the audioContext")
            }
            self.render(audioContext: audioContext, stream: stream)
            // HACK: workaround for finishWithMetadata resulting in a malloc free of a pointer already freed.
            let result = stream.intermediateDecodeWithMetadata(numResults: 1)
            let string = stream.finishStream()
            let end = CFAbsoluteTimeGetCurrent()
            print("\"\(audioPath)\": \(end - start) - (result)")
            
            
            self.sendTranscription(decoded: result, event: "onWavTranscribed")
            // Free model
            self.model = nil
        })
    }
    
    private func sendTranscription(decoded: DeepSpeechMetadata, event: String) {
        let transcriptCandidate = decoded.transcripts[0].tokens
        var words = [String]()
        var timestamps = [Int]()
        
        var workingString = ""
        var lastTimestamp = -1
        for token in transcriptCandidate {
            let text = token.text
            let timestamp = token.startTime
            if(lastTimestamp == -1){
                lastTimestamp = Int(timestamp)
            }
            if(text == " "){
                // Append timestamp, reset string, reset timestamp to -1
                words.append(workingString)
                timestamps.append(lastTimestamp)
                workingString = ""
                lastTimestamp = -1
            }else{
                workingString.append(text)
            }
        }
        words.append(workingString)
        timestamps.append(lastTimestamp)
        self.sendEvent(withName: event, body: ["words": words, "timestamps": timestamps])
        //map.putArray("words", words)
        //map.putArray("timestamps", timestamps)
        return;
    }
    
    
    override func supportedEvents() -> [String]! {
        return ["onRecordingCompletion", "onRecordingChange", "onWavTranscribed", "onWavChange"]
    }
    
    
    override static func requiresMainQueueSetup() -> Bool {
        return false
    }
}
