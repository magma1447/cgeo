package cgeo.geocaching.unifiedmap.tileproviders;

import cgeo.geocaching.settings.Settings;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import org.apache.commons.lang3.StringUtils;
import static org.oscim.map.Viewport.MIN_ZOOM_LEVEL;

public class UserDefinedMapsforgeVTMOnlineSource extends AbstractMapsforgeVTMOnlineTileProvider {
    @NonNull private final String key;

    UserDefinedMapsforgeVTMOnlineSource(final Settings.PrefUserDefinedTileProvider provider) {
        super(UserDefinedTileProviderHelper.getName(provider), Uri.parse(provider.getUri()), "/{Z}/{X}/{Y}.png", MIN_ZOOM_LEVEL, 18, new Pair<>(UserDefinedTileProviderHelper.getName(provider), true));
        this.key = provider.getKey();
        final Uri fullUri = Uri.parse(provider.getUri());

        final String mapUri = fullUri.getScheme() + "://" + fullUri.getHost();
        setMapUri(Uri.parse(mapUri));

        String tilePath = fullUri.getPath();
        if (tilePath != null) {
            if (!(tilePath.contains("{X}") && tilePath.contains("{Y}"))) {
                if (!tilePath.endsWith("/")) {
                    tilePath += "/";
                }
                tilePath += "{Z}/{X}/{Y}.png";
            }
            final String query = fullUri.getQuery();
            setTilePath(tilePath + (StringUtils.isNotBlank(query) ? "?" + query : ""));
            setTilePath(tilePath);
        }
        supportsHillshading = true;
    }

    /**
     * Deriving the id from the map Uri (as the superclass does) is not sufficient here,
     * as several user-defined providers may share the same host.
     */
    @Override
    @NonNull
    public String getId() {
        return getClass().getName() + ":" + key;
    }
}
