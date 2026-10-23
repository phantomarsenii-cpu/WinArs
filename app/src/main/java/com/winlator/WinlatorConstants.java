package com.winlator;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import com.winlator.core.DefaultVersion;

/** Константы и мелкие хелперы, которые раньше лежали в MainActivity / SettingsFragment (UI Winlator удалён). */
public final class WinlatorConstants {
    public static final boolean DEBUG_MODE = false;
    public static final byte CONTAINER_PATTERN_COMPRESSION_LEVEL = 9;
    public static final byte OPEN_FILE_REQUEST_CODE = 2;
    /** deviceId, которым SpenInput помечает события пера, превращённые в «мышь с правой кнопкой» */
    public static final int PEN_REMAPPED_DEVICE_ID = 0x57A5;
    public static final byte APP_THEME_LIGHT = 0;
    public static final byte APP_THEME_DARK = 1;
    public static final String DEFAULT_WINE_DEBUG_CHANNELS = "warn,err,fixme";

    private WinlatorConstants() {}

    /** Единственная Activity приложения — оболочка WinArs (Kotlin). */
    public static Class<?> hostActivity() {
        try { return Class.forName("com.winars.app.MainActivity"); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
    }

    public static void resetPreferenceVersions(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("box64_version", DefaultVersion.BOX64);
        editor.remove("current_box64_version");
        editor.remove("current_graphics_driver");
        editor.apply();
    }
}
