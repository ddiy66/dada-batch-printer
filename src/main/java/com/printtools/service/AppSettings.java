package com.printtools.service;

import java.util.prefs.Preferences;

/** 保存当前 Windows 用户的轻量设置，不记录任何敏感信息。 */
public final class AppSettings {
    private static final String MINIMIZE_TO_TRAY = "minimizeToTray";
    private static final String AUTO_START = "autoStart";
    private static final String FLOATING_LOGO = "floatingLogo";
    private final Preferences preferences = Preferences.userNodeForPackage(AppSettings.class);

    public boolean minimizeToTray() { return preferences.getBoolean(MINIMIZE_TO_TRAY, false); }
    public void minimizeToTray(boolean enabled) { preferences.putBoolean(MINIMIZE_TO_TRAY, enabled); }
    public boolean autoStart() { return preferences.getBoolean(AUTO_START, false); }
    public void autoStart(boolean enabled) { preferences.putBoolean(AUTO_START, enabled); }
    public boolean floatingLogo() { return preferences.getBoolean(FLOATING_LOGO, true); }
    public void floatingLogo(boolean enabled) { preferences.putBoolean(FLOATING_LOGO, enabled); }
}
