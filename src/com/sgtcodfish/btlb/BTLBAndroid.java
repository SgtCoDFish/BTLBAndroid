package com.sgtcodfish.btlb;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import android.bluetooth.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class BTLBAndroid extends Activity {	
	enum BTLBState {
		BTLB_STATE_WAITSTART,
		BTLB_STATE_SEARCHING,
		BTLB_STATE_NOBLUETOOTH
	}
	
	Button actionButton = null;
	TextView infoText = null;
	TextView deviceName= null;
	boolean searchBluetooth = false;
	boolean hasBluetooth = true;
	
	BluetoothAdapter localAdapter = null;
	
	BTLBState state = BTLBState.BTLB_STATE_WAITSTART;
	
	/**
	 * Used to detect changes in bluetooth state.
	 * @author Ashley Davis (SgtCoDFish)
	 */
	class BTLBBluetoothBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d("BROADCAST_RECV", "Received a broadcast.");
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
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_btlbandroid);
		actionButton = (Button)this.findViewById(R.id.cancelButton);
		infoText = (TextView) this.findViewById(R.id.infoText);
		deviceName = (TextView) this.findViewById(R.id.dev_name);
		deviceName.setVisibility(View.INVISIBLE);
		
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
		deviceName.setVisibility(View.INVISIBLE);
		infoText.setText(R.string.needs_bluetooth);
		
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
	
	public void run() {
		
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
		default:
			Log.d("WAT", "wat");
			break;
		}
	}
	
	public void startBluetoothSearch() {
		//Log.d("BT_SEARCH_START", "User started bluetooth search.");
		
		// change the text/button
		infoText.setText(R.string.please_wait);
		
		actionButton.setText(R.string.cancel_search);
		actionButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				cancelBluetoothSearch();
			}
		});
		
		searchBluetooth = true;
	}
	
	public void cancelBluetoothSearch() {
		//Log.d("BT_SEARCH_CANCEL", "User cancelled bluetooth search.");
		
		// change text/button
		infoText.setText(R.string.press_start);
		
		actionButton.setText(R.string.start_search);
		actionButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startBluetoothSearch();
			}
		});
		
		searchBluetooth = false;
	}
}
