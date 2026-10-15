package cgeo.geocaching.utils;

import cgeo.geocaching.R;
import cgeo.geocaching.activity.ActivityMixin;
import cgeo.geocaching.network.Network;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.ui.ImageParam;
import cgeo.geocaching.utils.html.HtmlUtils;
import cgeo.geocaching.utils.offlinetranslate.TranslateAccessor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/** Utilities used for (c:geo-external) translation */
public final class TranslationUtils {

    public enum Translator {
        NONE(R.string.translate_external_none, null, null),
        EXTERNAL_APP(R.string.translate_external_app, APP_PACKAGE_ANYAPP, null),
        GOOGLE(R.string.translate_external_google, "com.google.android.apps.translate", WEB_GOOGLE_URL),
        DEEPL(R.string.translate_external_deepl, "com.deepl.mobiletranslator", WEB_DEEPL_URL),
        DAVIDV(R.string.translate_external_davidv, "dev.davidv.translator", null);

        private final int nameId;
        public final String appPackageName;
        public final String urlPattern;

        Translator(final int nameId, final String appPackageName, final String urlPattern) {
            this.nameId = nameId;
            this.appPackageName = appPackageName;
            this.urlPattern = urlPattern;
        }

        /** the translator's name without the app/website variant, e.g. "Google Translate" */
        public String getShortName() {
            return LocalizationUtils.getString(nameId);
        }

        public String toUserDisplayableString() {
            final StringBuilder sb = new StringBuilder(LocalizationUtils.getString(nameId));
            if (this.appPackageName != null && !APP_PACKAGE_ANYAPP.equals(this.appPackageName)) {
                sb
                    .append(" (")
                    .append(LocalizationUtils.getString(appIsAvailable(this.appPackageName) ? R.string.translate_external_variant_app :
                        (this.urlPattern != null ? R.string.translate_external_variant_web : R.string.translate_external_variant_unavailable)))
                    .append(")");
            }
            return sb.toString();
        }

        @Override
        @NonNull
        public String toString() {
            return name() + "/appPackage:" + appPackageName + "(av=" + appIsAvailable(appPackageName) + ")";
        }

        @NonNull
        public ImageParam getIcon() {
            if (appIsAvailable(appPackageName)) {
                final Drawable icon = ProcessUtils.getApplicationIcon(appPackageName);
                if (icon != null) {
                    return ImageParam.drawable(icon).setNullifyTintList(true);
                }
            }
            return ImageParam.id(R.drawable.ic_menu_translate);
        }
    }

    //special package name for "any app"
    private static final String APP_PACKAGE_ANYAPP = "ANY_APP";

    //URLs for Web translations. Following parameters will be replaced:
    // %1 (String) gives the URL-encoded text to translate (spaces will be replaced with +)
    // %2 (String) gives the URL-encoded text (spaces will be replaced with %20)
    // %3 (String) gives the target language code (2-digit-ISO)
    private static final String WEB_GOOGLE_URL = "https://translate.google.com/m/translate?vi=c#auto|%3$s|%1$s";
    private static final String WEB_DEEPL_URL = "https://www.deepl.com/de/translator/q/xx/%1$s";

    private static final int TRANSLATION_WEB_TEXT_LENGTH_WARN = 500;

    private TranslationUtils() {
        // utility class
    }

    public static boolean isEnabled() {
        return Settings.getTranslatorExternal() != Translator.NONE;
    }

    @NonNull
    public static CharSequence getTranslationLabel() {
        return LocalizationUtils.getString(R.string.translate_external_label, getTranslationName());
    }

    @NonNull
    public static CharSequence getTranslationName() {
        return getTranslator().toUserDisplayableString();
    }

    @NonNull
    public static ImageParam getTranslationIcon() {
        return getDistinctTranslationIcon();
    }

    /**
     * Sets a menu item's icon to {@link #getDistinctTranslationIcon()}. Our own icons are tinted like the menu's
     * other icons; an app's icon is set as it is.
     */
    public static void applyTranslationIcon(@NonNull final Context context, @Nullable final MenuItem item) {
        if (item == null) {
            return;
        }
        final ImageParam icon = getDistinctTranslationIcon();
        if (icon.isReferencedById()) {
            item.setIcon(getFallbackTranslationIconId(getTranslator()));
            // the menu tinted its icons when it was created; this one is set later
            MenuUtils.tintMenuIcon(item);
        } else {
            item.setIcon(icon.getAsDrawable(context));
        }
    }

    /** without the translator's app: a globe for its website, else an external link */
    @DrawableRes
    private static int getFallbackTranslationIconId(@NonNull final Translator translator) {
        return translator.urlPattern != null ? R.drawable.ic_globe : R.drawable.ic_external_link;
    }

    /**
     * The icon for the external translation, used wherever it appears, so it differs from offline translation: the app's icon (its monochrome
     * "themed icon" layer where the app has one, Android 13+), a globe for the website, else an external link.
     */
    @NonNull
    public static ImageParam getDistinctTranslationIcon() {
        final Translator translator = getTranslator();
        final ImageParam icon = translator.getIcon();
        if (icon.isReferencedById()) {
            return ImageParam.id(getFallbackTranslationIconId(translator));
        }
        final Drawable appIcon = icon.getAsDrawable();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && appIcon instanceof AdaptiveIconDrawable
                && ((AdaptiveIconDrawable) appIcon).getMonochrome() != null) {
            // the layer has the adaptive icon's safe zone around it: enlarge it to the visible part, tint it like our icons
            return ImageParam.drawable(new InsetDrawable(((AdaptiveIconDrawable) appIcon).getMonochrome(), -0.25f));
        }
        return icon;
    }

    @NonNull
    public static String getTranslationShortName() {
        return getTranslator().getShortName();
    }

    public static void translate(final Activity activity, final String text) {
        final Translator translator = getTranslator();
        if (APP_PACKAGE_ANYAPP.equals(translator.appPackageName) || appIsAvailable(translator.appPackageName)) {
            startTranslateViaApp(activity, translator.appPackageName, text);
        } else if (translator.urlPattern != null) {
            startTranslateViaUrl(activity, translator.urlPattern, text);
        } else {
            //just open app selector
            startTranslateViaApp(activity, null, text);
        }
    }

    public static String prepareForTranslation(final CharSequence ... text) {
        return Arrays.stream(text).filter(StringUtils::isNotBlank).map(HtmlUtils::extractText).collect(Collectors.joining("\n\n"));
    }

    public static void registerTranslation(final Activity activity, final Button button, final Supplier<String> textSupplier) {
        registerTranslation(activity, button, null, null, textSupplier);
    }

    public static void registerTranslation(final Activity activity, final Button button, final View box, final TextView label,
           final Supplier<String> textSupplier) {

        final View realBox = box != null ? box : button;
        realBox.setVisibility(isEnabled() ? View.VISIBLE : View.GONE);
        if (label != null) {
            label.setText(getTranslationLabel());
        }
        getDistinctTranslationIcon().applyToIcon(button);
        button.setOnClickListener(v -> {
            final String text = textSupplier.get();
            translate(activity, text);
        });
        if (isEnabled() && box != null && !Settings.getLanguagesToNotTranslate().isEmpty()) {
            TranslateAccessor.get().guessLanguage(textSupplier.get(), lng -> {
                if (Settings.getLanguagesToNotTranslate().contains(lng)) {
                    realBox.setVisibility(View.GONE);
                }
            }, e -> {
                //ignore
            });
        }
    }

    private static Translator getTranslator() {
        return Settings.getTranslatorExternal();
    }

    private static void startTranslateViaUrl(final Activity activity, final String urlPattern, final String text) {
        if (text.length() > TRANSLATION_WEB_TEXT_LENGTH_WARN) {
            ActivityMixin.showToast(activity, R.string.translate_length_warning);
        }
        //build URL
        final String encodedText = Network.encode(text);
        final String encodedTextWithSpace = Strings.CS.replace(encodedText, "+", "%20");
        final String toLang = Locale.getDefault().getLanguage();
        final String url = String.format(urlPattern, encodedText, encodedTextWithSpace, toLang);
        //call actionView for URL
        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private static boolean appIsAvailable(final String packageName) {
        return ProcessUtils.isLaunchable(packageName);
    }

    private static void startTranslateViaApp(final Activity activity, final String packageName, final String text) {
        final Intent intent = new Intent();
        intent.setType("text/plain");
        // intent.setAction(Intent.ACTION_TRANSLATE); doesn't seem to work
        intent.setAction(Intent.ACTION_PROCESS_TEXT);
        intent.putExtra(Intent.EXTRA_PROCESS_TEXT, text);
        intent.putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
        if (packageName != null && !Objects.equals(APP_PACKAGE_ANYAPP, packageName)) {
            intent.setPackage(packageName);
        }
        activity.startActivity(intent);
    }
}
