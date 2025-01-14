package org.antwork.test;

import junit.framework.Assert;

import org.antwork.CallActivity;
import org.antwork.LinphoneActivity;
import org.antwork.LinphoneManager;
import org.antwork.core.LinphoneCall;
import org.antwork.mediastream.Log;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

public class Transfer extends SampleTest {
	@SmallTest
	@MediumTest
	@LargeTest
	public void testACallTransfer() {
		solo.enterText(0, iContext.getString(org.antwork.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.antwork.test.R.string.account_test_calls_domain));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.antwork.R.id.call));
		
		assertCallIsCorrectlyRunning();

		solo.clickOnView(solo.getView(org.antwork.R.id.options));
		solo.clickOnView(solo.getView(org.antwork.R.id.transfer));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.enterText(0, iContext.getString(org.antwork.test.R.string.conference_account_login) + "@" + iContext.getString(org.antwork.test.R.string.conference_account_domain));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.antwork.R.id.call)); // Transfer button as the same id, only the image changes
		
		solo.sleep(2000);
		Assert.assertTrue(LinphoneTestManager.getLc(1).getCallsNb() > 0);
		Assert.assertTrue(LinphoneTestManager.getLc(2).getCallsNb() > 0);
		LinphoneTestManager.getLc(1).terminateAllCalls();
		solo.sleep(500);
		Assert.assertTrue(LinphoneTestManager.getLc(1).getCallsNb() == 0);
		Assert.assertTrue(LinphoneTestManager.getLc(2).getCallsNb() == 0);
	}
	
	private void assertCallIsCorrectlyRunning() {
		solo.waitForActivity("InCallActivity", 5000);
		solo.assertCurrentActivity("Expected InCall Activity", CallActivity.class);
		
		solo.sleep(2000);
		LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
		
		int retry = 0;
		while ((call.getState() == LinphoneCall.State.OutgoingProgress || call.getState() == LinphoneCall.State.IncomingReceived) && retry < 5) {
			solo.sleep(1000);
			retry++;
			Log.w("Call in progress but not running, retry = " + retry);
		}
		
		waitForCallState(call,LinphoneCall.State.StreamsRunning);
	}
}
