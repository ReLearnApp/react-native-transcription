package com.reactnativetranscription;

import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;

public class WavHeaderFunctions {
  public static char readLEChar(RandomAccessFile f) throws IOException {
    byte b1 = f.readByte();
    byte b2 = f.readByte();
    return (char)((b2 << 8) | b1);
  }

  public static int readLEInt(RandomAccessFile f) throws IOException {
    byte b1 = f.readByte();
    byte b2 = f.readByte();
    byte b3 = f.readByte();
    byte b4 = f.readByte();
    return (int)((b1 & 0xFF) | (b2 & 0xFF) << 8 | (b3 & 0xFF) << 16 | (b4 & 0xFF) << 24);
  }
}
