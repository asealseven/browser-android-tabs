// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.graphics.Canvas;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.view.View;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.gesturenav.HistoryNavigationLayout;
import org.chromium.chrome.browser.search_engines.TemplateUrlServiceFactory;
import org.chromium.components.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.util.ViewUtils;

/**
 * The New Tab Page for use in the incognito profile.
 */
public class IncognitoNewTabPageView extends HistoryNavigationLayout {

    private IncognitoNewTabPageManager mManager;
    private boolean mFirstShow = true;
    private NewTabPageScrollView mScrollView;
    private Context mContext;

    private int mSnapshotWidth;
    private int mSnapshotHeight;
    private int mSnapshotScrollY;

    private View mDDGOfferLink;
    private View mDDGOfferImage;

    /**
     * Manages the view interaction with the rest of the system.
     */
    interface IncognitoNewTabPageManager {
        /** Loads a page explaining details about incognito mode in the current tab. */
        void loadIncognitoLearnMore();

        /**
         * Called when the NTP has completely finished loading (all views will be inflated
         * and any dependent resources will have been loaded).
         */
        void onLoadingComplete();
    }

    /** Default constructor needed to inflate via XML. */
    public IncognitoNewTabPageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mScrollView = (NewTabPageScrollView) findViewById(R.id.ntp_scrollview);
        mScrollView.setBackgroundColor(
                ApiCompatibilityUtils.getColor(getResources(), R.color.ntp_bg_incognito));
        setContentDescription(
                getResources().getText(R.string.brave_new_private_tab));

        // FOCUS_BEFORE_DESCENDANTS is needed to support keyboard shortcuts. Otherwise, pressing
        // any shortcut causes the UrlBar to be focused. See ViewRootImpl.leaveTouchMode().
        mScrollView.setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);

        IncognitoDescriptionView descriptionView =
                (IncognitoDescriptionView) findViewById(R.id.new_tab_incognito_container);
        descriptionView.setLearnMoreOnclickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mManager.loadIncognitoLearnMore();
            }
        });
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        assert mManager != null;
        if (mFirstShow) {
            mManager.onLoadingComplete();
            mFirstShow = false;
        }

        mDDGOfferLink = findViewById(R.id.ddg_offer_link);
        if (mDDGOfferLink != null) {
            mDDGOfferLink.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDDGOffer(true);
                }
            });
        }

        mDDGOfferImage = findViewById(R.id.ddg_offer_img);
        if (mDDGOfferImage != null) {
            mDDGOfferImage.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDDGOffer(true);
                }
            });
            showDDGOffer(false);
        }
    }

    /**
     * Initialize the incognito New Tab Page.
     * @param manager The manager that handles external dependencies of the view.
     */
    void initialize(IncognitoNewTabPageManager manager) {
        mManager = manager;
    }

    /** @return The IncognitoNewTabPageManager associated with this IncognitoNewTabPageView. */
    protected IncognitoNewTabPageManager getManager() {
        return mManager;
    }

    /**
     * @see org.chromium.chrome.browser.compositor.layouts.content.
     *         InvalidationAwareThumbnailProvider#shouldCaptureThumbnail()
     */
    boolean shouldCaptureThumbnail() {
        if (getWidth() == 0 || getHeight() == 0) return false;

        return getWidth() != mSnapshotWidth
                || getHeight() != mSnapshotHeight
                || mScrollView.getScrollY() != mSnapshotScrollY;
    }

    /**
     * @see org.chromium.chrome.browser.compositor.layouts.content.
     *         InvalidationAwareThumbnailProvider#captureThumbnail(Canvas)
     */
    void captureThumbnail(Canvas canvas) {
        ViewUtils.captureBitmap(this, canvas);
        mSnapshotWidth = getWidth();
        mSnapshotHeight = getHeight();
        mSnapshotScrollY = mScrollView.getScrollY();
    }

    public void showDDGOffer(boolean forceShow) {
        if (TemplateUrlServiceFactory.get().getDefaultSearchEngineKeyword(true).equals(TemplateUrlService.DDG_SE_KEYWORD) ||
            !ContextUtils.getAppSharedPreferences().getBoolean(TemplateUrlService.PREF_SHOW_DDG_OFFER, true)) {
            mDDGOfferLink.setVisibility(View.GONE);
            mDDGOfferImage.setVisibility(View.GONE);
            return;
        }
        if (!forceShow && ContextUtils.getAppSharedPreferences().getBoolean(TemplateUrlService.PREF_DDG_OFFER_SHOWN, false)) {
            return;
        }
        ContextUtils.getAppSharedPreferences().edit().putBoolean(TemplateUrlService.PREF_DDG_OFFER_SHOWN, true).apply();
        new AlertDialog.Builder(mContext, R.style.BraveDialogTheme)
        .setView(R.layout.ddg_offer_layout)
        .setPositiveButton(R.string.ddg_offer_positive, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                TemplateUrlServiceFactory.get().setSearchEngine(TemplateUrlService.DDG_SE_NAME, TemplateUrlService.DDG_SE_KEYWORD, true);
                mDDGOfferLink.setVisibility(View.GONE);
                mDDGOfferImage.setVisibility(View.GONE);
            }
        })
        .setNegativeButton(R.string.ddg_offer_negative, null)
        .show();
    }
}
