/*
 * Copyright (c) 2018 ThanksMister LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.thanksmister.bitcoin.localtrader.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.NetworkOnMainThreadException;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.android.IntentIntegrator;
import com.thanksmister.bitcoin.localtrader.Injector;
import com.thanksmister.bitcoin.localtrader.R;
import com.thanksmister.bitcoin.localtrader.data.database.DbManager;
import com.thanksmister.bitcoin.localtrader.events.AlertDialogEvent;
import com.thanksmister.bitcoin.localtrader.events.ConfirmationDialogEvent;
import com.thanksmister.bitcoin.localtrader.events.ProgressDialogEvent;
import com.thanksmister.bitcoin.localtrader.network.NetworkConnectionException;
import com.thanksmister.bitcoin.localtrader.network.NetworkException;
import com.thanksmister.bitcoin.localtrader.network.api.model.RetroError;
import com.thanksmister.bitcoin.localtrader.network.services.DataService;
import com.thanksmister.bitcoin.localtrader.network.services.DataServiceUtils;
import com.thanksmister.bitcoin.localtrader.network.services.SyncUtils;
import com.thanksmister.bitcoin.localtrader.ui.activities.PromoActivity;
import com.thanksmister.bitcoin.localtrader.utils.AuthUtils;
import com.trello.rxlifecycle.components.support.RxAppCompatActivity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.net.ssl.SSLHandshakeException;

import dpreference.DPreference;
import retrofit.RetrofitError;
import rx.functions.Action0;
import timber.log.Timber;

/**
 * Base activity which sets up a per-activity object graph and performs injection.
 */
public abstract class BaseActivity extends RxAppCompatActivity {
    /**
     * This activity requires authentication
     */
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface RequiresAuthentication {
    }

    @Inject
    protected DbManager dbManager;

    @Inject
    protected DataService dataService;

    @Inject
    protected SharedPreferences sharedPreferences;

    @Inject
    protected DPreference preference;

    private AlertDialog progressDialog;
    private AlertDialog alertDialog;
    private Snackbar snackBar;
    private HashMap<String, Boolean> syncMap = new HashMap<>(); // init sync map

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Injector.inject(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
            alertDialog = null;
        }

        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }

        if (snackBar != null && snackBar.isShownOrQueued()) {
            snackBar.dismiss();
            snackBar = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            unregisterReceiver(connReceiver);
        } catch (IllegalArgumentException e) {
            Timber.e(e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(connReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    /**
     * Keep a map of all syncing calls to update sync status and
     * broadcast when no more syncs running
     *
     * @param key
     * @param value
     */
    public void updateSyncMap(String key, boolean value) {
        Timber.d("updateSyncMap: " + key + " value: " + value);
        syncMap.put(key, value);
        if (!isSyncing()) {
            resetSyncing();
        }
    }

    /**
     * Prints the sync map for debugging
     */
    public void printSyncMap() {
        for (Object o : syncMap.entrySet()) {
            Map.Entry pair = (Map.Entry) o;
            Timber.d("Sync Map>>>>>> " + pair.getKey() + " = " + pair.getValue());
        }
    }

    /**
     * Checks if any active syncs are going one
     *
     * @return
     */
    public boolean isSyncing() {
        printSyncMap();
        Timber.d("isSyncing: " + syncMap.containsValue(true));
        return syncMap.containsValue(true);
    }

    /**
     * Resets the syncing map
     */
    private void resetSyncing() {
        syncMap = new HashMap<>();
    }
    /**
     * Called when network is disconnected
     */
    protected void handleNetworkDisconnect() {
        // override to in views to handle network disconnect
    }

    /**
     * Handle the network sync the first time
     */
    protected void handleSyncComplete() {

    }

    /**
     * Called from <code>SnackBar</code> when refresh button is pressed
     */
    protected void handleRefresh() {
        // override to in views to handle refresh
    }

    public void launchScanner() {
        IntentIntegrator scanIntegrator = new IntentIntegrator(BaseActivity.this);
        scanIntegrator.initiateScan(IntentIntegrator.QR_CODE_TYPES);
    }

    public static void hideSoftKeyboard(Activity activity) {
        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (activity.getCurrentFocus() != null && activity.getCurrentFocus().getWindowToken() != null) {
            try {
                inputMethodManager.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
            } catch (NullPointerException e) {
                Timber.e(e.getMessage());
            }
        }
    }

    public void showProgressDialog(ProgressDialogEvent event) {
        showProgressDialog(event, false);
    }

    public void showProgressDialog(ProgressDialogEvent event, boolean cancelable) {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
            return;
        }

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View dialogView = inflater.inflate(R.layout.dialog_progress, null, false);
        TextView progressDialogMessage = (TextView) dialogView.findViewById(R.id.progressDialogMessage);
        progressDialogMessage.setText(event.message);

        progressDialog = new AlertDialog.Builder(BaseActivity.this, R.style.DialogTheme)
                .setCancelable(cancelable)
                .setView(dialogView)
                .show();
    }

    public void hideProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    public void showAlertDialog(String message) {
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
        alertDialog = new AlertDialog.Builder(BaseActivity.this, R.style.DialogTheme)
                .setMessage(Html.fromHtml(message))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public void showAlertDialog(AlertDialogEvent event) {

        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }

        alertDialog = new AlertDialog.Builder(BaseActivity.this, R.style.DialogTheme)
                .setTitle(event.title)
                .setMessage(Html.fromHtml(event.message))
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(event.cancelable)
                .show();
    }

    public void showAlertDialogLinks(String message) {

        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }

        View view = View.inflate(BaseActivity.this, R.layout.dialog_about, null);
        TextView textView = (TextView) view.findViewById(R.id.message);
        textView.setText(Html.fromHtml(message));
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        alertDialog = new AlertDialog.Builder(BaseActivity.this, R.style.DialogTheme)
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public void showAlertDialog(@NonNull AlertDialogEvent event, final Action0 actionToTake) {

        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }

        alertDialog = new AlertDialog.Builder(BaseActivity.this, R.style.DialogTheme)
                .setTitle(event.title)
                .setMessage(Html.fromHtml(event.message))
                .setCancelable(event.cancelable)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        actionToTake.call();
                    }
                })
                .show();
    }

    public void showAlertDialog(String message, final Action0 actionToTake) {

        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }

        alertDialog = new AlertDialog.Builder(BaseActivity.this, R.style.DialogTheme)
                .setMessage(Html.fromHtml(message))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        actionToTake.call();
                    }
                })
                .show();
    }

    public void showAlertDialog(String message, final Action0 actionToTake, final Action0 cancelActionToTake) {
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }

        alertDialog = new AlertDialog.Builder(BaseActivity.this, R.style.DialogTheme)
                .setCancelable(false)
                .setMessage(Html.fromHtml(message))
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        cancelActionToTake.call();
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        actionToTake.call();
                    }
                })
                .show();
    }

    public void showAlertDialog(@NonNull AlertDialogEvent event, final Action0 actionToTake, final Action0 cancelActionToTake) {
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }

        alertDialog = new AlertDialog.Builder(BaseActivity.this, R.style.DialogTheme)
                .setTitle(event.title)
                .setCancelable(false)
                .setMessage(Html.fromHtml(event.message))
                .setCancelable(event.cancelable)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        cancelActionToTake.call();
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        actionToTake.call();
                    }
                })
                .show();
    }

    public void logOutConfirmation() {
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }

        alertDialog = new AlertDialog.Builder(BaseActivity.this, R.style.DialogTheme)
                .setTitle(R.string.dialog_logout_title)
                .setMessage(R.string.dialog_logout_message)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        logOut();
                    }
                })
                .show();
    }

    public void logOut() {
        showProgressDialog(new ProgressDialogEvent("Logging out..."));
        onLoggedOut();
    }

    private void onLoggedOut() {
        dataService.logout();
        dbManager.clearDbManager();

        // clear preferences
        AuthUtils.resetCredentials(sharedPreferences);
        AuthUtils.reset(preference);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor prefEditor = settings.edit();
        prefEditor.clear();
        prefEditor.apply();

        SyncUtils.cancelSync(this);

        hideProgressDialog();

        Intent intent = PromoActivity.createStartIntent(BaseActivity.this);
        startActivity(intent);
        finish();
    }

    public void showConfirmationDialog(final ConfirmationDialogEvent event) {
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }

        alertDialog = new AlertDialog.Builder(BaseActivity.this, R.style.DialogTheme)
                .setTitle(event.title)
                .setMessage(Html.fromHtml(event.message))
                .setNegativeButton(event.negative, null)
                .setPositiveButton(event.positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        event.action.call();
                    }
                })
                .show();
    }

    private BroadcastReceiver connReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectivityManager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
            NetworkInfo currentNetworkInfo = connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
            if (currentNetworkInfo != null && currentNetworkInfo.isConnected()) {
                if (snackBar != null && snackBar.isShown()) {
                    snackBar.dismiss();
                    snackBar = null;
                }
            } else {
                handleNetworkDisconnect();
            }
        }
    };

    protected void toast(int messageId) {
        toast(getString(messageId));
    }

    protected void toast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM, 0, 180);
        toast.show();
    }

    protected void reportError(Throwable throwable) {
        if (throwable instanceof RetrofitError) {
            if (DataServiceUtils.isHttp400Error(throwable)) {
                return;
            } else if (DataServiceUtils.isHttp500Error(throwable)) {
                return;
            }
        }

        if (throwable instanceof SSLHandshakeException) {
            Timber.e(throwable.getMessage());
            return;
        }

        if (throwable instanceof UnknownHostException) {
            Timber.e(throwable.getMessage());
            toast(getString(R.string.error_no_internet));
            return;
        }

        if (throwable != null && throwable instanceof NetworkOnMainThreadException) {
            NetworkOnMainThreadException exception = (NetworkOnMainThreadException) throwable;
            Timber.e(exception.getMessage());
        } else if (throwable != null) {
            Timber.e(throwable.getMessage());
            throwable.printStackTrace();
        }
    }

    protected void handleError(Throwable throwable) {
        handleError(throwable, false);
    }

    // FIXME when we move this over to new Retrofit implement new error handling across all network calls
    protected void handleError(Throwable throwable, boolean retry) {

        // Handle NetworkConnectionException
        if (throwable instanceof NetworkConnectionException) {
            NetworkConnectionException networkConnectionException = (NetworkConnectionException) throwable;
            snack(networkConnectionException.getMessage(), retry);
            return;
        }

        // Handle NetworkException
        if(throwable instanceof NetworkException) {
            NetworkException networkException = (NetworkException) throwable;
            if (networkException.getStatus() == 403) {
                showAlertDialog(new AlertDialogEvent(getString(R.string.alert_token_expired_title), getString(R.string.error_bad_token)), new Action0() {
                    @Override
                    public void call() {
                        logOut();
                    }
                });
                return;
            } else if (networkException.getCode() == DataServiceUtils.CODE_THREE) {
                // refreshing token and will return 403 if refresh token invalid
                return;
            } else {
                // let's just let the throwable pass through
                throwable = networkException.getCause();
            }
        }

        // Handle Throwable exception
        if (DataServiceUtils.isConnectionError(throwable)) {
            Timber.i("Connection Error");
            snack(getString(R.string.error_service_unreachable_error), retry);
        } else if (DataServiceUtils.isTimeoutError(throwable)) {
            Timber.i("Timeout Error");
            snack(getString(R.string.error_service_timeout_error), retry);
        } else if (DataServiceUtils.isNetworkError(throwable)) {
            Timber.i("Data Error: " + "Code 503");
            snack(getString(R.string.error_service_unreachable_error), retry);
        } else if (DataServiceUtils.isHttp504Error(throwable)) {
            Timber.i("Data Error: " + "Code 504");
            snack(getString(R.string.error_service_timeout_error), retry);
        } else if (DataServiceUtils.isHttp502Error(throwable)) {
            Timber.i("Data Error: " + "Code 502");
            snack(getString(R.string.error_service_error), retry);
        } else if (DataServiceUtils.isConnectionError(throwable)) {
            Timber.e("Connection Error: " + "Code ???");
            snack(getString(R.string.error_service_timeout_error), retry);
        } else if (DataServiceUtils.isHttp403Error(throwable)) {
            Timber.i("Data Error: " + "Code 403");
            toast(getString(R.string.error_authentication));
        } else if (DataServiceUtils.isHttp401Error(throwable)) {
            Timber.i("Data Error: " + "Code 401");
            snack(getString(R.string.error_no_internet), retry);
        } else if (DataServiceUtils.isHttp500Error(throwable)) {
            Timber.i("Data Error: " + "Code 500");
            snack(getString(R.string.error_service_error), retry);
        } else if (DataServiceUtils.isHttp404Error(throwable)) {
            Timber.i("Data Error: " + "Code 404");
            snack(getString(R.string.error_service_error), retry);
        } else if (DataServiceUtils.isHttp400Error(throwable)) {
            Timber.e("Data Error: " + "Code 400");
            RetroError error = DataServiceUtils.createRetroError(throwable);
            if (error.getCode() == 403) {
                toast(getString(R.string.error_bad_token));
                showAlertDialog(new AlertDialogEvent(getString(R.string.alert_token_expired_title), getString(R.string.error_bad_token)), new Action0() {
                    @Override
                    public void call() {
                        logOut();
                    }
                });
            } else {
                Timber.e("Data Error Message: " + error.getMessage());
                snack(error.getMessage(), retry);
            }
        } else if (throwable != null && throwable.getMessage() != null) {
            Timber.i("Data Error: " + throwable.getMessage());
            snack(throwable.getMessage(), retry);
        } else {
            snack(R.string.error_unknown_error, retry);
        }
    }

    protected void snack(int message, boolean retry) {
        snack(getString(message), retry);
    }

    protected void snackError(String message) {
        if (snackBar != null && snackBar.isShownOrQueued()) {
            snackBar.dismiss();
        }

        try {
            View view = findViewById(R.id.coordinatorLayout);
            if (view != null) {
                snackBar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
                TextView textView = snackBar.getView().findViewById(android.support.design.R.id.snackbar_text);
                textView.setTextColor(getResources().getColor(R.color.white));
                snackBar.show();
            }
        } catch (NullPointerException e) {
            Timber.e(e.getMessage());
        }
    }

    protected void snack(String message, boolean retry) {
        if (snackBar != null && snackBar.isShownOrQueued()) {
            snackBar.dismiss();
        }

        try {
            View view = findViewById(R.id.coordinatorLayout);
            if (view != null) {
                if (retry) {
                    snackBar = Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.button_retry, new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    handleRefresh();
                                }
                            });
                    TextView textView = (TextView) snackBar.getView().findViewById(android.support.design.R.id.snackbar_text);
                    textView.setTextColor(getResources().getColor(R.color.white));
                    snackBar.show();
                } else {
                    snackBar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
                    TextView textView = (TextView) snackBar.getView().findViewById(android.support.design.R.id.snackbar_text);
                    textView.setTextColor(getResources().getColor(R.color.white));
                    snackBar.show();
                }
            }
        } catch (NullPointerException e) {
            Timber.e(e.getMessage());
        }
    }
}