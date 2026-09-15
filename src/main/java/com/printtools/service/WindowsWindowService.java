package com.printtools.service;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;

import java.util.Locale;

/** Windows 窗口样式适配：让独立悬浮窗不出现在任务栏中。 */
public final class WindowsWindowService {
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int WS_EX_APPWINDOW = 0x00040000;
    public void hideFromTaskbar(String windowTitle) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) return;
        HWND window = User32.INSTANCE.FindWindow(null, windowTitle);
        if (window == null) return;
        User32.INSTANCE.ShowWindow(window, WinUser.SW_HIDE);
        int style = User32.INSTANCE.GetWindowLong(window, WinUser.GWL_EXSTYLE);
        style = (style | WS_EX_TOOLWINDOW) & ~WS_EX_APPWINDOW;
        User32.INSTANCE.SetWindowLong(window, WinUser.GWL_EXSTYLE, style);
        User32.INSTANCE.ShowWindow(window, WinUser.SW_SHOWNOACTIVATE);
    }
}
