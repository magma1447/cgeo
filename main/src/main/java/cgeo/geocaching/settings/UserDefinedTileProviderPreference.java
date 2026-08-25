package cgeo.geocaching.settings;

import cgeo.geocaching.R;
import cgeo.geocaching.activity.Keyboard;
import cgeo.geocaching.ui.dialog.Dialogs;
import cgeo.geocaching.ui.dialog.SimpleDialog;
import cgeo.geocaching.utils.LocalizationUtils;

import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButton;
import org.apache.commons.lang3.StringUtils;

/** Preference to add / edit / remove a single user-defined tile provider */
public class UserDefinedTileProviderPreference extends Preference {

    public UserDefinedTileProviderPreference(final Context context) {
        super(context);
        setWidgetLayoutResource(R.layout.button_icon_view);
    }

    public UserDefinedTileProviderPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public UserDefinedTileProviderPreference(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onBindViewHolder(@NonNull final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        setOnPreferenceClickListener(preference -> {
            launchEditDialog();
            return false;
        });
        final MaterialButton button = (MaterialButton) holder.findViewById(R.id.iconview);
        button.setIconResource(R.drawable.ic_menu_delete);
        button.setOnClickListener(v -> SimpleDialog.ofContext(getContext()).setTitle(R.string.settings_userDefinedTileProvider).setMessage(R.string.settings_userDefinedTileProvider_remove_confirm).confirm(() -> {
            // a provider without Uri gets removed
            Settings.putUserDefinedTileProvider(new Settings.PrefUserDefinedTileProvider(getKey(), null, null));
            callChangeListener(null);
        }));
    }

    public void launchEditDialog() {
        final View v = LayoutInflater.from(getContext()).inflate(R.layout.userdefined_tileprovider_preference_dialog, null);
        final EditText editTitle = v.findViewById(R.id.title);
        final EditText editUri = v.findViewById(R.id.edit);

        final Settings.PrefUserDefinedTileProvider provider = getProvider();
        if (provider != null) {
            editTitle.setText(provider.getName());
            editUri.setText(provider.getUri());
        }
        Dialogs.moveCursorToEnd(editUri);
        Keyboard.show(getContext(), StringUtils.isNotEmpty(editTitle.getText()) ? editUri : editTitle);

        final AlertDialog dialog = Dialogs.newBuilder(getContext())
                .setView(v)
                .setTitle(R.string.settings_userDefinedTileProvider)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .show();

        // override onClick listener to prevent closing the dialog when the Uri is missing
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v2 -> {
            final String newName = editTitle.getText().toString().trim();
            final String newUri = editUri.getText().toString().trim();
            if (StringUtils.isEmpty(newUri)) {
                editUri.setError(LocalizationUtils.getString(R.string.settings_userDefinedTileProvider_missing_error));
                return;
            }
            Settings.putUserDefinedTileProvider(new Settings.PrefUserDefinedTileProvider(getKey(), newName, newUri));
            callChangeListener(newUri);
            dialog.dismiss();
        });
    }

    private Settings.PrefUserDefinedTileProvider getProvider() {
        for (Settings.PrefUserDefinedTileProvider provider : Settings.getUserDefinedTileProviders()) {
            if (provider.getKey().equals(getKey())) {
                return provider;
            }
        }
        return null;
    }
}
