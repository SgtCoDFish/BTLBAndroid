package com.sgtcodfish.btlb;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class BTLBAndroid extends Activity {
	public final UUID BTLB_UUID = UUID.fromString("08abb94d-2b16-44e9-ae95-e2d18b614496");
	
	enum BTLBState {
		BTLB_STATE_WAITSTART,
		BTLB_STATE_SEARCHING,
		BTLB_STATE_NOBLUETOOTH,
		BTLB_STATE_CONNECTED_PLAYING,
		BTLB_STATE_CONNECTED_PAUSED
	}
	
	Button actionButton = null;
	TextView infoText = null;
	TextView deviceName= null;
	ProgressBar progressIndicator = null;
	
	boolean searchBluetooth = false;
	boolean hasBluetooth = true;
	
	BluetoothAdapter localAdapter = null;
	
	BluetoothServerSocket serverSocket = null;
	BTLBServerSocketWaiter socketWaiter = null;
	
	BluetoothSocket socket = null;
	
	BTLBState state = BTLBState.BTLB_STATE_WAITSTART;
	BTLBAudioPlayer btlbPlayer = null;
	
	/**
	 * Used to detect changes in bluetooth state.
	 * @author Ashley Davis (SgtCoDFish)
	 */
	class BTLBBluetoothBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d("BROADCAST_RECV", "Received a bluetooth broadcast.");
			if(intent.getAction().compareTo(BluetoothAdapter.ACTION_STATE_CHANGED) == 0) {
				int extra = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
				
				if(extra == BluetoothAdapter.STATE_TURNING_OFF || extra == BluetoothAdapter.STATE_OFF) {
					// the user has turned bluetooth off, we need to deal with it
					setState(BTLBState.BTLB_STATE_NOBLUETOOTH);
					Log.d("BT_STATE_CHANGE", "Bluetooth state changed: Turning off or off");
				} else if(extra == BluetoothAdapter.STATE_ON) {
					// bluetooth has come on
					Log.d("BT_STATE_CHANGE", "Bluetooth state changed: On");
					bluetoothEnabled();
					setState(BTLBState.BTLB_STATE_WAITSTART);
				}
			}
		}
	}
	
	BTLBBluetoothBroadcastReceiver bluetoothBroadcastReceiver = new BTLBBluetoothBroadcastReceiver();
	
	class BTLBNoisyBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d("BROADCAST_RECV", "Received a BECOMING_NOISY broadcast.");
			if(AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
				if(state == BTLBState.BTLB_STATE_CONNECTED_PLAYING) {
					setState(BTLBState.BTLB_STATE_CONNECTED_PAUSED);
				}
			}
		}
	}
	
	/**
	 * Waits for a socket connection from the desktop app.
	 * @author Ashley Davis (SgtCoDFish)
	 */
	class BTLBServerSocketWaiter extends AsyncTask<BluetoothServerSocket, Void, BluetoothSocket> {
		@Override
		protected BluetoothSocket doInBackground(BluetoothServerSocket... params) {
			try {
				Log.d("DIB", "In doInBackground" + params[0]);
				BluetoothSocket bluetoothSocket = params[0].accept();
				Log.d("DIB", "Socket accepted: " + bluetoothSocket.toString());
				return bluetoothSocket;
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(BluetoothSocket socket) {
			Log.d("CONNECTION_ESTABLISHED", "Connection established with remote device: "
					+ socket.getRemoteDevice().getName() + ": " + socket.getRemoteDevice().getAddress());
			socketFound(socket);
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_btlbandroid);
		actionButton = (Button)this.findViewById(R.id.cancelButton);
		infoText = (TextView) this.findViewById(R.id.infoText);
		deviceName = (TextView) this.findViewById(R.id.dev_name);
		deviceName.setVisibility(View.INVISIBLE);
		progressIndicator = (ProgressBar) this.findViewById(R.id.progressIndicator);
		
		// check that the device has bluetooth
		localAdapter = BluetoothAdapter.getDefaultAdapter();
		if(localAdapter == null) {
			hasBluetooth = false;
			setState(BTLBState.BTLB_STATE_NOBLUETOOTH);
			AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
			alertBuilder.setMessage(R.string.no_bluetooth).setTitle(R.string.no_bluetooth_title);
			AlertDialog noBTDialog = alertBuilder.create();
			noBTDialog.show();
		} else {
			// make sure we're alerted to changes in bluetooth state
			this.registerReceiver(bluetoothBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
			
			// the device has bluetooth, but is it on?
			
			if(localAdapter.isEnabled()) {
				bluetoothEnabled();
				setState(BTLBState.BTLB_STATE_WAITSTART);
			} else {
				setState(BTLBState.BTLB_STATE_NOBLUETOOTH);
			}
		}
	}
	
	@Override
	public void onDestroy() {		
		this.unregisterReceiver(bluetoothBroadcastReceiver);
		super.onDestroy();
	}
	
	public void bluetoothEnabled() {
		if(hasBluetooth && localAdapter != null) {
			deviceName.setGravity(Gravity.CENTER);
			deviceName.setText(this.getString(R.string.device_name_info) + "\n" + localAdapter.getName() + ": " + localAdapter.getAddress());
			deviceName.setVisibility(View.VISIBLE);
		}
	}
	
	/**
	 * Called to set the app into an unusable state if the device doesn't have bluetooth/has bluetooth but won't switch it on.
	 */
	public void bluetoothFail() {
		this.
		progressIndicator.setVisibility(View.INVISIBLE);
		deviceName.setVisibility(View.INVISIBLE);
		infoText.setText(R.string.needs_bluetooth);
		
		if(socketWaiter != null) {
			socketWaiter.cancel(true);
			socketWaiter = null;
			serverSocket = null;
		}
		
		if(!hasBluetooth) {
			// if we don't have bluetooth, don't allow anything to be done
			actionButton.setClickable(false);
			actionButton.setText(R.string.no_bluetooth_button);
		} else {
			// if we have bluetooth but it's off, use the action button to turn it on
			actionButton.setClickable(true);
			actionButton.setText(R.string.turn_bluetooth_on);
			actionButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
					startActivity(enableBtIntent);
				}
			});
		}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(data.getAction().compareTo(BluetoothAdapter.ACTION_REQUEST_ENABLE) == 0) {
			if(resultCode == Activity.RESULT_OK) {
				// bluetooth was turned on, happy days
				setState(BTLBState.BTLB_STATE_WAITSTART);
			} else if(resultCode == Activity.RESULT_CANCELED) {
				// they chose not to enable bluetooth, so we can't do anything
				setState(BTLBState.BTLB_STATE_NOBLUETOOTH);
			}
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_btlbandroid, menu);
		return true;
	}
	
	public void setState(BTLBState nState) {
		state = nState;
		Log.d("STATE_CHANGE", nState.toString());
		
		switch(nState) {
		case BTLB_STATE_NOBLUETOOTH:
			bluetoothFail();
			break;
		case BTLB_STATE_SEARCHING:
			startBluetoothSearch();
			break;
		case BTLB_STATE_WAITSTART:
			cancelBluetoothSearch();
			break;
			
		case BTLB_STATE_CONNECTED_PLAYING:
			connectedLoop(true);
			break;
			
		case BTLB_STATE_CONNECTED_PAUSED:
			connectedLoop(false);
			break;
		default:
			Log.d("WAT", "wat");
			break;
		}
	}
	
	public void startBluetoothSearch() {
		//Log.d("BT_SEARCH_START", "User started bluetooth search.");
		
		// change the text/button
		infoText.setText(R.string.please_wait);
		progressIndicator.setVisibility(View.VISIBLE);
		
		actionButton.setText(R.string.cancel_search);
		actionButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setState(BTLBState.BTLB_STATE_WAITSTART);
			}
		});
		
		searchBluetooth = true;
		
		try {
			serverSocket = localAdapter.listenUsingRfcommWithServiceRecord("BTLB", BTLB_UUID);
			socketWaiter = new BTLBServerSocketWaiter();
			socketWaiter.execute(serverSocket);
		} catch (IOException e) {
			Log.w("IO Fail", "Failed while trying to get connection.", e);
		}
	}
	
	public void cancelBluetoothSearch() {
		//Log.d("BT_SEARCH_CANCEL", "User cancelled bluetooth search.");
		
		// change text/button
		progressIndicator.setVisibility(View.INVISIBLE);
		if(searchBluetooth) {
			socketWaiter.cancel(true);
			serverSocket = null;
			socketWaiter = null;
		}
		
		infoText.setText(R.string.press_start);
		
		actionButton.setText(R.string.start_search);
		actionButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setState(BTLBState.BTLB_STATE_SEARCHING);
			}
		});
		
		searchBluetooth = false;
	}
	
	public void socketFound(BluetoothSocket connection) {
		socket = connection;
		Log.d("MIN_BUFFER_SIZE", "Minimum buffer size: " + AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT));
		btlbPlayer = new BTLBAudioPlayer();
		
//		try {
//			//socket.connect();
//			InputStream is = socket.getInputStream();
//			byte input[] = new byte[20];
//			is.read(input);
//			for(byte b : input) {
//				Log.d("READ", Byte.toString(b));
//			}
//			Log.d("READ", input.toString());
//		} catch(IOException ioe) {
//			Log.d("IO_EXCEPTION", "IOE trying to read from bluetooth connection", ioe);
//		}
//		
	}
	
	class BTDataReader extends Thread {
		// this is horrible i know :P
		InputStream is = null;
		BTLBAudioPlayer aP = null;
		
		byte buffer[] = new byte[100000];
		int bytesRead = 0;
		
		BTDataReader(InputStream nis, BTLBAudioPlayer naP) {
			this.is = nis;
			this.aP = naP;
			
			aP.setBuffer(buffer);
			aP.start();
		}

		@Override
		public void run() {
			try {
				bytesRead = is.read(buffer);
				Log.d("B_READ", "Bytes read:" + bytesRead);
				if(bytesRead > 90000) {
					aP.pause();
					byte bufferTemp[] = new byte[20000];
					System.arraycopy(buffer, 80000, bufferTemp, 0, 15000);
					buffer = null;
					buffer = new byte[100000];
					System.arraycopy(bufferTemp, 0, bufferTemp, 0, bufferTemp.length);
					aP.notify();
				}
			} catch (IOException e) {
				Log.d("BTDR", "BTDR IOE", e);
			}
		}
	}
	
	BTDataReader dataReader = null;
	/**
	 * The loop to run when a connection is established.
	 * @param play true if we should play music, false otherwise.
	 */
	public void connectedLoop(boolean play) {
		if(play) {
			InputStream is = null;
			
			try {
				is = socket.getInputStream();
			} catch(IOException ioe) {
				Log.d("IS_FAIL", "Couldn't get input stream.");
			}
			
			dataReader = new BTDataReader(is, btlbPlayer);
		} else {
		}
	}
}
