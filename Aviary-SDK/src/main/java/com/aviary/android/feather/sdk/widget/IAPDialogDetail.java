package com.aviary.android.feather.sdk.widget;

import android.content.Context;
import android.util.AttributeSet;

import com.aviary.android.feather.cds.PacksColumns;
import com.aviary.android.feather.cds.billing.util.Inventory;
import com.aviary.android.feather.library.services.BadgeService;
import com.aviary.android.feather.sdk.FeatherActivity;
import com.aviary.android.feather.sdk.widget.IAPDialogMain.IAPUpdater;

public class IAPDialogDetail extends PackDetailLayout {
    public static final String EXTRA_CLICK_FROM_POSITION = "click_from_position";
    private IAPUpdater    mData;
    private BadgeService  mBadgeService;
    private IAPDialogMain mParent;

    public IAPDialogDetail(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IAPUpdater getData() {
        return mData;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        final FeatherActivity activity = (FeatherActivity) getContext();
        mBadgeService = activity.getMainController().getService(BadgeService.class);

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            mParent.getController().getTracker().tagEvent("shop_details: closed");
        } catch (Throwable t) {
        }
    }

    @Override
    public boolean isValidContext() {
        return isAttached() && null != getContext();
    }

    @Override
    protected AviaryStoreWrapperAbstract getStoreWrapper() {
        return mParent.getStoreWrapper();
    }

    @Override
    protected void onSetPackContentCompleted(PacksColumns.PackCursorWrapper pack) {
        if (null != mBadgeService && null != pack) {
            mBadgeService.markAsRead(pack.getIdentifier());
        }
    }

    @Override
    public Inventory getInventory() {
        return mParent.mInventory;
    }

    @Override
    public void setInventory(final Inventory inventory) {}

    @Override
    protected boolean isChildVisible(final PackDetailLayout packDetailLayout) {
        return mParent.isChildVisible(this);
    }

    @Override
    protected void onForceUpdate() {
        update(mData, mParent);
    }

    public void update(IAPUpdater updater, IAPDialogMain parent) {
        logger.info("update: %s", updater);
        logger.log("isValidContext: %b", isValidContext());

        if (null == updater || !isValidContext()) {
            return;
        }

        if (parent.getStoreWrapper().isActive()) {
            mParent = parent;
            mData = (IAPUpdater) updater.clone();

            update(mData.getPackId(), mData.getIsAnimating(), mData.getExtras());
        }
    }
}
