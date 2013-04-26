package com.sgtcodfish.btlb;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Streams any loaded audio data to the android device's speakers/attached headphones.
 * @author Ashley Davis (SgtCoDFish)
 */
public class BTLBAudioPlayer extends Thread {
	public static int minBufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
	
	public static int sampleRateInHz = 44100;
	public static int bufferSize = 88200;
	
	byte buffer[] = null;
	
	AudioTrack audioTrack = null;
	
	public BTLBAudioPlayer() {
	}
	
	@Override
	public void start() {
		if(buffer == null) {
			throw new IllegalStateException("Tried to start BTLBAudioPlayer thread with null buffer. Call setBuffer(byte[]) first.");
		}
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, 88200, AudioTrack.MODE_STREAM);
		super.start();
	}

	@Override
	public void run() {
		if(buffer.length > 0) {
			audioTrack.write(buffer, 0, buffer.length);
		}
	}
	
	public void setBuffer(byte[] nbuffer) {
		if(nbuffer.length < minBufferSize) {
			throw new IllegalArgumentException("Call to setBuffer(byte[]) with buffer too small! Must be at least " + minBufferSize);
		}
		
		this.buffer = nbuffer;
	}
	
	public void pause() {
		try {
			this.wait();
		} catch (InterruptedException ie) {
			
		}
	}
}
