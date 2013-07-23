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
	public static int minBufferSize = AudioTrack.getMinBufferSize(22500, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
	
	public static int sampleRateInHz = 22500;
	public static int bufferSize = minBufferSize;
	
	boolean play = true;
	boolean paused = false;
	
	AudioTrack audioTrack = null;
	BluetoothSocket connection = null;
	
	class BTLBBluetoothReceiver extends Thread {
		byte []buffer = null;
		int read = 0;
		boolean doRead = true;
		int buffSize = minBufferSize;
		boolean beingRead = false;
		
		@Override
		public void run() {
			InputStream inputStream = null;
			buffer = new byte[buffSize*2];
			
			try {
				inputStream = connection.getInputStream();
			} catch(Exception e) {
				Log.d("IOE", "Couldn't get input stream.", e);
				return;
			}
			
			int playCount = 0;
			
			while(doRead) {
				if(!beingRead) {
					try {
						if(read >= (buffSize)) {
							playCount++;
							if(playCount > 100) {
								Log.d("PLAY_BUFF", "Playing buffer size:" + read);
								playCount = 0;
							}
							playBuffer(buffer);
							read = 0;
						}
						read += inputStream.read(buffer, read, minBufferSize);
					} catch (IOException e) {
						Log.d("CONN_INT", "Connection interrupted: ",e);
						return;
					}
				}
			}
		}
//		
//		public int getRead() {
//			if(buffer != null) {
//				return read;
//			}
//			
//			return -1;
//		}
//		
//		public byte[] getBuffer() {
//			Log.d("BYTES_READ", read + " bytes read before buffer obtained.");
//			beingRead = true;
//			read = 0;
//			byte[] clone = buffer.clone();
//			buffer = new byte[buffSize];
//			beingRead = false;
//			return clone;
//		}
		
		public void cease() {
			doRead = false;
		}
	}
	
	BTLBBluetoothReceiver receiver = null;
	
	public BTLBAudioPlayer(BluetoothSocket connection) {
		this.connection = connection; 
	}
	
	@Override
	public void start() {
		super.start();
	}

	@Override
	public void run() {
		if(connection != null) {
			audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 22500, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
			receiver = new BTLBBluetoothReceiver();
			receiver.start();
			audioTrack.play();
			while(true) {
				if(play) {
					if(!paused) {
						if(audioTrack.getState() == AudioTrack.PLAYSTATE_PLAYING) {
							audioTrack.pause();
							audioTrack.flush();
						}
					} else {
						if(audioTrack.getState() != AudioTrack.PLAYSTATE_PLAYING) {
							audioTrack.play();
						}
					}
				}
			} 
		}
	}
	
	public void playBuffer(byte []buffer) {
		audioTrack.write(buffer, 0, buffer.length);
	}
	
	public void setPaused(boolean pause) {
		paused = pause;
	}
	
	public void cease() {
		receiver.cease();
		play = false;
		audioTrack.stop();
	}
}
