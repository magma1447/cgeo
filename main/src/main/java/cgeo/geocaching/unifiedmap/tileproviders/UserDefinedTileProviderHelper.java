package cgeo.geocaching.unifiedmap.tileproviders;

import cgeo.geocaching.R;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.LocalizationUtils;

import android.net.Uri;

import org.apache.commons.lang3.StringUtils;

/** Common helpers for the user-defined tile providers (Mapsforge and VTM variant) */
public class UserDefinedTileProviderHelper {

    private UserDefinedTileProviderHelper() {
        // utility class
    }

    /** name to display for the given provider: its user-given name, its host, or a generic fallback */
    public static String getName(final Settings.PrefUserDefinedTileProvider provider) {
        if (StringUtils.isNotBlank(provider.getName())) {
            return provider.getName();
        }
        final String host = StringUtils.isBlank(provider.getUri()) ? null : Uri.parse(provider.getUri()).getHost();
        return StringUtils.isNotBlank(host) ? host : LocalizationUtils.getString(R.string.settings_userDefinedTileProvider);
    }

    /** a provider can only be registered if it has a Uri with a host */
    public static boolean isConfigured(final Settings.PrefUserDefinedTileProvider provider) {
        final String uri = provider.getUri();
        return StringUtils.isNotBlank(uri) && StringUtils.isNotBlank(Uri.parse(uri).getHost());
    }
}
