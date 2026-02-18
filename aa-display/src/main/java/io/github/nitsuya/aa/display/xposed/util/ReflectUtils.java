package io.github.nitsuya.aa.display.xposed.util;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Safe reflection utilities that prevent crashes from missing classes/methods.
 * Uses Optional return types to force callers to handle missing items.
 */
public class ReflectUtils {
    private static final String TAG = "AAD_ReflectUtils";

    /**
     * Safely load a class by name.
     *
     * @param className Fully qualified class name
     * @param classLoader ClassLoader to use, or null for default
     * @return Optional containing the class if found
     */
    public static Optional<Class<?>> safeForName(String className, ClassLoader classLoader) {
        try {
            Class<?> cls;
            if (classLoader != null) {
                cls = Class.forName(className, true, classLoader);
            } else {
                cls = Class.forName(className);
            }
            logProbeSuccess("safeForName", className, null);
            return Optional.of(cls);
        } catch (ClassNotFoundException e) {
            logProbeFailure("safeForName", className, null, e);
            return Optional.empty();
        } catch (Throwable t) {
            logProbeFailure("safeForName", className, null, t);
            return Optional.empty();
        }
    }

    /**
     * Safely get a method from a class.
     *
     * @param cls The class to search
     * @param methodName Method name
     * @param paramTypes Parameter types
     * @return Optional containing the method if found
     */
    public static Optional<Method> safeGetMethod(Class<?> cls, String methodName, Class<?>... paramTypes) {
        try {
            Method method = cls.getMethod(methodName, paramTypes);
            logProbeSuccess("safeGetMethod", cls.getName(), methodName);
            return Optional.of(method);
        } catch (NoSuchMethodException e) {
            // Try declared methods as fallback
            try {
                Method method = cls.getDeclaredMethod(methodName, paramTypes);
                method.setAccessible(true);
                logProbeSuccess("safeGetMethod (declared)", cls.getName(), methodName);
                return Optional.of(method);
            } catch (NoSuchMethodException e2) {
                logProbeFailure("safeGetMethod", cls.getName(), methodName, e2);
                return Optional.empty();
            }
        } catch (Throwable t) {
            logProbeFailure("safeGetMethod", cls.getName(), methodName, t);
            return Optional.empty();
        }
    }

    /**
     * Safely invoke a method.
     *
     * @param method The method to invoke
     * @param instance Object instance (null for static methods)
     * @param args Method arguments
     * @return Optional containing the result if successful
     */
    public static Optional<Object> safeInvoke(Method method, Object instance, Object... args) {
        try {
            Object result = method.invoke(instance, args);
            logProbeSuccess("safeInvoke", method.getDeclaringClass().getName(), method.getName());
            return Optional.ofNullable(result);
        } catch (Throwable t) {
            logProbeFailure("safeInvoke", method.getDeclaringClass().getName(), method.getName(), t);
            return Optional.empty();
        }
    }

    /**
     * Safely load a DEX file and get a class from it.
     *
     * @param dexPath Path to the DEX file
     * @param className Class name to load
     * @param parentClassLoader Parent class loader
     * @return Optional containing the class if found
     */
    public static Optional<Class<?>> loadDexClassSafe(String dexPath, String className, ClassLoader parentClassLoader) {
        try {
            File dexFile = new File(dexPath);
            if (!dexFile.exists()) {
                Log.w(TAG, "DEX file not found: " + dexPath);
                return Optional.empty();
            }

            dalvik.system.DexClassLoader loader = new dalvik.system.DexClassLoader(
                    dexPath, null, null,
                    parentClassLoader != null ? parentClassLoader : ClassLoader.getSystemClassLoader()
            );
            Class<?> cls = loader.loadClass(className);
            logProbeSuccess("loadDexClassSafe", className, dexPath);
            return Optional.of(cls);
        } catch (Throwable t) {
            logProbeFailure("loadDexClassSafe", className, dexPath, t);
            return Optional.empty();
        }
    }

    /**
     * Get version of a target application.
     *
     * @param context Application context
     * @param packageName Package name to query
     * @return Version string if found, null otherwise
     */
    public static String getTargetAppVersion(Context context, String packageName) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0).versionName;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to get version for " + packageName + ": " + t.getMessage());
            return null;
        }
    }

    /**
     * Check if AADisplay is enabled via Settings.Global.
     *
     * @param context Application context
     * @return true if enabled (default: true)
     */
    public static boolean isAadEnabled(Context context) {
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(),
                    "aad_display_enable",
                    1
            ) == 1;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to read aad_display_enable setting, defaulting to enabled: " + t.getMessage());
            return true;
        }
    }

    /**
     * Log detailed probe information for debugging reflection issues.
     */
    public static void logProbeInfo(String className, String dexPath, String methodName, Throwable error) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== AADisplay Reflection Probe ===\n");
        sb.append("Android Version: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("Class: ").append(className).append("\n");
        if (dexPath != null) {
            sb.append("DEX Path: ").append(dexPath).append("\n");
        }
        if (methodName != null) {
            sb.append("Method: ").append(methodName).append("\n");
        }
        if (error != null) {
            sb.append("Error: ").append(error.getClass().getSimpleName()).append(" - ").append(error.getMessage()).append("\n");
            sb.append("Stack trace: ");
            for (StackTraceElement element : error.getStackTrace()) {
                sb.append("\n  at ").append(element.toString());
            }
        }
        sb.append("\n=================================");
        Log.d(TAG, sb.toString());
    }

    private static void logProbeSuccess(String operation, String className, String detail) {
        try {
            ProbeLogger.INSTANCE.logMethodLookup(
                    className != null ? className : "",
                    operation + (detail != null ? " [" + detail + "]" : ""),
                    new Class<?>[0],
                    true,
                    null
            );
        } catch (Throwable ignored) {
            // ProbeLogger not available
        }
    }

    private static void logProbeFailure(String operation, String className, String detail, Throwable error) {
        try {
            ProbeLogger.INSTANCE.logMethodLookup(
                    className != null ? className : "",
                    operation + (detail != null ? " [" + detail + "]" : ""),
                    new Class<?>[0],
                    false,
                    error
            );
        } catch (Throwable ignored) {
            // ProbeLogger not available, use standard log
            Log.w(TAG, operation + " failed for " + className + (detail != null ? " [" + detail + "]" : "") + ": " + (error != null ? error.getMessage() : "unknown"));
        }
    }
}
