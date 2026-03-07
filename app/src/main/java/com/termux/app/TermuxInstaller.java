package com.termux.app;

/**
 * JNI bridge for bootstrap zip loading.
 * The native method name is bound to this exact package/class path.
 */
public final class TermuxInstaller {

    public static byte[] loadZipBytes() {
        System.loadLibrary("termux-bootstrap");
        return getZip();
    }

    public static native byte[] getZip();
}
