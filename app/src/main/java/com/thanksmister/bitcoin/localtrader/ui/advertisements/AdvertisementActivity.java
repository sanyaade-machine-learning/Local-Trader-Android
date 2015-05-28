/*
 * Copyright (c) 2015 ThanksMister LLC
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
 */

package com.thanksmister.bitcoin.localtrader.ui.advertisements;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.squareup.sqlbrite.SqlBrite;
import com.thanksmister.bitcoin.localtrader.BaseActivity;
import com.thanksmister.bitcoin.localtrader.R;
import com.thanksmister.bitcoin.localtrader.data.api.model.Advertisement;
import com.thanksmister.bitcoin.localtrader.data.api.model.TradeType;
import com.thanksmister.bitcoin.localtrader.data.database.AdvertisementItem;
import com.thanksmister.bitcoin.localtrader.data.database.DbManager;
import com.thanksmister.bitcoin.localtrader.data.database.MethodItem;
import com.thanksmister.bitcoin.localtrader.data.services.DataService;
import com.thanksmister.bitcoin.localtrader.events.ConfirmationDialogEvent;
import com.thanksmister.bitcoin.localtrader.events.ProgressDialogEvent;
import com.thanksmister.bitcoin.localtrader.utils.Dates;
import com.thanksmister.bitcoin.localtrader.utils.Strings;
import com.thanksmister.bitcoin.localtrader.utils.TradeUtils;

import java.util.List;

import javax.inject.Inject;

import butterknife.ButterKnife;
import butterknife.InjectView;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func2;
import rx.subscriptions.Subscriptions;
import timber.log.Timber;

import static rx.android.app.AppObservable.bindActivity;

public class AdvertisementActivity extends BaseActivity implements SwipeRefreshLayout.OnRefreshListener
{
    public static final String EXTRA_AD_ID = "com.thanksmister.extras.EXTRA_AD_ID";

    @Inject
    DataService dataService;
    
    @Inject
    DbManager dbManager;

    @Inject
    SqlBrite db;
    
    @InjectView(R.id.tradePrice)
    TextView tradePrice;

    @InjectView(R.id.noteTextAdvertisement)
    TextView noteTextAdvertisement;

    @InjectView(R.id.tradeLimit)
    TextView tradeLimit;

    @InjectView(R.id.tradeTerms)
    TextView tradeTerms;

    @InjectView(R.id.priceEquation)
    TextView priceEquation;

    @InjectView(R.id.onlineProvider)
    TextView onlineProvider;

    @InjectView(R.id.paymentDetails)
    TextView paymentDetails;

    @InjectView(R.id.noteText)
    TextView noteText;

    @InjectView(R.id.noteLayout)
    View noteLayout;

    @InjectView(R.id.onlinePaymentLayout)
    View onlinePaymentLayout;

    @InjectView(R.id.advertisementProgress)
    View progress;

    @InjectView(R.id.advertisementContent)
    View content;

    @InjectView(R.id.advertisementToolBar)
    Toolbar toolbar;

    @InjectView(R.id.swipeLayout)
    SwipeRefreshLayout swipeLayout;

    private String adId;
    private Menu menu;
    private AdvertisementData advertisementData;
    
    private class AdvertisementData {
       public AdvertisementItem advertisement;
       public MethodItem method;
    }

    private Subscription subscription = Subscriptions.empty();
    private Subscription updateSubscription = Subscriptions.empty();

    private Observable<List<MethodItem>> methodObservable;
    private Observable<AdvertisementItem> advertisementObservable;
    private Observable<Advertisement> updateAdvertisementObservable;
    private Observable<Boolean> deleteObservable;
   
    public static Intent createStartIntent(Context context, String adId)
    {
        Intent intent = new Intent(context, AdvertisementActivity.class);
        intent.putExtra(EXTRA_AD_ID, adId);
        return intent;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.view_advertisement);

        ButterKnife.inject(this);

        if (savedInstanceState == null && getIntent().hasExtra(EXTRA_AD_ID)) {
            adId = getIntent().getStringExtra(EXTRA_AD_ID);
        } else if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_AD_ID)) {
            adId = savedInstanceState.getString(EXTRA_AD_ID);
        }
        
        if(toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
            setToolBarMenu(toolbar);
        }

        Timber.d("Activity Created adID: " + adId);
        
        methodObservable = bindActivity(this, dbManager.methodQuery().cache());
        advertisementObservable = bindActivity(this, dbManager.advertisementItemQuery(adId));
        updateAdvertisementObservable = bindActivity(this, dataService.getAdvertisement(adId));
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_AD_ID, adId);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        if(requestCode == EditActivity.REQUEST_CODE) {
            if (resultCode == EditActivity.RESULT_UPDATED) {
                this.adId = intent.getStringExtra(EXTRA_AD_ID);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        if(toolbar != null)
            toolbar.inflateMenu(R.menu.advertisement);

        this.menu = menu;

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRefresh()
    {
        updateData();
    }

    public void onRefreshStart()
    {
        hideProgress();

        if(swipeLayout != null)
            swipeLayout.setRefreshing(false);
    }

    public void onRefreshStop()
    {
        hideProgress();

        if(swipeLayout != null)
            swipeLayout.setRefreshing(false);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        subscribeData();
        
        updateData();
    }

    @Override
    public void onPause()
    {
        super.onPause();

        updateSubscription.unsubscribe();
        subscription.unsubscribe();
    }
    
    public void setToolBarMenu(Toolbar toolbar)
    {
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener()
        {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem)
            {
                switch (menuItem.getItemId()) {
                    case R.id.action_edit:
                        editAdvertisement();
                        return true;
                    case R.id.action_share:
                        shareAdvertisement();
                        return true;
                    case R.id.action_delete:
                        deleteAdvertisement();
                        return true;
                    case R.id.action_visible:
                        updateAdvertisementVisibility();
                        return true;
                    case R.id.action_website:
                        viewOnlineAdvertisement();
                        return true;
                    case R.id.action_location:
                        showAdvertisementOnMap();
                        return true;
                }
                return false;
            }
        });
    }
    
    public void showError()
    {
        progress.setVisibility(View.GONE);
        content.setVisibility(View.GONE);
    }
    
    public void showProgress()
    {
        progress.setVisibility(View.VISIBLE);
        content.setVisibility(View.GONE);
    }
    
    public void hideProgress()
    {
        progress.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
    }

    protected void subscribeData()
    {
        subscription = Observable.combineLatest(methodObservable, advertisementObservable, new Func2<List<MethodItem>, AdvertisementItem, AdvertisementData>()
        {
            @Override
            public AdvertisementData call(List<MethodItem> methods, AdvertisementItem advertisement)
            {
                advertisementData = new AdvertisementData();
                advertisementData.method = TradeUtils.getMethodForAdvertisement(advertisement, methods);
                advertisementData.advertisement = advertisement;
                return advertisementData;
            }
        }).subscribe(new Action1<AdvertisementData>()
        {
            @Override
            public void call(AdvertisementData advertisementData)
            {
                Timber.d("Update Advertisement Data");
                setAdvertisement(advertisementData.advertisement, advertisementData.method);
            }
        }, new Action1<Throwable>()
        {
            @Override
            public void call(Throwable throwable)
            {
                reportError(throwable);
                showError();
                toast("Unable to retrieve advertisement data.");
            }
        });
    }
    
    protected void updateData()
    {
        onRefreshStart();
                
        updateSubscription = updateAdvertisementObservable.subscribe(new Action1<Advertisement>()
        {
            @Override
            public void call(Advertisement advertisement)
            {
                onRefreshStop();
                
                dbManager.updateAdvertisement(advertisement);
            }
        }, new Action1<Throwable>()
        {
            @Override
            public void call(Throwable throwable)
            {
                onRefreshStop();
                
                handleError(throwable);
            }
        });
    }
    
    public void setAdvertisement(AdvertisementItem advertisement, @Nullable MethodItem method)
    {
        Timber.d("Set Advertisement Data");
        tradePrice.setText(getString(R.string.trade_price, advertisement.temp_price(), advertisement.currency()));

        String price = advertisement.currency();
        //String date = Dates.parseLocalDateStringAbbreviatedDate(advertisement.created_at());

        TradeType tradeType = TradeType.valueOf(advertisement.trade_type());
        String title = "";
        switch (tradeType) {
            case LOCAL_BUY:
                title = "Local buy";
                break;
            case LOCAL_SELL:
                title = "Local sale";
                break;
            case ONLINE_BUY:
                title = "Online buy";
                break;
            case ONLINE_SELL:
                title = "Online sale";
                break;
        }

        String location = advertisement.location_string();
        if (TradeUtils.isLocalTrade(advertisement)) {
            noteTextAdvertisement.setText(Html.fromHtml(getString(R.string.advertisement_notes_text_locally, title, price, location)));
        } else {
            String paymentMethod = TradeUtils.getPaymentMethod(advertisement, method);
            noteTextAdvertisement.setText(Html.fromHtml(getString(R.string.advertisement_notes_text_online, title, price, paymentMethod, advertisement.location_string())));
        }

        if(TradeUtils.isLocalTrade(advertisement)) {
            onlinePaymentLayout.setVisibility(View.GONE);
        }

        if(advertisement.max_amount() == null) {
            tradeLimit.setText(getString(R.string.trade_limit_min, advertisement.min_amount(), advertisement.currency()));
        } else { // no maximum set
            tradeLimit.setText(getString(R.string.trade_limit, advertisement.min_amount(), advertisement.max_amount(), advertisement.currency()));
        }

        priceEquation.setText(advertisement.price_equation());
        
        /*if(!Strings.isBlank(advertisement.message())){
            String message = advertisement.message().trim();
            //message = message.replace("\n", "").replace("\r", "<br>");
            tradeTerms.setText(Html.fromHtml(message));
            tradeTerms.setMovementMethod(LinkMovementMethod.getInstance());
        }*/

        if(!Strings.isBlank(advertisement.message())){
            tradeTerms.setText(advertisement.message().trim());
            tradeTerms.setMovementMethod(LinkMovementMethod.getInstance());
        }

        if(tradeType == TradeType.ONLINE_SELL) {
            String paymentMethod = TradeUtils.getPaymentMethodName(advertisement, method);
            onlineProvider.setText(paymentMethod);
            paymentDetails.setText(advertisement.account_info());
        }

        updateAdvertisement(advertisement);
    }
    
    public void updateAdvertisement(AdvertisementItem advertisement)
    {
        Timber.d("updateAdvertisement Data");
        
        noteLayout.setVisibility(advertisement.visible() ? View.GONE : View.VISIBLE);
        
        noteText.setText(getString(R.string.advertisement_invisible_warning));
        
        setMenuVisibilityIcon(advertisement.visible());
    }

    private void setMenuVisibilityIcon(boolean show)
    {
        int icon;
        if(show) {
            icon = R.drawable.ic_action_visibility;
        } else {
            icon = R.drawable.ic_action_visibility_off;
        }

        if(menu != null)
            menu.getItem(0).setIcon(icon);
        
        onRefreshStop();
    }

    private void viewOnlineAdvertisement()
    {
        if(advertisementData == null) return;;
        AdvertisementItem advertisement = advertisementData.advertisement;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(advertisement.action_public_view()));
        startActivity(intent);
    }

    private void deleteAdvertisement()
    {
        ConfirmationDialogEvent event = new ConfirmationDialogEvent("Delete Advertisement",
                getString(R.string.advertisement_delete_confirm),
                getString(R.string.button_delete),
                getString(R.string.button_cancel), new Action0() {
            @Override
            public void call() {
                deleteAdvertisementConfirmed(adId);
            }
        });

        showConfirmationDialog(event);
    }

    private void deleteAdvertisementConfirmed(final String adId)
    {
        deleteObservable = bindActivity(this, dataService.deleteAdvertisement(adId));
        deleteObservable.subscribe(new Action1<Boolean>()
        {
            @Override
            public void call(Boolean aBoolean)
            {
                db.delete(AdvertisementItem.TABLE, AdvertisementItem.ID, adId);
                toast("Advertisement deleted!");
                finish();
            }
        }, new Action1<Throwable>()
        {
            @Override
            public void call(Throwable throwable)
            {
                toast("Error deleting advertisement!");
            }
        });
    }

    private void updateAdvertisementVisibility()
    {
        showProgressDialog(new ProgressDialogEvent("Updating visibility..."));
        
        if(advertisementData == null) return;
        
        Advertisement advertisement = new Advertisement();
        advertisement = advertisement.convertAdvertisementItemToAdvertisement(advertisementData.advertisement);
        advertisement.visible = !advertisement.visible;
        
        Observable<Boolean> updateObservable = bindActivity(this, dataService.updateAdvertisementVisibility(advertisement, advertisement.visible));
        updateObservable.subscribe(new Action1<Boolean>()
        {
            @Override
            public void call(Boolean aBoolean)
            {
                hideProgressDialog();

                toast("Visibility updated!");

                updateData();
                
            }
        }, new Action1<Throwable>()
        {
            @Override
            public void call(Throwable throwable)
            {
                reportError(throwable);
                toast("Error updating visibility!");
            }
        });
    }
    
    private void showAdvertisementOnMap()
    {
        if(advertisementData == null) return;;
        AdvertisementItem advertisement = advertisementData.advertisement;
        String geoUri = "";
        if(TradeUtils.isLocalTrade(advertisement)) {
            geoUri = "http://maps.google.com/maps?q=loc:" + advertisement.lat() + "," + advertisement.lon() + " (" + advertisement.location_string() + ")";
        } else {
            geoUri = "geo:0,0?q=" + advertisement.location_string();
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(geoUri));
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            toast("There is no activity to handle this action (no maps).");
        }
    }

    private void shareAdvertisement()
    {
        if(advertisementData == null) return;;
        AdvertisementItem advertisement = advertisementData.advertisement;
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");

        String message = "";
        String buyOrSell = (TradeUtils.isBuyTrade(advertisement))? "Buy":"Sell";
        String prep = (TradeUtils.isSellTrade(advertisement))? " from ":" to ";
        String onlineOrLocal = (TradeUtils.isLocalTrade(advertisement))? "locally":"online";

        if(TradeUtils.isLocalTrade(advertisement)) {
            message = buyOrSell + " bitcoins " + onlineOrLocal + " in " + advertisement.location_string() + prep + advertisement.profile_username()  + ": " + advertisement.action_public_view();
        } else {
            String provider = TradeUtils.parsePaymentServiceTitle(advertisement.online_provider());
            message = buyOrSell + " bitcoins " + onlineOrLocal + " in " + advertisement.location_string() + prep + advertisement.profile_username() + " via "  + provider + ": " + advertisement.action_public_view();
        }

        shareIntent.putExtra(Intent.EXTRA_TEXT, message);
        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, buyOrSell + " bitcoins in " + advertisement.location_string());
        startActivity(Intent.createChooser(shareIntent, "Share to:"));
    }

    private void editAdvertisement()
    {
        Intent intent = EditActivity.createStartIntent(this, false, adId);
        startActivityForResult(intent, EditActivity.REQUEST_CODE);
    }
}
