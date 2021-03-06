// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar.bottom;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageButton;

import org.chromium.base.Callback;
import org.chromium.base.ObservableSupplier;
import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.ActivityTabProvider.HintlessActivityTabObserver;
import org.chromium.chrome.browser.ThemeColorProvider;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.bookmarks.BookmarkUtils;
import org.chromium.chrome.browser.feature_engagement.TrackerFactory;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabImpl;
import org.chromium.chrome.browser.tasks.ReturnToChromeExperimentsUtil;
import org.chromium.chrome.browser.toolbar.BookmarksButton;
import org.chromium.chrome.browser.toolbar.HomeButton;
import org.chromium.chrome.browser.toolbar.IncognitoStateProvider;
import org.chromium.chrome.browser.toolbar.TabCountProvider;
import org.chromium.chrome.browser.toolbar.TabSwitcherButtonCoordinator;
import org.chromium.chrome.browser.toolbar.TabSwitcherButtonView;
import org.chromium.chrome.browser.ui.appmenu.AppMenuButtonHelper;
import org.chromium.components.feature_engagement.FeatureConstants;
import org.chromium.chrome.browser.toolbar.bottom.SearchAccelerator;
import org.chromium.chrome.browser.util.AccessibilityUtil;
import org.chromium.components.feature_engagement.Tracker;
import org.chromium.ui.modelutil.PropertyModelChangeProcessor;
import org.chromium.chrome.browser.util.TabUtil;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;

import org.chromium.ui.widget.Toast;

import org.chromium.chrome.browser.toolbar.MenuButton;

/**
 * The coordinator for the browsing mode bottom toolbar. This class has two primary components,
 * an Android view that handles user actions and a composited texture that draws when the controls
 * are being scrolled off-screen. The Android version does not draw unless the controls offset is 0.
 */
public class BrowsingModeBottomToolbarCoordinator implements View.OnLongClickListener {
    /** The mediator that handles events from outside the browsing mode bottom toolbar. */
    private final BrowsingModeBottomToolbarMediator mMediator;

    /** The home button that lives in the bottom toolbar. */
    private final HomeButton mHomeButton;

    /** The share button that lives in the bottom toolbar. */
    private final ShareButton mShareButton;

    /** The bookmarks button that lives in the bottom toolbar. */
    private final BookmarksButton mBookmarksButton;

    /** The new tab button that lives in the bottom toolbar. */
    private final BottomToolbarNewTabButton mNewTabButton;

    /** The search accelerator that lives in the bottom toolbar. */
    private final SearchAccelerator mSearchAccelerator;

    /** The tab switcher button component that lives in the bottom toolbar. */
    private final TabSwitcherButtonCoordinator mTabSwitcherButtonCoordinator;

    /** The tab switcher button view that lives in the bottom toolbar. */
    private final TabSwitcherButtonView mTabSwitcherButtonView;

    /** The view group that includes all views shown on browsing mode */
    private final BrowsingModeBottomToolbarLinearLayout mToolbarRoot;

    /** The model for the browsing mode bottom toolbar that holds all of its state. */
    private final BrowsingModeBottomToolbarModel mModel;

    /** The callback to be exectured when the share button on click listener is available. */
    private Callback<OnClickListener> mShareButtonListenerSupplierCallback;

    /** The supplier for the share button on click listener. */
    private ObservableSupplier<OnClickListener> mShareButtonListenerSupplier;

    /** The activity tab provider that used for making the IPH. */
    private final ActivityTabProvider mTabProvider;

    /** The menu button that lives in the browsing mode bottom toolbar. */
    private final MenuButton mMenuButton;

    final Context context = ContextUtils.getApplicationContext();

    /**
     * Build the coordinator that manages the browsing mode bottom toolbar.
     * @param root The root {@link View} for locating the views to inflate.
     * @param tabProvider The {@link ActivityTabProvider} used for making the IPH.
     * @param homeButtonListener The {@link OnClickListener} for the home button.
     * @param searchAcceleratorListener The {@link OnClickListener} for the search accelerator.
     * @param shareButtonListener The {@link OnClickListener} for the share button.
     */
    BrowsingModeBottomToolbarCoordinator(View root, ActivityTabProvider tabProvider,
            OnClickListener homeButtonListener, OnClickListener searchAcceleratorListener,
            ObservableSupplier<OnClickListener> shareButtonListenerSupplier,
            OnLongClickListener tabSwitcherLongClickListener) {
        mModel = new BrowsingModeBottomToolbarModel();
        mToolbarRoot = root.findViewById(R.id.bottom_toolbar_browsing);
        mTabProvider = tabProvider;

        PropertyModelChangeProcessor.create(
                mModel, mToolbarRoot, new BrowsingModeBottomToolbarViewBinder());

        mMediator = new BrowsingModeBottomToolbarMediator(mModel);

        mHomeButton = mToolbarRoot.findViewById(R.id.bottom_home_button);
        mHomeButton.setOnClickListener(homeButtonListener);
        mHomeButton.setOnLongClickListener(this);
        mHomeButton.setActivityTabProvider(mTabProvider);
        setupIPH(FeatureConstants.CHROME_DUET_HOME_BUTTON_FEATURE, mHomeButton, homeButtonListener);

        mNewTabButton = mToolbarRoot.findViewById(R.id.bottom_new_tab_button);

        mShareButton = mToolbarRoot.findViewById(R.id.bottom_share_button);

        mBookmarksButton = mToolbarRoot.findViewById(R.id.bookmark_this_page_id);

        mMenuButton = mToolbarRoot.findViewById(R.id.menu_button_wrapper);

        mSearchAccelerator = mToolbarRoot.findViewById(R.id.search_accelerator);
        mSearchAccelerator.setOnClickListener(searchAcceleratorListener);
        setupIPH(FeatureConstants.CHROME_DUET_SEARCH_FEATURE, mSearchAccelerator,
                 searchAcceleratorListener);        

        // TODO(amaralp): Make this adhere to MVC framework.
        mTabSwitcherButtonView = mToolbarRoot.findViewById(R.id.bottom_tab_switcher_button);
        mTabSwitcherButtonCoordinator = new TabSwitcherButtonCoordinator(mTabSwitcherButtonView);

        mTabSwitcherButtonView.setOnLongClickListener(tabSwitcherLongClickListener);
        if (BottomToolbarVariationManager.isNewTabButtonOnBottom()
                && !BottomToolbarVariationManager.isBraveVariation()) {
            mNewTabButton.setVisibility(View.VISIBLE);
        }
        if (BottomToolbarVariationManager.isHomeButtonOnBottom()) {
            mHomeButton.setVisibility(View.VISIBLE);
        }

        if (BottomToolbarVariationManager.isTabSwitcherOnBottom()) {
            mTabSwitcherButtonView.setVisibility(View.VISIBLE);
        }
        if (BottomToolbarVariationManager.isShareButtonOnBottom()) {
            mShareButton.setVisibility(View.VISIBLE);
            mShareButtonListenerSupplierCallback = shareButtonListener -> {
                mShareButton.setOnClickListener(shareButtonListener);
            };
            mShareButtonListenerSupplier = shareButtonListenerSupplier;
            mShareButton.setActivityTabProvider(mTabProvider);
            mShareButtonListenerSupplier.addObserver(mShareButtonListenerSupplierCallback);
        }
        if (BottomToolbarVariationManager.isBookmarkButtonOnBottom()) {
            mBookmarksButton.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Setup and show the IPH bubble for Chrome Duet if needed.
     * @param feature A String identifying the feature.
     * @param anchor The view to anchor the IPH to.
     * @param listener An {@link OnClickListener} that is triggered when IPH is clicked. {@link
     *         HomeButton} and {@link SearchAccelerator} need to pass this parameter, {@link
     *         TabSwitcherButtonView} just passes null.
     */
    void setupIPH(@FeatureConstants String feature, View anchor, OnClickListener listener) {
        mTabProvider.addObserverAndTrigger(new HintlessActivityTabObserver() {
            @Override
            public void onActivityTabChanged(Tab tab) {
                if (tab == null) return;
                TabImpl tabImpl = (TabImpl) tab;
                final Tracker tracker = TrackerFactory.getTrackerForProfile(tabImpl.getProfile());
                final Runnable completeRunnable = () -> {
                    if (listener != null) {
                        listener.onClick(anchor);
                    }
                };
                tracker.addOnInitializedCallback((ready) -> {
                    mMediator.showIPH(
                            feature, tabImpl.getActivity(), anchor, tracker, completeRunnable);
                });
                mTabProvider.removeObserver(this);
            }
        });

        // Set long click events
        if (mHomeButton != null) {
            mHomeButton.setOnLongClickListener(this);
        }
        if (mSearchAccelerator != null) {
            mSearchAccelerator.setOnLongClickListener(this);
        }
        if (mBookmarksButton != null) {
            mBookmarksButton.setOnLongClickListener(this);
        }
    }

    /**
     * @param isVisible Whether the bottom toolbar is visible.
     */
    void onVisibilityChanged(boolean isVisible) {
        if (isVisible) return;
        TabImpl tabImpl = (TabImpl) mTabProvider.get();
        if (tabImpl != null) {
            mMediator.dismissIPH(tabImpl.getActivity());
        }
    }

    /**
     * Initialize the bottom toolbar with the components that had native initialization
     * dependencies.
     * <p>
     * Calling this must occur after the native library have completely loaded.
     * @param tabSwitcherListener An {@link OnClickListener} that is triggered when the
     *                            tab switcher button is clicked.
     * @param tabSwitcherListener An {@link OnClickListener} that is triggered when the
     *                            tab switcher button is clicked.
     * @param menuButtonHelper An {@link AppMenuButtonHelper} that is triggered when the
     *                         menu button is clicked.
     * @param tabCountProvider Updates the tab count number in the tab switcher button.
     * @param themeColorProvider Notifies components when theme color changes.
     * @param incognitoStateProvider Notifies components when incognito state changes.
     * @param overviewModeBehavior Notifies components when overview mode changes.
     */
    void initializeWithNative(OnClickListener newTabListener, OnClickListener tabSwitcherListener, OnClickListener bookmarkClickListener,
            AppMenuButtonHelper menuButtonHelper, TabCountProvider tabCountProvider,
            ThemeColorProvider themeColorProvider, IncognitoStateProvider incognitoStateProvider,
            OverviewModeBehavior overviewModeBehavior) {
        mMediator.setThemeColorProvider(themeColorProvider);
        if (BottomToolbarVariationManager.isNewTabButtonOnBottom()) {
            mNewTabButton.setOnClickListener(newTabListener);
            mNewTabButton.setThemeColorProvider(themeColorProvider);
            mNewTabButton.setIncognitoStateProvider(incognitoStateProvider);
        }
        if (BottomToolbarVariationManager.isHomeButtonOnBottom()) {
            mHomeButton.setThemeColorProvider(themeColorProvider);
        }

        if (BottomToolbarVariationManager.isShareButtonOnBottom()) {
            mShareButton.setThemeColorProvider(themeColorProvider);
        }

        mSearchAccelerator.setThemeColorProvider(themeColorProvider);
        mSearchAccelerator.setIncognitoStateProvider(incognitoStateProvider);

        if (BottomToolbarVariationManager.isTabSwitcherOnBottom()) {
            mTabSwitcherButtonCoordinator.setTabSwitcherListener(tabSwitcherListener);
            mTabSwitcherButtonCoordinator.setThemeColorProvider(themeColorProvider);
            mTabSwitcherButtonCoordinator.setTabCountProvider(tabCountProvider);
            // Send null to IPH here to avoid tabSwitcherListener to be called twince, since
            // mTabSwitcherButtonView has it own OnClickListener, but other buttons set
            // OnClickListener to their wrappers.
            setupIPH(FeatureConstants.CHROME_DUET_TAB_SWITCHER_FEATURE, mTabSwitcherButtonView,
                    null);
        }

        // If StartSurface is HomePage, BrowsingModeBottomToolbar is shown in browsing mode and in
        // overview mode. We need to pass the OverviewModeBehavior to the buttons so they are
        // disabled based on the overview state.
        if (ReturnToChromeExperimentsUtil.shouldShowStartSurfaceAsTheHomePage()) {
            mShareButton.setOverviewModeBehavior(overviewModeBehavior);
            mTabSwitcherButtonCoordinator.setOverviewModeBehavior(overviewModeBehavior);
            mHomeButton.setOverviewModeBehavior(overviewModeBehavior);
        }

        if (mBookmarksButton != null) {
            mBookmarksButton.setThemeColorProvider(themeColorProvider);
            mBookmarksButton.setOnClickListener(bookmarkClickListener);
        }

        mMenuButton.setAppMenuButtonHelper(menuButtonHelper);
        mMenuButton.setThemeColorProvider(themeColorProvider);
    }

    @Override
    public boolean onLongClick(View v) {
        String description = "";
        Resources resources = context.getResources();

        if (v == mHomeButton) {
            if (!HomepageManager.isHomepageEnabled()) {
                TabUtil.showTabPopupMenu(context, v);
                return true;
            } else {
                description = resources.getString(R.string.accessibility_toolbar_btn_home);
            }
        } else if (v == mBookmarksButton) {
            description = resources.getString(R.string.accessibility_toolbar_btn_bookmark);
        } else if (v == mSearchAccelerator) {
            description = resources.getString(R.string.accessibility_toolbar_btn_search_accelerator);
        }

        return Toast.showAnchoredToast(context, v, description);
    }

    /**
     * @param enabled Whether to disable click events on the bottom toolbar. Setting true can also
     *                prevent from all click events on toolbar and all children views on toolbar.
     */
    void setTouchEnabled(boolean enabled) {
        mToolbarRoot.setTouchEnabled(enabled);
    }

    /**
     * @param visible Whether to hide the tab switcher bottom toolbar
     */
    void setVisible(boolean visible) {
        mModel.set(BrowsingModeBottomToolbarModel.IS_VISIBLE, visible);
    }

    /**
     * @return The browsing mode bottom toolbar's share button.
     */
    ShareButton getShareButton() {
        return mShareButton;
    }

    /**
     * @return The browsing mode bottom toolbar's bookmark button.
     */
    BookmarksButton getBookmarksButton() {
        return mBookmarksButton;
    }

    /**
     * @return The browsing mode bottom toolbar's tab switcher button.
     */
    TabSwitcherButtonView getTabSwitcherButtonView() {
        return mTabSwitcherButtonView;
    }

    /**
     * @return The browsing mode bottom toolbar's search button.
     */
    SearchAccelerator getSearchAccelerator() {
        return mSearchAccelerator;
    }

    /**
     * @return The browsing mode bottom toolbar's home button.
     */
    HomeButton getHomeButton() {
        return mHomeButton;
    }

    /**
     * @return The browsing mode bottom toolbar's new tab button.
     */
    BottomToolbarNewTabButton getNewTabButton() {
        return mNewTabButton;
    }

    /**
     * Clean up any state when the browsing mode bottom toolbar is destroyed.
     */
    public void destroy() {
        if (mShareButtonListenerSupplier != null) {
            mShareButtonListenerSupplier.removeObserver(mShareButtonListenerSupplierCallback);
        }
        mMediator.destroy();
        mHomeButton.destroy();
        mShareButton.destroy();
        mSearchAccelerator.destroy();
        mTabSwitcherButtonCoordinator.destroy();
        mMenuButton.destroy();
        mBookmarksButton.destroy();
    }

    /**
     * @param isBookmarked Whether or not the current tab is already bookmarked.
     * @param editingAllowed Whether or not bookmarks can be modified (added, edited, or removed).
     */
    public void updateBookmarkButton(boolean isBookmarked, boolean editingAllowed) {
        if (mBookmarksButton != null) {
            mBookmarksButton.updateBookmarkButton(isBookmarked, editingAllowed);
        }
    }
}
