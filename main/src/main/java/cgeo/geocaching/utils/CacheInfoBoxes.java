package cgeo.geocaching.utils;

import cgeo.geocaching.AttributesGridAdapter;
import cgeo.geocaching.CacheDetailActivity;
import cgeo.geocaching.CacheListActivity;
import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.R;
import cgeo.geocaching.SearchResult;
import cgeo.geocaching.activity.Progress;
import cgeo.geocaching.enumerations.CacheAttribute;
import cgeo.geocaching.enumerations.CacheAttributeCategory;
import cgeo.geocaching.enumerations.CacheType;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.filters.FilterUtils;
import cgeo.geocaching.filters.NamedFilter;
import cgeo.geocaching.list.PseudoList;
import cgeo.geocaching.list.StoredList;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.network.Network;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.settings.SettingsActivity;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.ui.SimpleItemListModel;
import cgeo.geocaching.ui.TextParam;
import cgeo.geocaching.ui.ViewUtils;
import cgeo.geocaching.ui.dialog.SimpleDialog;
import cgeo.geocaching.wherigo.WherigoActivity;
import cgeo.geocaching.wherigo.WherigoUtils;
import cgeo.geocaching.wherigo.WherigoViewUtils;
import static cgeo.geocaching.ui.ViewUtils.showToast;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.ImmutablePair;


public class CacheInfoBoxes {

    private CacheInfoBoxes() {
        // utility class
    }

    public static void updateOfflineBox(final View view, final Geocache cache,
                                        final View.OnClickListener refreshCacheClickListener,
                                        final View.OnClickListener dropCacheClickListener,
                                        final View.OnClickListener storeCacheClickListener,
                                        final View.OnClickListener showHintClickListener,
                                        final View.OnLongClickListener moveCacheListener,
                                        final View.OnLongClickListener storeCachePreselectedListener) {
        if (view == null) {
            return; // fragment already destroyed?
        }

        // offline use
        final TextView offlineText = view.findViewById(R.id.offline_text);
        final View offlineRefresh = view.findViewById(R.id.offline_refresh);
        final View offlineStore = view.findViewById(R.id.offline_store);
        final View offlineDrop = view.findViewById(R.id.offline_drop);
        final View offlineEdit = view.findViewById(R.id.offline_edit);

        // check if hint is available and set onClickListener and hint button visibility accordingly
        final boolean hintButtonEnabled = setOfflineHintText(showHintClickListener, view.findViewById(R.id.offline_hint_text), cache.getHint(), cache.getPersonalNote());
        final View offlineHint = view.findViewById(R.id.offline_hint);
        if (null != offlineHint) {
            if (hintButtonEnabled) {
                offlineHint.setVisibility(View.VISIBLE);
                offlineHint.setClickable(true);
                offlineHint.setOnClickListener(showHintClickListener);
            } else {
                offlineHint.setVisibility(View.GONE);
                offlineHint.setClickable(false);
                offlineHint.setOnClickListener(null);
            }
        }

        offlineStore.setClickable(true);
        offlineStore.setOnClickListener(storeCacheClickListener);
        offlineStore.setOnLongClickListener(storeCachePreselectedListener);

        offlineDrop.setClickable(true);
        offlineDrop.setOnClickListener(dropCacheClickListener);
        offlineDrop.setOnLongClickListener(null);

        offlineEdit.setOnClickListener(storeCacheClickListener);
        if (moveCacheListener != null) {
            offlineEdit.setOnLongClickListener(moveCacheListener);
        }

        offlineRefresh.setVisibility(cache.supportsRefresh() ? View.VISIBLE : View.GONE);
        offlineRefresh.setClickable(true);
        offlineRefresh.setOnClickListener(refreshCacheClickListener);

        if (cache.isOffline()) {
            final long detailedUpdate = cache.getDetailedUpdate();
            // the map popup uses a compact layout and a compact age
            final boolean compact = view.findViewById(R.id.offline_compact_row) != null;
            offlineText.setText(compact ? Formatter.formatStoredAgoCompact(detailedUpdate) : Formatter.formatStoredAgo(detailedUpdate));
            // allow toggling between the relative ("stored ... ago") and the exact date by tapping the text
            offlineText.setTag(Boolean.FALSE);
            offlineText.setClickable(true);
            offlineText.setOnClickListener(v -> {
                final boolean showExact = !Boolean.TRUE.equals(offlineText.getTag());
                offlineText.setTag(showExact);
                offlineText.setText(showExact
                        ? Formatter.formatStoredExact(detailedUpdate)
                        : compact ? Formatter.formatStoredAgoCompact(detailedUpdate) : Formatter.formatStoredAgo(detailedUpdate));
            });

            offlineStore.setVisibility(View.GONE);
            offlineDrop.setVisibility(View.VISIBLE);
            offlineEdit.setVisibility(View.VISIBLE);
        } else {
            offlineText.setText(LocalizationUtils.getString(R.string.cache_offline_not_ready));
            offlineText.setOnClickListener(null);
            offlineText.setClickable(false);
            offlineText.setTag(null);

            offlineStore.setVisibility(View.VISIBLE);
            offlineDrop.setVisibility(View.GONE);
            offlineEdit.setVisibility(View.GONE);
        }
        collapseOfflineActions(view, offlineEdit, offlineRefresh, offlineDrop, moveCacheListener);
    }

    /**
     * When more than one of the lists/refresh/delete buttons applies, show a single menu button
     * instead, so that the "stored ... ago" text gets the room and every row ends in one icon.
     */
    private static void collapseOfflineActions(final View view, final View offlineEdit, final View offlineRefresh, final View offlineDrop,
                                               @Nullable final View.OnLongClickListener moveCacheListener) {
        final View offlineMore = view.findViewById(R.id.offline_more);
        if (offlineMore == null) {
            return;
        }
        final List<Pair<Integer, Runnable>> actions = new ArrayList<>();
        if (offlineEdit.getVisibility() == View.VISIBLE) {
            actions.add(new Pair<>(R.string.menu_lists, offlineEdit::performClick));
            if (moveCacheListener != null) {
                actions.add(new Pair<>(R.string.cache_menu_move_list, () -> moveCacheListener.onLongClick(offlineEdit)));
            }
        }
        if (offlineRefresh.getVisibility() == View.VISIBLE) {
            actions.add(new Pair<>(R.string.cache_offline_refresh, offlineRefresh::performClick));
        }
        if (offlineDrop.getVisibility() == View.VISIBLE) {
            actions.add(new Pair<>(R.string.cache_offline_delete, offlineDrop::performClick));
        }
        if (actions.size() < 2) {
            offlineMore.setVisibility(View.GONE);
            return;
        }
        offlineEdit.setVisibility(View.GONE);
        offlineRefresh.setVisibility(View.GONE);
        offlineDrop.setVisibility(View.GONE);
        offlineMore.setVisibility(View.VISIBLE);
        offlineMore.setOnClickListener(v -> {
            final PopupMenu popup = new PopupMenu(v.getContext(), v);
            for (int i = 0; i < actions.size(); i++) {
                popup.getMenu().add(Menu.NONE, i, i, actions.get(i).first);
            }
            popup.setOnMenuItemClickListener(item -> {
                actions.get(item.getItemId()).second.run();
                return true;
            });
            popup.show();
        });
    }


    public static void updateAttributes(final Geocache cache, final View attributesView, final GridView attributesGrid, final Activity activity) {
        final List<String> attributes = cache.getAttributes();
        if (!CacheAttribute.hasRecognizedAttributeIcon(attributes)) {
            attributesView.setVisibility(View.GONE);
            attributesGrid.setVisibility(View.GONE);
            return;
        }
        // traverse by category and attribute order
        final ArrayList<String> orderedAttributeNames = new ArrayList<>();
        final StringBuilder attributesTextBuilder = new StringBuilder();
        CacheAttributeCategory lastCategory = null;
        for (CacheAttributeCategory category : CacheAttributeCategory.getOrderedCategoryList()) {
            for (CacheAttribute attr : CacheAttribute.getByCategory(category)) {
                for (Boolean enabled : Arrays.asList(false, true, null)) {
                    final String key = attr.getValue(enabled);
                    if (attributes.contains(key)) {
                        if (lastCategory != category) {
                            if (lastCategory != null) {
                                attributesTextBuilder.append("<br /><br />");
                            }
                            attributesTextBuilder.append("<b><u>").append(category.getName()).append("</u></b><br />");
                            lastCategory = category;
                        } else {
                            attributesTextBuilder.append("<br />");
                        }
                        orderedAttributeNames.add(key);
                        attributesTextBuilder.append(attr.getL10n(enabled == null || enabled));
                    }
                }
            }
        }

        final TextView attributesText = attributesView.findViewById(R.id.attributes_text);
        attributesGrid.setAdapter(new AttributesGridAdapter(activity, orderedAttributeNames, () -> toggleAttributesView(attributesText, attributesGrid)));
        attributesGrid.setVisibility(View.VISIBLE);

        if (attributesText != null) {
            attributesText.setText(HtmlCompat.fromHtml(attributesTextBuilder.toString(), 0));
            attributesText.setVisibility(View.GONE);
            attributesText.setOnClickListener(v -> toggleAttributesView(attributesText, attributesGrid));
        }
    }

    private static void toggleAttributesView(final TextView attributesText, final GridView attributesGrid) {
        attributesText.setVisibility(attributesText.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        attributesGrid.setVisibility(attributesGrid.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    @SuppressLint("SetTextI18n")
    private static boolean setOfflineHintText(final View.OnClickListener showHintClickListener, final TextView offlineHintTextView, final String hint, final String personalNote) {
        if (null != showHintClickListener) {
            final boolean hintGiven = StringUtils.isNotEmpty(hint);
            final boolean personalNoteGiven = StringUtils.isNotEmpty(personalNote);
            if (hintGiven || personalNoteGiven) {
                offlineHintTextView.setText((hintGiven ? hint + (personalNoteGiven ? "\r\n" : "") : "") + (personalNoteGiven ? personalNote : ""));
                return true;
            }
        }
        return false;
    }

    public static void updateCacheListsAndMatchingFilters(final View view, final Geocache cache, @Nullable final CacheDetailActivity cacheDetailActivity) {
        if (view.findViewById(R.id.offline_compact_row) != null) {
            updateCompactListsAndMatchingFilters(view, cache);
            return;
        }
        final SpannableStringBuilder builder = new SpannableStringBuilder();

        // only display "Lists: ..." if the cache is actually stored offline (i.e. part of at least one list)
        if (cache.isOffline()) {
            final SpannableStringBuilder listsBuilder = new SpannableStringBuilder();
            for (final Integer listId : cache.getLists()) {
                if (listsBuilder.length() > 0) {
                    listsBuilder.append(", ");
                }
                appendClickableList(listsBuilder, view, listId, cacheDetailActivity);
            }
            listsBuilder.insert(0, LocalizationUtils.getString(R.string.list_list_headline) + " ");
            builder.append(listsBuilder);
        }

        final TextView offlineLists = view.findViewById(R.id.offline_lists);
        offlineLists.setText(builder);
        offlineLists.setMovementMethod(LinkMovementMethod.getInstance());
        offlineLists.setVisibility(builder.length() > 0 ? View.VISIBLE : View.GONE);

        final View markerBox = view.findViewById(R.id.namedfilter_box);
        if (markerBox != null) {
            final Context context = view.getContext();
            final boolean enableMarkerButton = context != null && Settings.getNamedFilterDisplayMode() != Settings.NamedFilterDisplayMode.NONE;
            markerBox.setVisibility(enableMarkerButton ? View.VISIBLE : View.GONE);
            
            final View marker = view.findViewById(R.id.marker_button);
            if (marker != null && enableMarkerButton) {
                FilterUtils.registerFilterActivateDeactivateButton(context, marker);
            }
        }

        final SpannableStringBuilder markerBuilder = new SpannableStringBuilder();
        appendMatchingNamedFilters(markerBuilder, cache, cacheDetailActivity);

        final TextView matchingMarkers = view.findViewById(R.id.marker_text);
        if (matchingMarkers != null) {
            matchingMarkers.setText(markerBuilder);
            matchingMarkers.setMovementMethod(LinkMovementMethod.getInstance());
            matchingMarkers.setVisibility(markerBuilder.length() > 0 ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Appends the named filters matching {@code cache} to {@code builder} (preceded by a line break if
     * {@code builder} is not empty), depending on {@link Settings#getNamedFilterDisplayMode()}.
     * Active filters are shown in the standard link color, inactive (passive) ones grayed out.
     * The filter name is clickable (its optional marker prefix is not).
     */
    private static void appendMatchingNamedFilters(final SpannableStringBuilder builder, final Geocache cache, final CacheDetailActivity cacheDetailActivity) {
        final Settings.NamedFilterDisplayMode mode = Settings.getNamedFilterDisplayMode();
        if (mode == Settings.NamedFilterDisplayMode.NONE) {
            return;
        }

        final SpannableStringBuilder filtersBuilder = new SpannableStringBuilder();

        final ImmutablePair<List<NamedFilter>, List<NamedFilter>> matches = NamedFilter.getFiltersMatchingCache(cache, mode == Settings.NamedFilterDisplayMode.ACTIVE_ONLY, -1);
        final List<NamedFilter> activeFilters = matches.left;
        final List<NamedFilter> passiveFilters = matches.right;
        if (activeFilters.isEmpty() && passiveFilters.isEmpty()) {
            filtersBuilder.insert(0, LocalizationUtils.getString(R.string.filters_list_empty));
        } else {
            for (final NamedFilter filter : activeFilters) {
                appendNamedFilter(filtersBuilder, filter, !Settings.isConditionalCacheMarkersEnabled(), cacheDetailActivity);
            }
            for (final NamedFilter filter : passiveFilters) {
                appendNamedFilter(filtersBuilder, filter, true, cacheDetailActivity);
            }
            filtersBuilder.insert(0, LocalizationUtils.getString(R.string.filters_list_headline) + " ");
        }

        builder.append(filtersBuilder);
    }

    private static void appendNamedFilter(final SpannableStringBuilder builder, final NamedFilter filter, final boolean inactive, final CacheDetailActivity cacheDetailActivity) {
        if (builder.length() > 0) {
            builder.append(", ");
        }

        if (StringUtils.isNotBlank(filter.getMarkerId())) {
            builder.append(filter.getMarkerId()).append(" ");
        }
        final int start = builder.length();
        builder.append(filter.getName());
        builder.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull final View widget) {
                Settings.setLastDisplayedList(PseudoList.ALL_LIST.id);
                if (cacheDetailActivity != null) {
                    cacheDetailActivity.setNeedsRefresh();
                }
                CacheListActivity.startActivityOffline(widget.getContext(), filter);
            }
        }, start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (inactive) {
            builder.setSpan(new ForegroundColorSpan(ContextCompat.getColor(CgeoApplication.getInstance(), R.color.colorTextHint)), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /**
     * Map popup variant: "Stored + N lists" (or the only list's name, or "N lists") and "Matches N filters",
     * where the counts open a chooser. Keeps both texts short and finger-sized.
     */
    private static void updateCompactListsAndMatchingFilters(final View view, final Geocache cache) {
        final Context context = view.getContext();

        // lists
        final TextView offlineLists = view.findViewById(R.id.offline_lists);
        final SpannableStringBuilder lists = new SpannableStringBuilder();
        if (cache.isOffline()) {
            final List<Integer> listIds = new ArrayList<>(cache.getLists());
            if (listIds.remove(Integer.valueOf(StoredList.STANDARD_LIST_ID))) {
                appendClickable(lists, DataStore.getList(StoredList.STANDARD_LIST_ID).getTitle(), () -> openList(context, StoredList.STANDARD_LIST_ID));
                if (!listIds.isEmpty()) {
                    lists.append(" + ");
                    appendClickable(lists, LocalizationUtils.getPlural(R.plurals.popup_list_count, listIds.size()), () -> chooseList(context, listIds));
                }
            } else if (listIds.size() == 1) {
                final int listId = listIds.get(0);
                appendClickable(lists, DataStore.getList(listId).getTitle(), () -> openList(context, listId));
            } else if (!listIds.isEmpty()) {
                appendClickable(lists, LocalizationUtils.getPlural(R.plurals.popup_list_count, listIds.size()), () -> chooseList(context, listIds));
            }
        }
        offlineLists.setText(lists);
        offlineLists.setMovementMethod(LinkMovementMethod.getInstance());
        offlineLists.setVisibility(lists.length() > 0 ? View.VISIBLE : View.GONE);

        // matching named filters
        final View marker = view.findViewById(R.id.marker_button);
        final TextView matching = view.findViewById(R.id.marker_text);
        final Settings.NamedFilterDisplayMode mode = Settings.getNamedFilterDisplayMode();
        final boolean showFilters = mode != Settings.NamedFilterDisplayMode.NONE;
        if (marker != null) {
            marker.setVisibility(showFilters ? View.VISIBLE : View.GONE);
            if (showFilters) {
                FilterUtils.registerFilterActivateDeactivateButton(context, marker);
            }
        }
        final View divider = view.findViewById(R.id.offline_filters_divider);
        if (divider != null) {
            divider.setVisibility(showFilters ? View.VISIBLE : View.GONE);
        }
        if (matching == null) {
            return;
        }
        if (!showFilters) {
            matching.setVisibility(View.GONE);
            return;
        }
        final ImmutablePair<List<NamedFilter>, List<NamedFilter>> matches = NamedFilter.getFiltersMatchingCache(cache, mode == Settings.NamedFilterDisplayMode.ACTIVE_ONLY, -1);
        final List<NamedFilter> filters = new ArrayList<>(matches.left);
        filters.addAll(matches.right);
        final SpannableStringBuilder text = new SpannableStringBuilder();
        if (filters.isEmpty()) {
            text.append(LocalizationUtils.getString(R.string.popup_filters_none));
        } else {
            text.append(LocalizationUtils.getString(R.string.popup_filters_matches)).append(" ");
            appendClickable(text, LocalizationUtils.getPlural(R.plurals.popup_filter_count, filters.size()), () -> chooseFilter(context, filters));
        }
        matching.setText(text);
        matching.setMovementMethod(LinkMovementMethod.getInstance());
        matching.setVisibility(View.VISIBLE);
    }

    private static void appendClickable(final SpannableStringBuilder builder, final String text, final Runnable action) {
        final int start = builder.length();
        builder.append(text);
        builder.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull final View widget) {
                action.run();
            }
        }, start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void openList(final Context context, final int listId) {
        Settings.setLastDisplayedList(listId);
        CacheListActivity.startActivityOffline(context, null);
    }

    private static void chooseList(final Context context, final List<Integer> listIds) {
        final SimpleDialog.ItemSelectModel<Integer> model = new SimpleDialog.ItemSelectModel<>();
        model.setItems(listIds)
                .setDisplayMapper(id -> TextParam.text(DataStore.getList(id).getTitle()))
                .setChoiceMode(SimpleItemListModel.ChoiceMode.SINGLE_PLAIN);
        SimpleDialog.ofContext(context).setTitle(TextParam.id(R.string.popup_lists_title)).selectSingle(model, id -> openList(context, id));
    }

    private static void chooseFilter(final Context context, final List<NamedFilter> filters) {
        final SimpleDialog.ItemSelectModel<NamedFilter> model = new SimpleDialog.ItemSelectModel<>();
        model.setItems(filters)
                .setDisplayMapper(f -> TextParam.text(StringUtils.isNotBlank(f.getMarkerId()) ? f.getMarkerId() + " " + f.getName() : f.getName()))
                .setChoiceMode(SimpleItemListModel.ChoiceMode.SINGLE_PLAIN);
        SimpleDialog.ofContext(context).setTitle(TextParam.id(R.string.popup_filters_title)).selectSingle(model, filter -> {
            Settings.setLastDisplayedList(PseudoList.ALL_LIST.id);
            CacheListActivity.startActivityOffline(context, filter);
        });
    }

    private static void appendClickableList(final SpannableStringBuilder builder, final View view, final Integer listId, @Nullable final CacheDetailActivity cacheDetailActivity) {
        final StoredList list = DataStore.getList(listId);
        if (StringUtils.isNotBlank(list.emojiMarker)) {
            builder.append(list.emojiMarker).append(" ");
        }
        final int start = builder.length();
        builder.append(list.getTitle());
        builder.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull final View widget) {
                Settings.setLastDisplayedList(listId);
                if (cacheDetailActivity != null) {
                    cacheDetailActivity.setNeedsRefresh();
                }
                CacheListActivity.startActivityOffline(view.getContext(), null);
            }
        }, start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * Loads a cache from the server in the background if the given condition is true and a network connection is available.
     * Shows an indeterminate progress dialog on {@code activity} while loading.
     * Calls {@code callback} with the freshly loaded cache on the main thread.
     * If loading is not needed, {@code callback} is called with the original {@code cache} instead.
     * If loading fails (missing network connectivity or server error), a toast is shown and {@code callback} is not called.
     *
     * @param activity  the activity used to show the progress dialog
     * @param needsLoad whether a server load is required
     * @param cache     the cache to potentially reload
     * @param callback  action to execute (on the main thread) with the resulting cache
     */
    private static void fetchCacheIfNeededAndCall(final Activity activity, final boolean needsLoad, final Geocache cache, final Consumer<Geocache> callback) {
        if (needsLoad) {
            if (Network.isConnected()) {
                final Progress progress = new Progress();
                progress.show(activity, LocalizationUtils.getString(R.string.cache_wherigo_no_cartridge_fetch), "", true, null);
                AndroidRxUtils.networkScheduler.scheduleDirect(() -> {
                    Geocache loaded = null;
                    final SearchResult result = Geocache.searchByGeocode(cache.getGeocode(), null, false, null);
                    if (result != null) {
                        loaded = result.getFirstCacheFromResult(LoadFlags.LOAD_CACHE_OR_DB);
                    }
                    final Geocache effectiveCache = loaded;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        progress.dismiss();
                        if (effectiveCache != null) {
                            callback.accept(effectiveCache);
                        } else {
                            showToast(activity, R.string.err_load_descr_failed);
                        }
                    });
                });
            } else {
                showToast(activity, R.string.err_load_descr_failed);
            }
        } else {
            new Handler(Looper.getMainLooper()).post(() -> {
                callback.accept(cache);
            });
        }
    }


    public static void updateWherigoBox(final Geocache cache, final Activity activity, @NonNull final Button wherigoButton, @Nullable final View wherigoView, @Nullable final TextView wherigoText) {
        final List<String> wherigoGuids = WherigoUtils.getWherigoGuids(cache);
        final boolean wherigoGuidsAvailable = !wherigoGuids.isEmpty();
        final boolean isEnabled = wherigoGuidsAvailable || cache.getType() == CacheType.WHERIGO;

        ViewUtils.setVisibility(wherigoView, isEnabled ? View.VISIBLE : View.GONE);

        ViewUtils.setVisibility(wherigoText, isEnabled ? View.VISIBLE : View.GONE);
        ViewUtils.setText(wherigoText, (!isEnabled || Settings.hasGCCredentials()) ? R.string.cache_wherigo_start : R.string.cache_wherigo_credentials);

        ViewUtils.setVisibility(wherigoButton, isEnabled ? View.VISIBLE : View.GONE);
        if (isEnabled) {
            wherigoButton.setOnClickListener(v -> {
                if (Settings.hasGCCredentials()) {
                    fetchCacheIfNeededAndCall(
                            activity,
                            !wherigoGuidsAvailable && !cache.isDetailed(),
                            cache,
                            currentCache -> {
                                final List<String> guids = wherigoGuidsAvailable ? wherigoGuids : WherigoUtils.getWherigoGuids(currentCache);
                                if (guids.isEmpty()) {
                                    SimpleDialog.of(activity)
                                            .setTitle(TextParam.id(R.string.cache_wherigo_no_cartridge_title))
                                            .setMessage(TextParam.id(R.string.cache_wherigo_no_cartridge_message))
                                            .show();
                                    return;
                                }
                                WherigoViewUtils.executeForOneCartridge(activity, guids, guid ->
                                        WherigoActivity.startForGuid(activity, guid, currentCache.getGeocode(), true));
                            });
                } else {
                    SettingsActivity.openForScreen(R.string.preference_screen_gc, activity);
                }
            });
        }
    }

    public static void updateChirpWolfBox(final Geocache cache, final Activity activity, @NonNull final Button chirpButton, @Nullable final View chirpView, @Nullable final TextView chirpText) {
        final Intent chirpWolf = ProcessUtils.getLaunchIntent(LocalizationUtils.getPlainString(R.string.package_chirpwolf));
        final String compare = CacheAttribute.WIRELESSBEACON.getValue(true);
        boolean isEnabled = false;
        for (String current : cache.getAttributes()) {
            if (Strings.CS.equals(current, compare)) {
                isEnabled = true;
                break;
            }
        }

        ViewUtils.setVisibility(chirpView, isEnabled ? View.VISIBLE : View.GONE);

        ViewUtils.setVisibility(chirpText, isEnabled ? View.VISIBLE : View.GONE);
        ViewUtils.setText(chirpText, chirpWolf != null ? R.string.cache_chirpwolf_start : R.string.cache_chirpwolf_install);

        ViewUtils.setVisibility(chirpButton, isEnabled ? View.VISIBLE : View.GONE);
        if (isEnabled) {
            chirpButton.setOnClickListener(v -> {
                // re-check installation state, might have changed since creating the view
                final Intent chirpWolf2 = ProcessUtils.getLaunchIntent(LocalizationUtils.getPlainString(R.string.package_chirpwolf));
                if (chirpWolf2 != null) {
                    chirpWolf2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(chirpWolf2);
                } else {
                    ProcessUtils.openMarket(activity, LocalizationUtils.getPlainString(R.string.package_chirpwolf));
                }
            });
        }
    }

    public static void updateALCBox(final Geocache cache, final Activity activity, @NonNull final Button alcButton, @Nullable final View alcView, @Nullable final TextView alcText) {
        final boolean isLabListing = CacheInfoBoxes.isLabAdventure(cache);
        final boolean isEnabled = isLabListing || (cache.getType() == CacheType.MYSTERY && CacheInfoBoxes.findAdvLabUrl(cache) != null);

        ViewUtils.setVisibility(alcView, isEnabled ? View.VISIBLE : View.GONE);

        ViewUtils.setVisibility(alcText, isEnabled ? View.VISIBLE : View.GONE);
        ViewUtils.setText(alcText, isLabPlayerInstalled() ? (isLabListing ? R.string.cache_alc_start : R.string.cache_alc_related_start) : R.string.cache_alc_install);

        ViewUtils.setVisibility(alcButton, isEnabled ? View.VISIBLE : View.GONE);
        if (isEnabled) {
            alcButton.setOnClickListener(v -> {
                // re-check installation state, might have changed since creating the view
                final String url = isLabListing ? cache.getUrl() : CacheInfoBoxes.findAdvLabUrl(cache);
                if (isLabPlayerInstalled() && StringUtils.isNotBlank(url)) {
                    final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                } else {
                    ProcessUtils.openMarket(activity, LocalizationUtils.getPlainString(R.string.package_alc));
                }
            });
        }
    }

    private static boolean isLabAdventure(@NonNull final Geocache cache) {
        return cache.getType() == CacheType.ADVLAB && StringUtils.isNotEmpty(cache.getUrl());
    }

    private static boolean isLabPlayerInstalled() {
        return null != ProcessUtils.getLaunchIntent(LocalizationUtils.getPlainString(R.string.package_alc));
    }

    /**
     * Find links to Adventure Labs in Listing of a cache. Returns URL if exactly 1 link target is found, else null.
     * 3 types of URLs possible: https://adventurelab.page.link/Cw3L, https://labs.geocaching.com/goto/Theater, https://labs.geocaching.com/goto/a4b45b7b-fa76-4387-a54f-045875ffee0c
     */
    @Nullable
    private static String findAdvLabUrl(final Geocache cache) {
        final Pattern patternAdvLabUrl = Pattern.compile("(https?://labs.geocaching.com/goto/[a-zA-Z0-9-_]{1,36}|https?://adventurelab.page.link/[a-zA-Z0-9]{4})");
        final Matcher matcher = patternAdvLabUrl.matcher(cache.getShortDescription() + " " + cache.getDescription());
        final Set<String> urls = new HashSet<>();
        while (matcher.find()) {
            urls.add(matcher.group(1));
        }
        if (urls.size() == 1) {
            return urls.iterator().next();
        }
        return null;
    }
}
