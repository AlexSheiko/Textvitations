package com.aviary.android.feather.sdk.widget;

import android.app.Activity;

import com.aviary.android.feather.cds.IAPInstance;
import com.aviary.android.feather.cds.billing.util.IabHelper;
import com.aviary.android.feather.library.services.IAPService;
import com.aviary.android.feather.sdk.FeatherActivity;

public class AviaryStoreWrapper extends AviaryStoreWrapperAbstract {
    public AviaryStoreWrapper(final Callback callback, final int requestCode) {
        super(callback, requestCode);
    }

    @Override
    protected IAPInstance createWrapper(final Activity activity, final String billingKey) {
        return ((FeatherActivity) activity).getMainController().getService(IAPService.class);
    }

    @Override
    protected void launchPurchaseFlow(
        final String identifier, final IabHelper.OnIabPurchaseFinishedListener purchaseFinishedListener, final String extraData) {
        ((IAPService) wrapper).launchPurchaseFlow(identifier, purchaseFinishedListener, null);
    }

    public void onAttach(final FeatherActivity context) {
        super.onAttach(context, null);
    }
}
