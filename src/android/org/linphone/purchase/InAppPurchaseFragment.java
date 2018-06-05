package org.linphone.purchase;
/*
InAppPurchaseFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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

import java.util.Locale;

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.core.LinphoneProxyConfig;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import org.linphone.R;

import android.app.AlertDialog;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.util.Log;

import org.linphone.LinphoneActivity;
import org.linphone.xmlrpc.XmlRpcHelper;
import org.linphone.xmlrpc.XmlRpcListenerBase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.SkuType;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsResponseListener;

import android.content.Context;

public class InAppPurchaseFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemClickListener {

	private LayoutInflater mInflater;

	private static InAppPurchaseFragment instance;

	private static final String TAG = "InAppFragment";

	private InAppPurchaseController mInappController;

	private LinphoneActivity mActivity;

	private ImageView cancel;
	private ProgressBar inProgress;

	private List<Purchasable> mAvailableItems;

	private InAppBillingManager mBillingManager;

	private List<SkuDetails> subsDetails;

	private boolean isAccountTrial = false, isAccountExpired = false, isWrongAccount = false, anyTransaction = false;

	private ListView mInappList;


	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "BITE");
		super.onCreate(savedInstanceState);

		mActivity = LinphoneActivity.instance();

		mInappController = new InAppPurchaseController(this);

		mBillingManager = new InAppBillingManager(mActivity, mInappController.getUpdateListener());

		DisplayExpirationNotification();
		instance = this;
	}

	public void recreateBillingManager() {
		mBillingManager.destroy();
		mBillingManager = new InAppBillingManager(mActivity, mInappController.getUpdateListener());
	}

	class InAppListAdapter extends BaseAdapter {
		InAppListAdapter() {}

		public int getCount() {
			return mAvailableItems.size();
		}

		public Object getItem(int position) {
			return mAvailableItems.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(final int position, View convertView, ViewGroup parent) {
			View view = null;
			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.in_app_purchase_item, parent, false);
			}

			final Purchasable item = mAvailableItems.get(position);

			TextView itemTitle = (TextView) view.findViewById(R.id.purchase_title);
			TextView itemDesc = (TextView) view.findViewById(R.id.purchase_description);
			TextView itemPrice = (TextView) view.findViewById(R.id.purchase_price);

			itemTitle.setText(item.getTitle());
			itemDesc.setText(item.getDescription());
			itemPrice.setText(item.getPrice());

			view.setTag(item);
			return view;
		}
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();

		if (id == R.id.dialer) {
			LinphoneActivity.instance().goToDialerFragment();
		}
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mActivity.hideTabBar(true);
		mInflater = inflater;
		View view = mInflater.inflate(R.layout.in_app_fragment, container, false);
		inProgress = view.findViewById(R.id.purchaseItemsFetchInProgress);
		inProgress.setVisibility(View.VISIBLE);

		cancel = view.findViewById(R.id.dialer);
		cancel.setOnClickListener(this);

		mInappList = view.findViewById(R.id.inapp_list);
		mInappList.setVisibility(View.GONE);

		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onStop() {
		super.onStop();
	}

	@Override
	public void onDestroyView() {
		mActivity.hideTabBar(false);
		super.onDestroyView();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onDetach() {
		super.onDetach();
	}

	public void displayInappList() {
		mInappList.setVisibility(View.VISIBLE);
		inProgress.setVisibility(View.GONE);
	}

	public void hideInAppList() {
		mInappList.setVisibility(View.GONE);
		inProgress.setVisibility(View.VISIBLE);
	}

	public void buyInapp(String sku) {
		String username = getUsername();
		/*
		To be adapted to actual subscriptions. These are from coponi.
		switch (sku) {
			case (InAppConstants.SKU_YEARLY_SUB):
				if (mInappController.getMonthlySub()) {
					ArrayList<String> currentSubs = new ArrayList<>();
					currentSubs.add(InAppConstants.SKU_MONTHLY_SUB);
					mBillingManager.initiatePurchaseFlow(sku, currentSubs, SkuType.SUBS, username);
				} else {
					mBillingManager.initiatePurchaseFlow(sku, SkuType.SUBS, username);
				}
				break;

			case (InAppConstants.SKU_MONTHLY_SUB):
				if (mInappController.getYearlySub()) {
					ArrayList<String> currentSubs = new ArrayList<>();
					currentSubs.add(InAppConstants.SKU_YEARLY_SUB);
					mBillingManager.initiatePurchaseFlow(sku, currentSubs, SkuType.SUBS, username);
				} else {
					mBillingManager.initiatePurchaseFlow(sku, SkuType.SUBS, username);
				}
				break;
		}
		*/
	}

	public static InAppPurchaseFragment instance() {
		return instance;
	}

	public void FetchExpiration() {
		if(LinphoneManager.getLc().getDefaultProxyConfig() != null && LinphonePreferences.instance().getInappPopupTime() != null) {
			XmlRpcHelper helper = new XmlRpcHelper();
			helper.getAccountExpireAsync(new XmlRpcListenerBase() {
				@Override
				public void onAccountExpireFetched(String result) {
					if (result == null) {
						return;
					}
					long timestamp = Long.parseLong(result);

					Calendar calresult = Calendar.getInstance();
					calresult.setTimeInMillis(timestamp);

					long diff = timestamp - Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis();
					isAccountExpired = (diff <= 0);
					mActivity.accountIsExpired(isAccountExpired);
					FetchTrial();
				}

				@Override
				public void onError(String error) {
				}
			}, LinphonePreferences.instance().getAccountUsername(LinphonePreferences.instance().getDefaultAccountIndex()), LinphonePreferences.instance().getAccountHa1(LinphonePreferences.instance().getDefaultAccountIndex()));
		}
	}

	private void FetchTrial() {
		if(LinphoneManager.getLc().getDefaultProxyConfig() != null && LinphonePreferences.instance().getInappPopupTime() != null) {
			XmlRpcHelper helper = new XmlRpcHelper();
			helper.isTrialAccountAsync(new XmlRpcListenerBase() {
				@Override
				public void onTrialAccountFetched(boolean isTrial) {
					isAccountTrial = isTrial;
					MaybeDisplayInapps();
				}

				@Override
				public void onError(String error) {
				}
			}, LinphonePreferences.instance().getAccountUsername(LinphonePreferences.instance().getDefaultAccountIndex()), LinphonePreferences.instance().getAccountHa1(LinphonePreferences.instance().getDefaultAccountIndex()));
		}
	}

	public void DisplayExpirationNotification() {
		if(LinphoneManager.getLc().getDefaultProxyConfig() != null && LinphonePreferences.instance().getInappPopupTime() != null) {
			XmlRpcHelper helper = new XmlRpcHelper();
			helper.getAccountExpireAsync(new XmlRpcListenerBase() {
				@Override
				public void onAccountExpireFetched(String result) {
					if (result != null) {
						long timestamp = Long.parseLong(result);

						Calendar calresult = Calendar.getInstance();
						calresult.setTimeInMillis(timestamp);

						int diff = LinphoneActivity.getDiffDays(calresult, Calendar.getInstance());
						mActivity.forceDisplayInappNotification(LinphoneActivity.instance().timestampToHumanDate(calresult));
					}
				}

				@Override
				public void onError(String error) {
				}
			}, LinphonePreferences.instance().getAccountUsername(LinphonePreferences.instance().getDefaultAccountIndex()), LinphonePreferences.instance().getAccountHa1(LinphonePreferences.instance().getDefaultAccountIndex()));
		}
	}

	void onBillingManagerSetupFinished() {
		getSkusDetails();
	}

	void getSkusDetails() {
		mBillingManager.querySkuDetailsAsync(SkuType.SUBS, InAppConstants.getSkuList(), new SkuDetailsResponseListener() {
			@Override
			public void onSkuDetailsResponse(int responseCode, List<SkuDetails> skuDetailsList) {
				if (responseCode != BillingClient.BillingResponse.OK) {
					Log.w(TAG,"Unsuccessful query for type: subs"
							+ ". Error code: " + responseCode);
				} else if (skuDetailsList != null
						&& skuDetailsList.size() > 0) {
					subsDetails = new ArrayList<>();
					for (SkuDetails item : skuDetailsList) {
						subsDetails.add(item);
					}
					populateAvailableItems();
				}

			}
		});
	}

	public void populateAvailableItems() {
		mAvailableItems = new ArrayList<>();
		for (int i = 0; i < subsDetails.size(); ++i) {
			SkuDetails item = subsDetails.get(i);
			Purchasable p = new Purchasable(item.getSku()).setDescription(item.getDescription()).setPrice(item.getPrice()).setTitle(item.getTitle());
			mAvailableItems.add(p);
		}
		if(mAvailableItems != null){
			mInappList.setAdapter(new InAppListAdapter());
			mInappList.setOnItemClickListener(this);
		}
	}

	public void MaybeDisplayInapps() {
		Log.d(TAG, "isWrongAccount: " + isWrongAccount);
		Log.d(TAG, "anyTransaction: " + anyTransaction);
		Log.d(TAG, "isAccountExpired: " + isAccountExpired);
		if ((anyTransaction && !isWrongAccount) || (!anyTransaction && isAccountExpired)) {
			displayInappList();
		} else {
			DisplayInappNotDisplayedPopup();
		}
	}

	void DisplayInappNotDisplayedPopup() {
		inProgress.setVisibility(View.GONE);
		AlertDialog.Builder infoPopupBuilder = new AlertDialog.Builder(mActivity);
		infoPopupBuilder.setTitle("Cannot Display In-app Purchases");
		if (isWrongAccount) { // Not logged in with the right lockfone account
			infoPopupBuilder.setMessage("You have an active subscription for Lockfone on a different Lockfone account.");
		} else { // no transaction but subscription active, ie either wrong Google account or subscription has been canceled.
			infoPopupBuilder.setMessage("Your account is active but you have no current subscription for Lockfone on your Google Account.\n" +
					"If you cancelled your subscription, wait until the end of the subscription.\nIf not, you are logged in the wrong Google account.");
		}infoPopupBuilder.setCancelable(false);
		infoPopupBuilder.setNeutralButton("Ok", null);
		infoPopupBuilder.show();
	}

	public String getUsername() {
		String username = LinphonePreferences.instance().getAccountUsername(LinphonePreferences.instance().getDefaultAccountIndex());
		LinphoneProxyConfig lpc = LinphoneManager.getLc().createProxyConfig();
		username = lpc.normalizePhoneNumber(username);
		return username.toLowerCase(Locale.getDefault());
	}

	public void setIsWrongAccount(boolean b) {
		isWrongAccount = b;
	}

	public void setAnyTransaction(boolean anyTransaction) {
		this.anyTransaction = anyTransaction;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		Purchasable item = (Purchasable) view.getTag();
		buyInapp(item.getId());
	}
}