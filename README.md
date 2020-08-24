# react-native-transcription

Transcribe live and (coming soon) ~~recorded~~ audio on Android and (coming soon) ~~iOS~~

## Installation

```sh
npm install react-native-transcription
```

## Usage

It's easiest to read the [example.](https://github.com/zaptrem/react-native-transcription/blob/master/example/src/App.js)

1. Download models from the Mozilla-STT repo to the local file system and save the URIs. We used RNBackgroundDownloader as the files are too large to assume a continuous session for the whole download.


```js
import Transcription from "react-native-transcription";
import { NativeEventEmitter } from 'react-native';

// Start a streaming/live transcription.
Transcription.startRecording(modelURI, scorerURI)

// Stop the transcription
Transcription.stopRecording

// Listen to changes in the live transcription
this.callThisToUnsubscribe1 = TranscriptEvents.addListener("onRecordingChange", res => {
      console.log("onRecordingChange event", res);
      var transcriptionString = "";
      for(word in res.words){
        transcriptionString = (transcriptionString + res.words[word] + " ");
      }
      this.setState({ result: transcriptionString });
    });
 
 // Listen for final transcription
 this.callThisToUnsubscribe2 = TranscriptEvents.addListener("onRecordingCompletion", res => {
      console.log("onRecordingcompletion event", res);
      var transcriptionString = "";
      for(word in res.words){
        transcriptionString = (transcriptionString + res.words[word] + " ");
      }
      this.setState({ result: transcriptionString });
    });


```

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
