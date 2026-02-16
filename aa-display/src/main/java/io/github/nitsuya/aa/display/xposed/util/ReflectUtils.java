package io.github.nitsuya.aa.display.xposed.util;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Optional;

import dalvik.system.DexClassLoader;

public final class ReflectUtils {
    private static final String TAG = "AADisplayReflect";

    private ReflectUtils() {
    }

    public static Optional<Class<?>> safeForName(String className, ClassLoader loader) {
        try {
            Class<?> clazz = (loader != null)
                    ? Class.forName(className, false, loader)
                    : Class.forName(className);
            logClassLoadSuccess(className, null);
            return Optional.of(clazz);
        } catch (Throwable t) {
            Log.w(TAG, "Class not found: " + className + " - " + t.getMessage());
            logClassLoadFailure(className, null, t);
            return Optional.empty();
        }
    }

    public static Optional<Method> safeGetMethod(Class<?> cls, String name, Class<?>... params) {
        try {
            Method method = cls.getMethod(name, params);
            logMethodLookupSuccess(cls.getName(), name, params);
            return Optional.of(method);
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "Method not found: " + cls.getName() + "#" + name);
            logMethodLookupFailure(cls.getName(), name, params, e);
            return Optional.empty();
        } catch (Throwable t) {
            Log.w(TAG, "safeGetMethod error: " + t.getMessage());
            logMethodLookupFailure(cls.getName(), name, params, t);
            return Optional.empty();
        }
    }

    public static Optional<Object> safeInvoke(Method method, Object instance, Object... args) {
        try {
            if (method == null) {
                logMethodInvokeFailure("null", new NullPointerException("Method is null"));
                return Optional.empty();
            }
            Object result = method.invoke(instance, args);
            logMethodInvokeSuccess(method.getName());
            return Optional.ofNullable(result);
        } catch (Throwable t) {
            Log.w(TAG, "invoke failed: " + (method == null ? "null method" : method.getName()) + " - " + t.getMessage());
            logMethodInvokeFailure(method == null ? "null" : method.getName(), t);
            return Optional.empty();
        }
    }

    public static ClassLoader safeLoadDex(String dexPath, Context context) {
        try {
            // optimizedDirectory is ignored on API 26+ (minSdk 31), pass null
            DexClassLoader loader = new DexClassLoader(
                    dexPath,
                    null,
                    null,
                    context.getClassLoader()
            );
            Log.i(TAG, "DexClassLoader created for " + dexPath);
            logDexLoadSuccess(dexPath);
            return loader;
        } catch (Throwable t) {
            Log.w(TAG, "Dex load failed: " + t.getMessage());
            logDexLoadFailure(dexPath, t);
            return null;
        }
    }

    public static Class<?> loadDexClassSafe(String dexPath, String className, Context context) {
        Log.i(TAG, "Trying to load " + className + " from " + dexPath + " on Android " + Build.VERSION.RELEASE);
        ClassLoader loader = safeLoadDex(dexPath, context);
        if (loader == null) {
            Log.w(TAG, "Failed to create DexClassLoader for " + dexPath);
            return null;
        }
        Optional<Class<?>> maybe = safeForName(className, loader);
        if (!maybe.isPresent()) {
            Log.w(TAG, "Class " + className + " not present in dex " + dexPath);
            return null;
        }
        Log.i(TAG, "Successfully loaded class: " + className);
        return maybe.get();
    }

    public static String getTargetAppVersion(Context context, String packageName) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0).versionName;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to get version for package: " + packageName + " - " + t.getMessage());
            return null;
        }
    }

    public static boolean isAadEnabled(Context context) {
        try {
            return android.provider.Settings.Global.getInt(
                    context.getContentResolver(),
                    "aad_display_enable",
                    1
            ) == 1;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to read aad_display_enable setting, defaulting to enabled: " + t.getMessage());
            return true;
        }
    }

    public static void logProbeInfo(String className, String dexPath, String methodName, Throwable error) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== AADisplay Reflection Probe ===\n");
        sb.append("Android Version: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("Class: ").append(className).append("\n");
        if (dexPath != null) {
            sb.append("DEX Path: ").append(dexPath).append("\n");
        }
        if (methodName != null) {
            sb.append("Method: ").append(methodName).append("\n");
        }
        if (error != null) {
            sb.append("Error: ").append(error.getClass().getSimpleName())
                    .append(" - ").append(error.getMessage()).append("\n");
            sb.append("Stack trace:");
            for (StackTraceElement element : error.getStackTrace()) {
                sb.append("\n at ").append(element.toString());
            }
        }
        sb.append("\n=================================");
        Log.w(TAG, sb.toString());
    }

    private static void logClassLoadSuccess(String className, String dexPath) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logClassLoad", String.class, String.class, boolean.class, Throwable.class);
            method.invoke(null, className, dexPath, true, null);
        } catch (Throwable ignored) {
        }
    }

    private static void logClassLoadFailure(String className, String dexPath, Throwable error) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logClassLoad", String.class, String.class, boolean.class, Throwable.class);
            method.invoke(null, className, dexPath, false, error);
        } catch (Throwable ignored) {
        }
    }

    private static void logMethodLookupSuccess(String className, String methodName, Class<?>[] params) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logMethodLookup", String.class, String.class, Class[].class, boolean.class, Throwable.class);
            method.invoke(null, className, methodName, params, true, null);
        } catch (Throwable ignored) {
        }
    }

    private static void logMethodLookupFailure(String className, String methodName, Class<?>[] params, Throwable error) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logMethodLookup", String.class, String.class, Class[].class, boolean.class, Throwable.class);
            method.invoke(null, className, methodName, params, false, error);
        } catch (Throwable ignored) {
        }
    }

    private static void logMethodInvokeSuccess(String methodName) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logMethodInvoke", String.class, boolean.class, Throwable.class);
            method.invoke(null, methodName, true, null);
        } catch (Throwable ignored) {
        }
    }

    private static void logMethodInvokeFailure(String methodName, Throwable error) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logMethodInvoke", String.class, boolean.class, Throwable.class);
            method.invoke(null, methodName, false, error);
        } catch (Throwable ignored) {
        }
    }

    private static void logDexLoadSuccess(String dexPath) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logDexLoad", String.class, boolean.class, Throwable.class);
            method.invoke(null, dexPath, true, null);
        } catch (Throwable ignored) {
        }
    }

    private static void logDexLoadFailure(String dexPath, Throwable error) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logDexLoad", String.class, boolean.class, Throwable.class);
            method.invoke(null, dexPath, false, error);
        } catch (Throwable ignored) {
        }
    }
}
