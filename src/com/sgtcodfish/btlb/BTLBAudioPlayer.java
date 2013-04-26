package com.sgtcodfish.btlb;

import java.io.IOException;
import java.io.InputStream;

import android.bluetooth.BluetoothSocket;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * Streams any loaded audio data to the android device's speakers/attached headphones.
 * @author Ashley Davis (SgtCoDFish)
 */
public class BTLBAudioPlayer extends Thread {
	public static int minBufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
	
	public static int sampleRateInHz = 44100;
	public static int bufferSize = 88200;
	
	byte buffer[] = new byte[bufferSize * 10];
	boolean play = true;
	
	AudioTrack audioTrack = null;
	BluetoothSocket connection = null;
	
	public BTLBAudioPlayer(BluetoothSocket connection) {
		this.connection = connection; 
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
		if(connection != null) {
			InputStream inputStream = null;
			try {
				inputStream = connection.getInputStream();
			} catch (IOException e1) {
				Log.d("NO_IS", "Couldn't get input stream.");
				return;
			}
			
			while(play) {
				try {				
					//inputStream.read(buffer);
					//int read = inputStream.read(buffer, 0, bufferSize);
					int read = inputStream.read(buffer);
					Log.d("BYTES_READ", "Read " + read + "B");
					audioTrack.play();
					audioTrack.write(buffer, 0, read);
					
				} catch (IOException e) {
					Log.d("IO_EXCEPTION", "IO exception in AudioPlayer.run()", e);
					break;
				}
			}
		}
	}
	
	public void cease() {
		play = false;
		audioTrack.stop();
	}
}
