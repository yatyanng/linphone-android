package org.linphone.purchase;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.Purchase;

import java.util.List;

public class InAppPurchaseController {

	private final String TAG = "InAppPurchaseController";

	private boolean mYearlySub = false, mMonthlySub = false;

	private final UpdateListener mUpdateListener;
	private InAppPurchaseFragment mFragment;

	public UpdateListener getUpdateListener() {
		return mUpdateListener;
	}

	public InAppPurchaseController (InAppPurchaseFragment fragment) {
		mUpdateListener = new UpdateListener();
		mFragment = fragment;
	}

	public boolean getYearlySub() {return mYearlySub;}
	public boolean getMonthlySub() {return mMonthlySub;}


	/**
	 * Handler to billing updates
	 */
	private class UpdateListener implements InAppBillingManager.BillingUpdatesListener {
		@Override
		public void onBillingClientSetupFinished() {
			mFragment.onBillingManagerSetupFinished();
		}

		@Override
		public void onConsumeFinished(String token, @BillingClient.BillingResponse int result) {
			//We don't consume anything because we only deal with subs
		}


		@Override
		public void onPurchasesUpdated(List<Purchase> purchaseList) {
			mMonthlySub = false;
			mYearlySub = false;

			for (Purchase purchase : purchaseList) {
				switch (purchase.getSku()) {
					case InAppConstants.SKU_MONTHLY_SUB:
						mMonthlySub = true;
						break;
					case InAppConstants.SKU_YEARLY_SUB:
						mYearlySub = true;
						break;
				}
			}
			mFragment.FetchExpiration();
		}
	}

}
