# react-native-transcription

Transcribe live and recorded audio on Android and iOS (live audio not currently available for iOS)

## Installation

```sh
npm install react-native-transcription
```
### iOS

Mozilla DeepSpeech is only available as a Dynamic Framework, so we have to use a cocoapods plugin to avoid using `use_frameworks!` which breaks many other libraries.

Run:
``` Bash
$ sudo gem install cocoapods-user-defined-build-types
```

at the top of your podfile, add 

```ruby
plugin 'cocoapods-user-defined-build-types'
enable_user_defined_build_types!
```

then add the build_type tag to the react-native-transcription pod in your podfile.
```ruby
  pod 'react-native-transcription', :build_type => :dynamic_framework, :path => 'THIS/IS/DIFFERENT/FOR/YOU'
```

then run `pod install`.

Then open your project in Xcode and add the libdeepspeech.so (you can find it either in the DeepSpeech repo releases or in node_modules/react-native-transcription/ios/Frameworks) file to your Target's "Frameworks, Libraries, and Embedded Content" section: 
![Screen Shot 2020-08-26 at 8 17 36 PM](https://user-images.githubusercontent.com/1612230/91369225-459c1d80-e7d9-11ea-86e2-f535fe65cd2e.png)


## Example App

To get started with the example project, run `yarn bootstrap` in the root directory to install the required dependencies for each package:

```sh
yarn bootstrap
```

Run the [example app](/example/) on your preferred platform.

To run the example app on Android:

```sh
yarn example android
```

To run the example app on iOS:

```sh
yarn example ios
```

## Usage

It's easiest to read the [example's code.](https://github.com/zaptrem/react-native-transcription/blob/master/example/src/App.js)

1. Download models from the Mozilla-STT repo to the local file system and save the URIs. We used RNBackgroundDownloader as the files are too large to assume a continuous session for the whole download.


```js
import Transcription from "react-native-transcription";
import { NativeEventEmitter } from 'react-native';

// Transcribe a .wav file
Transcription.transcribeWav(wavFileURI, modelURI, scorerURI)

// Start a streaming/live transcription.
Transcription.startRecording(aacFileURI, modelURI, scorerURI)

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
