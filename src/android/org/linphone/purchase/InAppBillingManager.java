package org.linphone.purchase;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponse;
import com.android.billingclient.api.BillingClient.FeatureType;
import com.android.billingclient.api.BillingClient.SkuType;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.Purchase.PurchasesResult;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.linphone.LinphonePreferences;
import org.linphone.xmlrpc.XmlRpcHelper;
import org.linphone.xmlrpc.XmlRpcListenerBase;

public class InAppBillingManager implements PurchasesUpdatedListener {
// Default value of mBillingClientResponseCode until BillingManager was not yeat initialized
    public static final int BILLING_MANAGER_NOT_INITIALIZED  = -1;

    private static final String TAG = "BillingManager";

    /** A reference to BillingClient **/
    private BillingClient mBillingClient;

    /**
     * True if billing service is connected now.
     */
    private boolean mIsServiceConnected;

    private final BillingUpdatesListener mBillingUpdatesListener;

    private final Activity mActivity;

    private Set<String> mTokensToBeConsumed;

    private int mBillingClientResponseCode = BILLING_MANAGER_NOT_INITIALIZED;

    /**
     * Listener to the updates that happen when purchases list was updated or consumption of the
     * item was finished
     */
    public interface BillingUpdatesListener {
        void onBillingClientSetupFinished();
        void onConsumeFinished(String token, @BillingResponse int result);
        void onPurchasesUpdated(List<Purchase> purchases);
    }

    public InAppBillingManager(Activity activity, final BillingUpdatesListener updatesListener) {
        Log.d(TAG, "Creating Billing client.");
        
        mActivity = activity;
        mBillingUpdatesListener = updatesListener;
        mBillingClient = BillingClient.newBuilder(mActivity).setListener(this).build();

        Log.d(TAG, "Starting setup.");

        // Start setup. This is asynchronous and the specified listener will be called
        // once setup completes.
        // It also starts to report all the new purchases through onPurchasesUpdated() callback.
        startServiceConnection(new Runnable() {
            @Override
            public void run() {
                // Notifying the listener that billing client is ready
                mBillingUpdatesListener.onBillingClientSetupFinished();
                // IAB is fully set up. Now, let's get an inventory of stuff we own.
                Log.d(TAG, "Setup successful. Querying inventory.");
                queryPurchases();
            }
        });
    }

    /**
     * Handle a callback that purchases were updated from the Billing library
     */
    @Override
    public void onPurchasesUpdated(int resultCode, List<Purchase> purchases) {
        if (resultCode == BillingResponse.OK) {
			Log.d(TAG, "LIST OF PURCHASES:");
            for (Purchase purchase : purchases) {
            	Log.d(TAG, purchase.toString());
                handlePurchase(purchase);
            }
        } else if (resultCode == BillingResponse.USER_CANCELED) {
            Log.i(TAG, "onPurchasesUpdated() - user cancelled the purchase flow - skipping");
        } else {
            Log.w(TAG, "onPurchasesUpdated() got unknown resultCode: " + resultCode);
        }
    }

    /**
     * Start a purchase flow
     */
    public void initiatePurchaseFlow(final String skuId, final @SkuType String billingType,String username) {
        initiatePurchaseFlow(skuId, null, billingType, username);
    }

    /**
     * Start a purchase or subscription replace flow
     */
    public void initiatePurchaseFlow(final String skuId, final ArrayList<String> oldSkus,
									 final @SkuType String billingType, final String username) {
        Runnable purchaseFlowRequest = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Launching in-app purchase flow. Replace old SKU? " + (oldSkus != null));
                BillingFlowParams purchaseParams = BillingFlowParams.newBuilder()
                        .setSku(skuId).setType(billingType).setOldSkus(oldSkus).setAccountId(username).build();
                mBillingClient.launchBillingFlow(mActivity, purchaseParams);
            }
        };

        executeServiceRequest(purchaseFlowRequest);
    }

    public Context getContext() {
        return mActivity;
    }

    /**
     * Clear the resources
     */
    public void destroy() {
        Log.d(TAG, "Destroying the manager.");

        if (mBillingClient != null && mBillingClient.isReady()) {
            mBillingClient.endConnection();
            mBillingClient = null;
        }
    }

    public void querySkuDetailsAsync(@SkuType final String itemType, final List<String> skuList,
                                     final SkuDetailsResponseListener listener) {
        // Creating a runnable from the request to use it inside our connection retry policy below
        Runnable queryRequest = new Runnable() {
            @Override
            public void run() {
                // Query the purchase async
                SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
                params.setSkusList(skuList).setType(itemType);
                mBillingClient.querySkuDetailsAsync(params.build(),
                        new SkuDetailsResponseListener() {
                            @Override
                            public void onSkuDetailsResponse(int responseCode,
                                                             List<SkuDetails> skuDetailsList) {
                                listener.onSkuDetailsResponse(responseCode, skuDetailsList);
                            }
                        });
            }
        };

        executeServiceRequest(queryRequest);
    }

    /**
     * Handles the purchase
     * @param purchase Purchase to be handled
     */
    private void handlePurchase(Purchase purchase) {
		verifyValidSignature(purchase);
	}

	private void signatureVerified(final Purchase purchase, boolean verified) {
        if (!verified) {
            Log.i(TAG, "Got a purchase: " + purchase + "; but signature is bad. Skipping...");
            return;
        }

        Log.d(TAG, "Got a verified purchase: " + purchase);
		XmlRpcHelper xmlRpcHelper = new XmlRpcHelper();
		/*
		This changes the expiration. The signature is inside the original json and is checked on the server side (again).
		xmlRpcHelper.newUpdateAccountExpireAsync(new XmlRpcListenerBase() {
			@Override
			public void onAccountExpireUpdated(String result) {
				InAppPurchaseFragment.instance().recreateBillingManager();
			}
		}, LinphonePreferences.instance().getAccountUsername(0), LinphonePreferences.instance().getAccountHa1(0), "lockfone.com", purchase.getOriginalJson());
		*/
        InAppPurchaseFragment.instance().hideInAppList();
    }

    /**
     * Handle a result from querying of purchases and report an updated list to the listener
     */
    public void onQueryPurchasesFinished(PurchasesResult result) {
    	if (result.getResponseCode() != BillingResponse.OK) {
    		return;
		}
		ArrayList<Purchase> purchases = new ArrayList<>();
    	for (Purchase p : result.getPurchasesList()) {
    		if (!p.isAutoRenewing()) {
    			continue;
			}
			purchases.add(p);
		}
		if (purchases.isEmpty()) {
			InAppPurchaseFragment.instance().setAnyTransaction(false);
			InAppPurchaseFragment.instance().FetchExpiration();
			return;
		}
		if (purchases.size() > 1) {
    		Log.d(TAG, "MORE THAN ONE RENEWING PURCHASE, NOT POSSIBLE, CONTACT ADMINISTRATOR");
    		return;
		}
		Log.d(TAG, "Only one purchase, everything is normal.");
		InAppPurchaseFragment.instance().setAnyTransaction(true);
		GetUserOfTransaction(purchases.get(0));
    }

    /**
     * Checks if subscriptions are supported for current client
     * <p>Note: This method does not automatically retry for RESULT_SERVICE_DISCONNECTED.
     * It is only used in unit tests and after queryPurchases execution, which already has
     * a retry-mechanism implemented.
     * </p>
     */
    public boolean areSubscriptionsSupported() {
        int responseCode = mBillingClient.isFeatureSupported(FeatureType.SUBSCRIPTIONS);
        if (responseCode != BillingResponse.OK) {
            Log.w(TAG, "areSubscriptionsSupported() got an error response: " + responseCode);
        }
        return responseCode == BillingResponse.OK;
    }

    /**
     * Query purchases across various use cases and deliver the result in a formalized way through
     * a listener
     */
    public void queryPurchases() {
		Runnable queryToExecute = new Runnable() {
			@Override
			public void run() {
				long time = System.currentTimeMillis();
				PurchasesResult purchasesResult = mBillingClient.queryPurchases(SkuType.INAPP);
				Log.i(TAG, "Querying purchases elapsed time: " + (System.currentTimeMillis() - time)
						+ "ms");
				// If there are subscriptions supported, we add subscription rows as well
				if (areSubscriptionsSupported()) {
					PurchasesResult subscriptionResult
							= mBillingClient.queryPurchases(SkuType.SUBS);
					Log.i(TAG, "Querying purchases and subscriptions elapsed time: "
							+ (System.currentTimeMillis() - time) + "ms");
					Log.i(TAG, "Querying subscriptions result code: "
							+ subscriptionResult.getResponseCode()
							+ " res: " + subscriptionResult.getPurchasesList().size());

					if (subscriptionResult.getResponseCode() == BillingResponse.OK) {
						purchasesResult.getPurchasesList().addAll(
								subscriptionResult.getPurchasesList());
					} else {
						Log.e(TAG, "Got an error response trying to query subscription purchases");
					}
				} else if (purchasesResult.getResponseCode() == BillingResponse.OK) {
					Log.i(TAG, "Skipped subscription purchases query since they are not supported");
				} else {
					Log.w(TAG, "queryPurchases() got an error response code: "
							+ purchasesResult.getResponseCode());
				}
				onQueryPurchasesFinished(purchasesResult);
			}
		};

		executeServiceRequest(queryToExecute);
    }

    public void startServiceConnection(final Runnable executeOnSuccess) {
        mBillingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@BillingResponse int billingResponseCode) {
                Log.d(TAG, "Setup finished. Response code: " + billingResponseCode);

                if (billingResponseCode == BillingResponse.OK) {
                    mIsServiceConnected = true;
                    if (executeOnSuccess != null) {
                        executeOnSuccess.run();
                    }
                }
                mBillingClientResponseCode = billingResponseCode;
            }

            @Override
            public void onBillingServiceDisconnected() {
                mIsServiceConnected = false;
            }
        });
    }

    private void executeServiceRequest(Runnable runnable) {
        if (mIsServiceConnected) {
            runnable.run();
        } else {
            // If billing service was disconnected, we try to reconnect 1 time.
            // (feel free to introduce your retry policy here).
            startServiceConnection(runnable);
        }
    }

    /**
     * Verifies that the purchase was signed correctly for this developer's public key.
     * <p>Note: It's strongly recommended to perform such check on your backend since hackers can
     * replace this method with "constant true" if they decompile/rebuild your app.
     * </p>
     */
    private void verifyValidSignature(final Purchase p) {
		XmlRpcHelper helper = new XmlRpcHelper();
		helper.verifySignatureAsync(new XmlRpcListenerBase() {
			@Override
			public void onSignatureVerified(boolean verified) {
				signatureVerified(p, verified);
			}

			@Override
			public void onError(String error) {

			}
		} ,p.getOriginalJson(), p.getSignature());
    }


	private void GetUserOfTransaction(final Purchase p) {
		XmlRpcHelper helper = new XmlRpcHelper();
		/*
		This requires a transaction database that holds the id of the user for each transaction.
		helper.getUserOfTransactionAsync(new XmlRpcListenerBase() {
			@Override
			public void onUserOfTransactionFetched(String user) {
				Log.d(TAG, "Fetched username: " + user + ", current username: " + InAppPurchaseFragment.instance().getUsername());
				if ( !user.equals(InAppPurchaseFragment.instance().getUsername())) {
					InAppPurchaseFragment.instance().setIsWrongAccount(true);
					InAppPurchaseFragment.instance().FetchExpiration();
					Log.e(TAG, "WRONG USERNAME FOR PURCHASE.");
					return;
				}
				InAppPurchaseFragment.instance().setIsWrongAccount(false);
				Log.d(TAG, "Right Username, proceeding.");
				ArrayList<Purchase> purchases = new ArrayList<>();
				purchases.add(p);
				mBillingUpdatesListener.onPurchasesUpdated(purchases);
			}

			@Override
			public void onError(String error) {
			}
		}, p.getOriginalJson(), p.getSignature());
		*/
	}

}
