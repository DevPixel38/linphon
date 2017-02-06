package org.antwork.mini;
/*
LinphoneMiniManager.java
Copyright (C) 2014  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.antwork.core.LinphoneAddress;
import org.antwork.core.LinphoneCall;
import org.antwork.core.LinphoneCall.State;
import org.antwork.core.LinphoneCallStats;
import org.antwork.core.LinphoneChatMessage;
import org.antwork.core.LinphoneChatRoom;
import org.antwork.core.LinphoneContent;
import org.antwork.core.LinphoneCore;
import org.antwork.core.LinphoneCore.EcCalibratorStatus;
import org.antwork.core.LinphoneCore.GlobalState;
import org.antwork.core.LinphoneCore.RegistrationState;
import org.antwork.core.LinphoneCore.RemoteProvisioningState;
import org.antwork.core.LinphoneCoreException;
import org.antwork.core.LinphoneCoreFactory;
import org.antwork.core.LinphoneCoreListener;
import org.antwork.core.LinphoneEvent;
import org.antwork.core.LinphoneFriend;
import org.antwork.core.LinphoneInfoMessage;
import org.antwork.core.LinphoneProxyConfig;
import org.antwork.core.PublishState;
import org.antwork.core.SubscriptionState;
import org.antwork.mediastream.Log;
import org.antwork.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.antwork.mediastream.video.capture.hwconf.AndroidCameraConfiguration.AndroidCamera;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;

/**
 * @author Sylvain Berfini
 */
public class LinphoneMiniManager implements LinphoneCoreListener {
	private static LinphoneMiniManager mInstance;
	private Context mContext;
	private LinphoneCore mLinphoneCore;
	private Timer mTimer;
	
	public LinphoneMiniManager(Context c) {
		mContext = c;
		LinphoneCoreFactory.instance().setDebugMode(true, "Linphone Mini");
		
		try {
			String basePath = mContext.getFilesDir().getAbsolutePath();
			copyAssetsFromPackage(basePath);
			mLinphoneCore = LinphoneCoreFactory.instance().createLinphoneCore(this, basePath + "/.linphonerc", basePath + "/linphonerc", null, mContext);
			initLinphoneCoreValues(basePath);
			
			setUserAgent();
			setFrontCamAsDefault();
			startIterate();
			mInstance = this;
	        mLinphoneCore.setNetworkReachable(true); // Let's assume it's true
		} catch (LinphoneCoreException e) {
		} catch (IOException e) {
		}
	}
	
	public static LinphoneMiniManager getInstance() {
		return mInstance;
	}
	
	public void destroy() {
		try {
			mTimer.cancel();
			mLinphoneCore.destroy();
		}
		catch (RuntimeException e) {
		}
		finally {
			mLinphoneCore = null;
			mInstance = null;
		}
	}
	
	private void startIterate() {
		TimerTask lTask = new TimerTask() {
			@Override
			public void run() {
				mLinphoneCore.iterate();
			}
		};
		
		/*use schedule instead of scheduleAtFixedRate to avoid iterate from being call in burst after cpu wake up*/
		mTimer = new Timer("LinphoneMini scheduler");
		mTimer.schedule(lTask, 0, 20); 
	}
	
	private void setUserAgent() {
		try {
			String versionName = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName;
			if (versionName == null) {
				versionName = String.valueOf(mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionCode);
			}
			mLinphoneCore.setUserAgent("LinphoneMiniAndroid", versionName);
		} catch (NameNotFoundException e) {
		}
	}
	
	private void setFrontCamAsDefault() {
		int camId = 0;
		AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
		for (AndroidCamera androidCamera : cameras) {
			if (androidCamera.frontFacing)
				camId = androidCamera.id;
		}
		mLinphoneCore.setVideoDevice(camId);
	}
	
	private void copyAssetsFromPackage(String basePath) throws IOException {
		LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.oldphone_mono, basePath + "/oldphone_mono.wav");
		LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.ringback, basePath + "/ringback.wav");
		LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.toy_mono, basePath + "/toy_mono.wav");
		LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.linphonerc_default, basePath + "/.linphonerc");
		LinphoneMiniUtils.copyFromPackage(mContext, R.raw.linphonerc_factory, new File(basePath + "/linphonerc").getName());
		LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.lpconfig, basePath + "/lpconfig.xsd");
		LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.rootca, basePath + "/rootca.pem");
	}
	
	private void initLinphoneCoreValues(String basePath) {
		mLinphoneCore.setContext(mContext);
		mLinphoneCore.setRing(null);
		mLinphoneCore.setRootCA(basePath + "/rootca.pem");
		mLinphoneCore.setPlayFile(basePath + "/toy_mono.wav");
		mLinphoneCore.setChatDatabasePath(basePath + "/linphone-history.db");
		
		int availableCores = Runtime.getRuntime().availableProcessors();
		mLinphoneCore.setCpuCount(availableCores);
	}
	
	@Override
	public void authInfoRequested(LinphoneCore lc, String realm, String username) {
		
	}

	@Override
	public void globalState(LinphoneCore lc, GlobalState state, String message) {
		Log.d("Global state: " + state + "(" + message + ")");
	}

	@Override
	public void callState(LinphoneCore lc, LinphoneCall call, State cstate,
			String message) {
		Log.d("Call state: " + cstate + "(" + message + ")");
	}

	@Override
	public void callStatsUpdated(LinphoneCore lc, LinphoneCall call,
			LinphoneCallStats stats) {
		
	}

	@Override
	public void callEncryptionChanged(LinphoneCore lc, LinphoneCall call,
			boolean encrypted, String authenticationToken) {
		
	}

	@Override
	public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg,
			RegistrationState cstate, String smessage) {
		Log.d("Registration state: " + cstate + "(" + smessage + ")");
	}

	@Override
	public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf,
			String url) {
		
	}

	@Override
	public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {
		
	}

	@Override
	public void textReceived(LinphoneCore lc, LinphoneChatRoom cr,
			LinphoneAddress from, String message) {
		
	}

	@Override
	public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr,
			LinphoneChatMessage message) {
		Log.d("Message received from " + cr.getPeerAddress().asString() + " : " + message.getText() + "(" + message.getExternalBodyUrl() + ")");
	}

	@Override
	public void isComposingReceived(LinphoneCore lc, LinphoneChatRoom cr) {
		Log.d("Composing received from " + cr.getPeerAddress().asString());
	}

	@Override
	public void dtmfReceived(LinphoneCore lc, LinphoneCall call, int dtmf) {
		
	}

	@Override
	public void ecCalibrationStatus(LinphoneCore lc, EcCalibratorStatus status,
			int delay_ms, Object data) {
		
	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneCall call,
			LinphoneAddress from, byte[] event) {
		
	}

	@Override
	public void transferState(LinphoneCore lc, LinphoneCall call,
			State new_call_state) {
		
	}

	@Override
	public void infoReceived(LinphoneCore lc, LinphoneCall call,
			LinphoneInfoMessage info) {
		
	}

	@Override
	public void subscriptionStateChanged(LinphoneCore lc, LinphoneEvent ev,
			SubscriptionState state) {
		
	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneEvent ev,
			String eventName, LinphoneContent content) {
		Log.d("Notify received: " + eventName + " -> " + content.getDataAsString());
	}

	@Override
	public void publishStateChanged(LinphoneCore lc, LinphoneEvent ev,
			PublishState state) {
		
	}

	@Override
	public void configuringStatus(LinphoneCore lc,
			RemoteProvisioningState state, String message) {
		Log.d("Configuration state: " + state + "(" + message + ")");
	}

	@Override
	public void show(LinphoneCore lc) {
		
	}

	@Override
	public void displayStatus(LinphoneCore lc, String message) {
		
	}

	@Override
	public void displayMessage(LinphoneCore lc, String message) {
		
	}

	@Override
	public void displayWarning(LinphoneCore lc, String message) {
		
	}

}
