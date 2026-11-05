package com.touchforge.toolkit.aidl;

/** Callbacks from the helper to the app. All oneway. */
oneway interface IToolkitCallback {
    void onGrabData(in byte[] rawEvents);
    void onDiagnostics(int code, String message);
}
