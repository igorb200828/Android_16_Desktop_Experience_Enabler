package com.igorb.desktopexperience;

import android.content.Context;
import android.view.Display;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "DesktopModeEnabler";

    // --- Target Classes ---
    private static final String CLASS_SYSTEM_PROPERTIES = "android.os.SystemProperties";
    private static final String CLASS_DESKTOP_MODE_STATUS = "com.android.wm.shell.shared.desktopmode.DesktopModeStatus";
    private static final String CLASS_DESKTOP_STATE_IMPL = "com.android.wm.shell.shared.desktopmode.DesktopStateImpl";
    private static final String CLASS_DESKTOP_EXPERIENCE_FLAGS = "android.window.DesktopExperienceFlags";
    private static final String CLASS_DESKTOP_MODE_FLAGS_ENUM = "android.window.DesktopModeFlags";
    private static final String CLASS_TASKBAR_DESKTOP_EXPERIENCE_FLAGS = "com.android.launcher3.taskbar.TaskbarDesktopExperienceFlags";
    private static final String CLASS_DESKTOP_EXPERIENCE_FLAG_INNER = "android.window.DesktopExperienceFlags$DesktopExperienceFlag";
    private static final String CLASS_TASKBAR_ACTIVITY_CONTEXT = "com.android.launcher3.taskbar.TaskbarActivityContext";


    // --- Methods to force true in DesktopModeStatus ---
    private static final String[] DMS_METHODS_TO_FORCE_TRUE = {
            "isDesktopModeSupported", "isDesktopModeDevOptionSupported", "canShowDesktopModeDevOption",
            "canShowDesktopExperienceDevOption", "shouldDevOptionBeEnabledByDefault",
            "isDeviceEligibleForDesktopMode", "isDeviceEligibleForDesktopModeDevOption"
    };

    // --- Fields to modify in DesktopStateImpl constructor ---
    private static final String[] DSI_FIELDS_TO_FORCE_TRUE_IN_CONSTRUCTOR = {
            "canEnterDesktopMode", "enterDesktopByDefaultOnFreeformDisplay", "isDesktopModeSupported"
    };
    private static final String[] DSI_FIELDS_TO_FORCE_FALSE_IN_CONSTRUCTOR = {
            "canInternalDisplayHostDesktops",
            "enforceDeviceRestrictions"
    };

    // --- Static field names in DesktopExperienceFlags ---
    private static final String[] DEF_FLAGS_FIELDS_TO_FORCE_TRUE = {
            "ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE",
            "ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAYS",
            "ENABLE_MULTIPLE_DESKTOPS_BACKEND",
            "ENABLE_TASKBAR_CONNECTED_DISPLAYS"
    };
    private static final String[] DEF_FLAGS_FIELDS_TO_FORCE_FALSE = {
            "ENABLE_MULTIPLE_DESKTOPS_FRONTEND"
    };
    private static final Set<Object> sTargetDesktopExperienceFlagInstancesToForceTrue = new HashSet<>();
    private static final Set<Object> sTargetDesktopExperienceFlagInstancesToForceFalse = new HashSet<>();
    private static final Set<String> sInitializedPackagesForDefFlags = new HashSet<>();


    // --- Static field names in DesktopModeFlags (enum) to force true ---
    private static final String[] DMF_ENUM_FIELDS_TO_FORCE_TRUE = {
            "ENABLE_DESKTOP_CLOSE_SHORTCUT_BUGFIX", "ENABLE_TASKBAR_OVERFLOW",
            "ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION", "PRESERVE_RECENTS_TASK_CONFIGURATION_ON_RELAUNCH"
    };
    private static final Set<Object> sTargetDesktopModeEnumFlagInstances = new HashSet<>();
    private static final Set<String> sInitializedPackagesForDmfEnumFlags = new HashSet<>();

    // --- Static field names in TaskbarDesktopExperienceFlags to force true ---
    private static final String[] TDEF_FLAGS_FIELDS_TO_FORCE_TRUE = {
            "enableAltTabKqsOnConnectedDisplays",
            "enableAltTabKqsFlatenning"
    };
    private static final Set<Object> sTargetTaskbarDesktopExperienceFlagInstances = new HashSet<>();
    private static final Set<String> sInitializedPackagesForTdefFlags = new HashSet<>();


    // --- Target System Property ---
    // The value of this property will be forced to FALSE
    private static final String PROP_ENFORCE_DEVICE_RESTRICTIONS = "persist.wm.debug.desktop_mode_enforce_device_restrictions";

    // --- Target Packages ---
    private static final String PACKAGE_SETTINGS = "com.android.settings";
    private static final String PACKAGE_PIXEL_LAUNCHER = "com.google.android.apps.nexuslauncher";
    private static final String PACKAGE_SYSTEMUI = "com.android.systemui";
    private static final String PACKAGE_ANDROID_FRAMEWORK = "android";
    private static final String PACKAGE_SYSTEM = "system";
    private static final String PACKAGE_ANDROID_SHELL = "com.android.shell";


    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        boolean isTargetPackage = lpparam.packageName.equals(PACKAGE_SETTINGS) ||
                lpparam.packageName.equals(PACKAGE_PIXEL_LAUNCHER) ||
                lpparam.packageName.equals(PACKAGE_SYSTEMUI) ||
                lpparam.packageName.equals(PACKAGE_SYSTEM) ||
                lpparam.packageName.equals(PACKAGE_ANDROID_FRAMEWORK) ||
                lpparam.packageName.equals(PACKAGE_ANDROID_SHELL);

        if (!isTargetPackage) {
            return;
        }
        XposedBridge.log(TAG + ": Found target package: " + lpparam.packageName);

        hookSystemProperties(lpparam.classLoader, lpparam.packageName);
        hookDesktopModeStatus(lpparam.classLoader, lpparam.packageName);
        hookDesktopStateImpl(lpparam.classLoader, lpparam.packageName);
        hookDesktopExperienceFlags(lpparam.classLoader, lpparam.packageName);
        hookDesktopModeFlagsEnum(lpparam.classLoader, lpparam.packageName);

        if (lpparam.packageName.equals(PACKAGE_PIXEL_LAUNCHER)) {
            hookTaskbarDesktopExperienceFlags(lpparam.classLoader, lpparam.packageName);
            hookTaskbarActivityContext(lpparam.classLoader, lpparam.packageName);
        }
    }

    private void hookSystemProperties(ClassLoader classLoader, String packageName) {
        try {
            Class<?> systemPropertiesClass = XposedHelpers.findClass(CLASS_SYSTEM_PROPERTIES, classLoader);
            XposedHelpers.findAndHookMethod(systemPropertiesClass, "getBoolean", String.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[0];
                            if (PROP_ENFORCE_DEVICE_RESTRICTIONS.equals(key)) {
                                param.setResult(false); // MODIFIED: Force to false
                                XposedBridge.log(TAG + ": SystemProperties.getBoolean for " + PROP_ENFORCE_DEVICE_RESTRICTIONS + " forced to false in " + packageName);
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + ": Hooked SystemProperties.getBoolean in " + packageName);
        } catch (Throwable t) { logError("hookSystemProperties", packageName, t); }
    }

    private void hookDesktopModeStatus(ClassLoader classLoader, String packageName) {
        try {
            Class<?> desktopModeStatusClass = XposedHelpers.findClass(CLASS_DESKTOP_MODE_STATUS, classLoader);
            for (String methodName : DMS_METHODS_TO_FORCE_TRUE) {
                try {
                    XposedHelpers.findAndHookMethod(desktopModeStatusClass, methodName, Context.class, XC_MethodReplacement.returnConstant(true));
                } catch (Throwable t) { logError(CLASS_DESKTOP_MODE_STATUS + "#" + methodName, packageName, t); }
            }
            try {
                // MODIFIED: Force enforceDeviceRestrictions() to return false
                XposedHelpers.findAndHookMethod(desktopModeStatusClass, "enforceDeviceRestrictions", XC_MethodReplacement.returnConstant(false));
                XposedBridge.log(TAG + ": Hooked " + CLASS_DESKTOP_MODE_STATUS + "#enforceDeviceRestrictions to return false in " + packageName);
            } catch (Throwable t) { logError(CLASS_DESKTOP_MODE_STATUS + "#enforceDeviceRestrictions", packageName, t); }
        } catch (Throwable t) { logError("hookDesktopModeStatus general", packageName, t); }
    }

    private void hookDesktopStateImpl(ClassLoader classLoader, String packageName) {
        try {
            Class<?> desktopStateImplClass = XposedHelpers.findClass(CLASS_DESKTOP_STATE_IMPL, classLoader);
            XposedHelpers.findAndHookConstructor(desktopStateImplClass, Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object thisObject = param.thisObject;
                            for (String fieldName : DSI_FIELDS_TO_FORCE_TRUE_IN_CONSTRUCTOR) {
                                try { XposedHelpers.setBooleanField(thisObject, fieldName, true); } catch (Throwable t) { logError("Set DSI Field True: " + fieldName, packageName, t); }
                            }
                            // MODIFIED: enforceDeviceRestrictions is now in DSI_FIELDS_TO_FORCE_FALSE_IN_CONSTRUCTOR
                            for (String fieldName : DSI_FIELDS_TO_FORCE_FALSE_IN_CONSTRUCTOR) {
                                try {
                                    XposedHelpers.setBooleanField(thisObject, fieldName, false);
                                    XposedBridge.log(TAG + ": Set " + CLASS_DESKTOP_STATE_IMPL + "." + fieldName + " = false in " + packageName);
                                } catch (Throwable t) { logError("Set DSI Field False: " + fieldName, packageName, t); }
                            }
                        }
                    }
            );
            try { XposedHelpers.findAndHookMethod(desktopStateImplClass, "isDeviceEligibleForDesktopMode", XC_MethodReplacement.returnConstant(true)); } catch (Throwable t) { /* ... */ }
            try { XposedHelpers.findAndHookMethod(desktopStateImplClass, "isDesktopModeSupportedOnDisplay", Display.class, XC_MethodReplacement.returnConstant(true)); } catch (Throwable t) { /* ... */ }
            try { XposedHelpers.findAndHookMethod(desktopStateImplClass, "isDesktopModeSupportedOnDisplay", int.class, XC_MethodReplacement.returnConstant(true)); } catch (Throwable t) { /* ... */ }
        } catch (Throwable t) { logError("hookDesktopStateImpl general", packageName, t); }
    }

    private void hookTaskbarActivityContext(ClassLoader classLoader, String packageName) {
        try {
            Class<?> taskbarActivityContextClass = XposedHelpers.findClass(CLASS_TASKBAR_ACTIVITY_CONTEXT, classLoader);
            XposedHelpers.findAndHookMethod(taskbarActivityContextClass, "showDesktopTaskbarForFreeformDisplay", XC_MethodReplacement.returnConstant(true));
            XposedBridge.log(TAG + ": Hooked " + CLASS_TASKBAR_ACTIVITY_CONTEXT + "#showDesktopTaskbarForFreeformDisplay to return true in " + packageName);
        } catch (Throwable t) { logError("hookTaskbarActivityContext general", packageName, t); }
    }

    private void hookTaskbarDesktopExperienceFlags(ClassLoader classLoader, String packageName) {
        try {
            final Class<?> taskbarDefContainerClass = XposedHelpers.findClass(CLASS_TASKBAR_DESKTOP_EXPERIENCE_FLAGS, classLoader);
            final Class<?> defInnerFlagClass = XposedHelpers.findClass(CLASS_DESKTOP_EXPERIENCE_FLAG_INNER, classLoader);
            synchronized (sTargetTaskbarDesktopExperienceFlagInstances) {
                String cacheKey = packageName + taskbarDefContainerClass.getName();
                if (!sInitializedPackagesForTdefFlags.contains(cacheKey)) {
                    boolean oneFlagCached = false;
                    for (String flagFieldName : TDEF_FLAGS_FIELDS_TO_FORCE_TRUE) {
                        try {
                            Object flagInstance = XposedHelpers.getStaticObjectField(taskbarDefContainerClass, flagFieldName);
                            if (flagInstance != null) { sTargetTaskbarDesktopExperienceFlagInstances.add(flagInstance); oneFlagCached = true; }
                        } catch (Throwable t) { logError("Cache TDEF: " + flagFieldName, packageName, t); }
                    }
                    if (oneFlagCached) { sInitializedPackagesForTdefFlags.add(cacheKey); XposedBridge.log(TAG + ": Cached TaskbarDesktopExperienceFlags for " + packageName); }
                }
            }
            XposedHelpers.findAndHookMethod(defInnerFlagClass, "isTrue", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object currentFlagInstance = param.thisObject;
                    if (currentFlagInstance != null && sTargetTaskbarDesktopExperienceFlagInstances.contains(currentFlagInstance)) {
                        String flagName = getFlagInstanceName(taskbarDefContainerClass, currentFlagInstance, TDEF_FLAGS_FIELDS_TO_FORCE_TRUE, "TaskbarDEF");
                        param.setResult(true);
                        XposedBridge.log(TAG + ": Taskbar flag " + flagName + " (" + defInnerFlagClass.getSimpleName() + "#isTrue()) forced to true in " + packageName);
                    }
                }
            });
            XposedBridge.log(TAG + ": Hooked " + defInnerFlagClass.getName() + "#isTrue() for Taskbar flags in " + packageName);
        } catch (Throwable t) { logError("hookTaskbarDesktopExperienceFlags general", packageName, t); }
    }

    private void hookDesktopExperienceFlags(ClassLoader classLoader, String packageName) {
        try {
            final Class<?> defClass = XposedHelpers.findClass(CLASS_DESKTOP_EXPERIENCE_FLAGS, classLoader);
            synchronized (sTargetDesktopExperienceFlagInstancesToForceTrue) {
                String cacheKey = packageName + defClass.getName();
                if (!sInitializedPackagesForDefFlags.contains(cacheKey)) {
                    boolean oneFlagCached = false;
                    for (String flagFieldName : DEF_FLAGS_FIELDS_TO_FORCE_TRUE) {
                        try {
                            Object flagInstance = XposedHelpers.getStaticObjectField(defClass, flagFieldName);
                            if (flagInstance != null) { sTargetDesktopExperienceFlagInstancesToForceTrue.add(flagInstance); oneFlagCached = true; }
                        } catch (Throwable t) { logError("Cache DEF True: " + flagFieldName, packageName, t); }
                    }
                    for (String flagFieldName : DEF_FLAGS_FIELDS_TO_FORCE_FALSE) {
                        try {
                            Object flagInstance = XposedHelpers.getStaticObjectField(defClass, flagFieldName);
                            if (flagInstance != null) { sTargetDesktopExperienceFlagInstancesToForceFalse.add(flagInstance); oneFlagCached = true; }
                        } catch (Throwable t) { logError("Cache DEF False: " + flagFieldName, packageName, t); }
                    }
                    if (oneFlagCached) { sInitializedPackagesForDefFlags.add(cacheKey); XposedBridge.log(TAG + ": Cached framework DesktopExperienceFlags for " + packageName); }
                }
            }
            XposedHelpers.findAndHookMethod(defClass, "isTrue", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object currentFlagInstance = param.thisObject;
                    if (currentFlagInstance == null) return;
                    if (sTargetDesktopExperienceFlagInstancesToForceFalse.contains(currentFlagInstance)) {
                        String flagName = getFlagInstanceName(defClass, currentFlagInstance, DEF_FLAGS_FIELDS_TO_FORCE_FALSE, "FrameworkDEF");
                        param.setResult(false);
                        XposedBridge.log(TAG + ": Framework " + CLASS_DESKTOP_EXPERIENCE_FLAGS + "#isTrue() for " + flagName + " forced to false in " + packageName);
                    } else if (sTargetDesktopExperienceFlagInstancesToForceTrue.contains(currentFlagInstance)) {
                        String flagName = getFlagInstanceName(defClass, currentFlagInstance, DEF_FLAGS_FIELDS_TO_FORCE_TRUE, "FrameworkDEF");
                        param.setResult(true);
                        XposedBridge.log(TAG + ": Framework " + CLASS_DESKTOP_EXPERIENCE_FLAGS + "#isTrue() for " + flagName + " forced to true in " + packageName);
                    }
                }
            });
            XposedBridge.log(TAG + ": Hooked framework " + CLASS_DESKTOP_EXPERIENCE_FLAGS + "#isTrue() in " + packageName);
        } catch (Throwable t) { logError("hookDesktopExperienceFlags general", packageName, t); }
    }

    private void hookDesktopModeFlagsEnum(ClassLoader classLoader, String packageName) {
        try {
            final Class<?> dmfEnumClass = XposedHelpers.findClass(CLASS_DESKTOP_MODE_FLAGS_ENUM, classLoader);
            synchronized (sTargetDesktopModeEnumFlagInstances) {
                String cacheKey = packageName + dmfEnumClass.getName();
                if (!sInitializedPackagesForDmfEnumFlags.contains(cacheKey)) {
                    boolean oneFlagCached = false;
                    for (String flagFieldName : DMF_ENUM_FIELDS_TO_FORCE_TRUE) {
                        try {
                            Object flagInstance = XposedHelpers.getStaticObjectField(dmfEnumClass, flagFieldName);
                            if (flagInstance != null) { sTargetDesktopModeEnumFlagInstances.add(flagInstance); oneFlagCached = true; }
                        } catch (Throwable t) { logError("Cache DMF Enum: " + flagFieldName, packageName, t); }
                    }
                    if (oneFlagCached) { sInitializedPackagesForDmfEnumFlags.add(cacheKey); XposedBridge.log(TAG + ": Cached DesktopModeFlagsEnum for " + packageName); }
                }
            }
            XposedHelpers.findAndHookMethod(dmfEnumClass, "isTrue", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object currentEnumInstance = param.thisObject;
                    if (currentEnumInstance != null && sTargetDesktopModeEnumFlagInstances.contains(currentEnumInstance)) {
                        String flagName = getFlagInstanceName(dmfEnumClass, currentEnumInstance, DMF_ENUM_FIELDS_TO_FORCE_TRUE, "DMFEnum");
                        param.setResult(true);
                        XposedBridge.log(TAG + ": " + CLASS_DESKTOP_MODE_FLAGS_ENUM + "#isTrue() for " + flagName + " forced to true in " + packageName);
                    }
                }
            });
            XposedBridge.log(TAG + ": Hooked " + CLASS_DESKTOP_MODE_FLAGS_ENUM + "#isTrue() in " + packageName);
        } catch (Throwable t) { logError("hookDesktopModeFlagsEnum general", packageName, t); }
    }

    private String getFlagInstanceName(Class<?> flagContainerClass, Object flagInstance, String[] knownFieldNames, String typeHint) {
        if (flagInstance == null || flagContainerClass == null) return typeHint + ":UnknownInstance";
        if (flagInstance instanceof Enum) { return ((Enum<?>) flagInstance).name(); }
        for (String knownName : knownFieldNames) {
            try {
                Object knownInstance = XposedHelpers.getStaticObjectField(flagContainerClass, knownName);
                if (flagInstance.equals(knownInstance)) { return knownName; }
            } catch (Throwable ignored) {}
        }
        return typeHint + ":" + flagInstance.getClass().getSimpleName() + "@" + Integer.toHexString(flagInstance.hashCode());
    }

    private void logError(String context, String packageName, Throwable t) {
        XposedBridge.log(TAG + ": Error in " + context + " for " + packageName + ": " + t.getMessage());
        if (!(t instanceof XposedHelpers.ClassNotFoundError) && !(t instanceof NoSuchMethodError) &&
                !(t instanceof NoSuchFieldError)) {
            XposedBridge.log(t);
        }
    }
}