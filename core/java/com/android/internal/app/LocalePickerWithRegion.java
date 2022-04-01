/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.app;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.content.Context;
import android.os.Bundle;
import android.os.LocaleList;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SearchView;

import com.android.internal.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A two-step locale picker. It shows a language, then a country.
 *
 * <p>It shows suggestions at the top, then the rest of the locales.
 * Allows the user to search for locales using both their native name and their name in the
 * default locale.</p>
 */
public class LocalePickerWithRegion extends ListFragment implements SearchView.OnQueryTextListener {
    private static final String TAG = LocalePickerWithRegion.class.getSimpleName();
    private static final String PARENT_FRAGMENT_NAME = "localeListEditor";

    private SuggestedLocaleAdapter mAdapter;
    private LocaleSelectedListener mListener;
    private Set<LocaleStore.LocaleInfo> mLocaleList;
    private LocaleStore.LocaleInfo mParentLocale;
    private boolean mTranslatedOnly = false;
    private SearchView mSearchView = null;
    private CharSequence mPreviousSearch = null;
    private boolean mPreviousSearchHadFocus = false;
    private int mFirstVisiblePosition = 0;
    private int mTopDistance = 0;
    private String mAppPackageName;

    /**
     * Other classes can register to be notified when a locale was selected.
     *
     * <p>This is the mechanism to "return" the result of the selection.</p>
     */
    public interface LocaleSelectedListener {
        /**
         * The classes that want to retrieve the locale picked should implement this method.
         * @param locale    the locale picked.
         */
        void onLocaleSelected(LocaleStore.LocaleInfo locale);
    }

    private static LocalePickerWithRegion createCountryPicker(Context context,
            LocaleSelectedListener listener, LocaleStore.LocaleInfo parent,
            boolean translatedOnly, String appPackageName) {
        LocalePickerWithRegion localePicker = new LocalePickerWithRegion();
        boolean shouldShowTheList = localePicker.setListener(context, listener, parent,
                translatedOnly, appPackageName);
        return shouldShowTheList ? localePicker : null;
    }

    public static LocalePickerWithRegion createLanguagePicker(Context context,
            LocaleSelectedListener listener, boolean translatedOnly) {
        LocalePickerWithRegion localePicker = new LocalePickerWithRegion();
        localePicker.setListener(context, listener, /* parent */ null, translatedOnly, null);
        return localePicker;
    }

    public static LocalePickerWithRegion createLanguagePicker(Context context,
            LocaleSelectedListener listener, boolean translatedOnly, String appPackageName) {
        LocalePickerWithRegion localePicker = new LocalePickerWithRegion();
        localePicker.setListener(
                context, listener, /* parent */ null, translatedOnly, appPackageName);
        return localePicker;
    }

    /**
     * Sets the listener and initializes the locale list.
     *
     * <p>Returns true if we need to show the list, false if not.</p>
     *
     * <p>Can return false because of an error, trying to show a list of countries,
     * but no parent locale was provided.</p>
     *
     * <p>It can also return false if the caller tries to show the list in country mode and
     * there is only one country available (i.e. Japanese => Japan).
     * In this case we don't even show the list, we call the listener with that locale,
     * "pretending" it was selected, and return false.</p>
     */
    private boolean setListener(Context context, LocaleSelectedListener listener,
            LocaleStore.LocaleInfo parent, boolean translatedOnly, String appPackageName) {
        this.mParentLocale = parent;
        this.mListener = listener;
        this.mTranslatedOnly = translatedOnly;
        this.mAppPackageName = appPackageName;
        setRetainInstance(true);

        final HashSet<String> langTagsToIgnore = new HashSet<>();
        LocaleStore.LocaleInfo appCurrentLocale =
                LocaleStore.getAppCurrentLocaleInfo(context, appPackageName);
        boolean isForCountryMode = parent != null;

        if (!TextUtils.isEmpty(appPackageName) && !isForCountryMode) {
            if (appCurrentLocale != null) {
                Log.d(TAG, "appCurrentLocale: " + appCurrentLocale.getLocale().toLanguageTag());
                langTagsToIgnore.add(appCurrentLocale.getLocale().toLanguageTag());
            } else {
                Log.d(TAG, "appCurrentLocale is null");
            }
        } else if (!translatedOnly) {
            final LocaleList userLocales = LocalePicker.getLocales();
            final String[] langTags = userLocales.toLanguageTags().split(",");
            Collections.addAll(langTagsToIgnore, langTags);
        }

        if (isForCountryMode) {
            mLocaleList = LocaleStore.getLevelLocales(context,
                    langTagsToIgnore, parent, translatedOnly);
            if (mLocaleList.size() <= 1) {
                if (listener != null && (mLocaleList.size() == 1)) {
                    listener.onLocaleSelected(mLocaleList.iterator().next());
                }
                return false;
            }
        } else {
            mLocaleList = LocaleStore.getLevelLocales(context, langTagsToIgnore,
                    null /* no parent */, translatedOnly);
        }
        Log.d(TAG, "mLocaleList size:  " + mLocaleList.size());

        // Adding current locale and system default option into suggestion list
        if(!TextUtils.isEmpty(appPackageName)) {
            if (appCurrentLocale != null && !isForCountryMode) {
                mLocaleList.add(appCurrentLocale);
            }
            mLocaleList = filterTheLanguagesNotSupportedInApp(context, appPackageName);

            if (!isForCountryMode) {
                mLocaleList.add(LocaleStore.getSystemDefaultLocaleInfo(appCurrentLocale == null));
            }
        }
        return true;
    }

    private Set<LocaleStore.LocaleInfo> filterTheLanguagesNotSupportedInApp(
            Context context, String appPackageName) {
        ArrayList<Locale> supportedLocales =
                AppLocaleStore.getAppSupportedLocales(context, appPackageName);

        Set<LocaleStore.LocaleInfo> filteredList = new HashSet<>();
        for(LocaleStore.LocaleInfo li: mLocaleList) {
            for(Locale l: supportedLocales) {
                if(LocaleList.matchesLanguageAndScript(li.getLocale(), l)) {
                    filteredList.add(li);
                }
            }
        }
        Log.d(TAG, "mLocaleList after app-supported filter:  " + filteredList.size());

        return filteredList;
    }

    private void returnToParentFrame() {
        getFragmentManager().popBackStack(PARENT_FRAGMENT_NAME,
                FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        if (mLocaleList == null) {
            // The fragment was killed and restored by the FragmentManager.
            // At this point we have no data, no listener. Just return, to prevend a NPE.
            // Fixes b/28748150. Created b/29400003 for a cleaner solution.
            returnToParentFrame();
            return;
        }

        final boolean countryMode = mParentLocale != null;
        final Locale sortingLocale = countryMode ? mParentLocale.getLocale() : Locale.getDefault();
        mAdapter = new SuggestedLocaleAdapter(mLocaleList, countryMode, mAppPackageName);
        final LocaleHelper.LocaleInfoComparator comp =
                new LocaleHelper.LocaleInfoComparator(sortingLocale, countryMode);
        mAdapter.sort(comp);
        setListAdapter(mAdapter);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // In order to make the list view work with CollapsingToolbarLayout,
        // we have to enable the nested scrolling feature of the list view.
        getListView().setNestedScrollingEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int id = menuItem.getItemId();
        switch (id) {
            case android.R.id.home:
                getFragmentManager().popBackStack();
                return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mParentLocale != null) {
            getActivity().setTitle(mParentLocale.getFullNameNative());
        } else {
            getActivity().setTitle(R.string.language_selection_title);
        }

        getListView().requestFocus();
    }

    @Override
    public void onPause() {
        super.onPause();

        // Save search status
        if (mSearchView != null) {
            mPreviousSearchHadFocus = mSearchView.hasFocus();
            mPreviousSearch = mSearchView.getQuery();
        } else {
            mPreviousSearchHadFocus = false;
            mPreviousSearch = null;
        }

        // Save scroll position
        final ListView list = getListView();
        final View firstChild = list.getChildAt(0);
        mFirstVisiblePosition = list.getFirstVisiblePosition();
        mTopDistance = (firstChild == null) ? 0 : (firstChild.getTop() - list.getPaddingTop());
    }

    @Override
    public void onListItemClick(ListView parent, View v, int position, long id) {
        final LocaleStore.LocaleInfo locale =
                (LocaleStore.LocaleInfo) parent.getAdapter().getItem(position);
        // Special case for resetting the app locale to equal the system locale.
        boolean isSystemLocale = locale.isSystemLocale();
        boolean isRegionLocale = locale.getParent() != null;

        if (isSystemLocale || isRegionLocale) {
            if (mListener != null) {
                mListener.onLocaleSelected(locale);
            }
            returnToParentFrame();
        } else {
            LocalePickerWithRegion selector = LocalePickerWithRegion.createCountryPicker(
                    getContext(), mListener, locale, mTranslatedOnly /* translate only */,
                    mAppPackageName);
            if (selector != null) {
                getFragmentManager().beginTransaction()
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .replace(getId(), selector).addToBackStack(null)
                        .commit();
            } else {
                returnToParentFrame();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mParentLocale == null) {
            inflater.inflate(R.menu.language_selection_list, menu);

            final MenuItem searchMenuItem = menu.findItem(R.id.locale_search_menu);
            mSearchView = (SearchView) searchMenuItem.getActionView();

            mSearchView.setQueryHint(getText(R.string.search_language_hint));
            mSearchView.setOnQueryTextListener(this);

            // Restore previous search status
            if (!TextUtils.isEmpty(mPreviousSearch)) {
                searchMenuItem.expandActionView();
                mSearchView.setIconified(false);
                mSearchView.setActivated(true);
                if (mPreviousSearchHadFocus) {
                    mSearchView.requestFocus();
                }
                mSearchView.setQuery(mPreviousSearch, true /* submit */);
            } else {
                mSearchView.setQuery(null, false /* submit */);
            }

            // Restore previous scroll position
            getListView().setSelectionFromTop(mFirstVisiblePosition, mTopDistance);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (mAdapter != null) {
            mAdapter.getFilter().filter(newText);
        }
        return false;
    }
}
