package com.touchforge.toolkit.aidl;

import com.touchforge.toolkit.aidl.IToolkitCallback;
import com.touchforge.toolkit.aidl.TouchSpec;

/** TouchForge privileged helper contract (runs with shell uid via Shizuku). */
interface IToolkitService {
    int getProtocolVersion();
    int getShellUid();

    /** True when the command matches the shared whitelist (preview check). */
    boolean isCommandAllowed(String command);

    /** Executes a whitelisted shell command; returns stdout+stderr (may be empty). */
    String execCommand(String command);

    /** Tracked settings write — original value saved, auto-restored on watchdog/destroy. */
    boolean applySystemSetting(String key, String value);

    /** Tracked resolution/DPI change — original stored, auto-restored on watchdog/destroy. */
    boolean startResolutionScale(int width, int height, int dpi);

    /** Restores every setting and resolution changed through the guard. */
    boolean restoreGuardedSettings();

    /** Injects a synthetic touch via the framework InputManager (shell permission). */
    boolean injectTouch(in TouchSpec spec);

    /** True when the framework injection method is available in this context. */
    boolean canInjectProbe();

    /** True when the native grabber library is present and input nodes are listable. */
    boolean canGrabProbe();

    /** Starts grabbing the named raw input device (or auto-detect when empty). */
    boolean startGrab(String devicePath);

    /** Stops any active raw grab. */
    void stopGrab();

    /** True when a native grab is currently active. */
    boolean isGrabbing();

    void setCallback(IToolkitCallback cb);
    void ping();
    void shutdown();
}
