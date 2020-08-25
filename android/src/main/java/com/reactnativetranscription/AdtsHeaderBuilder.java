package com.reactnativetranscription;

import android.media.MediaCodecInfo;

public class AdtsHeaderBuilder {
  public static byte[] createAdtsHeader(int length, int SAMPLE_RATE_INDEX, int CHANNELS) {
    int frameLength = length + 7;
    byte[] adtsHeader = new byte[7];

    adtsHeader[0] = (byte) 0xFF; // Sync Word
    adtsHeader[1] = (byte) 0xF1; // MPEG-4, Layer (0), No CRC
    adtsHeader[2] = (byte) ((MediaCodecInfo.CodecProfileLevel.AACObjectLC - 1) << 6);
    adtsHeader[2] |= (((byte) SAMPLE_RATE_INDEX) << 2);
    adtsHeader[2] |= (((byte) CHANNELS) >> 2);
    adtsHeader[3] = (byte) (((CHANNELS & 3) << 6) | ((frameLength >> 11) & 0x03));
    adtsHeader[4] = (byte) ((frameLength >> 3) & 0xFF);
    adtsHeader[5] = (byte) (((frameLength & 0x07) << 5) | 0x1f);
    adtsHeader[6] = (byte) 0xFC;

    return adtsHeader;
  }
}
