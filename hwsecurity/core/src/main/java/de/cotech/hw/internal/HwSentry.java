package de.cotech.hw.internal;


import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import de.cotech.hw.SecurityKeyManagerConfig;


@RestrictTo(Scope.LIBRARY_GROUP)
public class HwSentry {
    private static boolean isSentryAvailable = false;
    private static boolean isCaptureExceptionOnInternalError = false;

    public static void initializeIfAvailable(SecurityKeyManagerConfig config) {
        if (config.isSentrySupportDisabled()) {
            return;
        }
    }

    public static void addBreadcrumb(String format, Object... args) {
        return;
    }

    public static void captureException(Exception exception) {
        return;
    }

    public static void addTag(String tag, String value) {
        return;
    }

    public static void removeTag(String tag) {
        return;
    }
}
