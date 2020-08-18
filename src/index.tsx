import { NativeModules } from 'react-native';

type TranscriptionType = {
  multiply(a: number, b: number): Promise<number>;
};

const { Transcription } = NativeModules;

export default Transcription as TranscriptionType;
