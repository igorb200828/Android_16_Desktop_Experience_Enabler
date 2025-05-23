package com.igorb.desktopexperience;

import android.content.Context;
import android.hardware.display.DisplayManager;
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
    private static final String CLASS_DESKTOP_EXPERIENCE_FLAGS = "android.window.DesktopExperienceFlags"; // Framework enum
    private static final String CLASS_INTERNAL_FEATURE_FLAGS_IMPL = "com.android.internal.hidden_from_bootclasspath.com.android.window.flags.FeatureFlagsImpl";


    // --- Methods to force true in DesktopModeStatus ---
    private static final String[] DMS_METHODS_TO_FORCE_TRUE = {
            "isDesktopModeSupported", "isDesktopModeDevOptionSupported", "canShowDesktopModeDevOption",
            "canShowDesktopExperienceDevOption", "shouldDevOptionBeEnabledByDefault", "isDeviceEligibleForDesktopMode",
            "isDeviceEligibleForDesktopModeDevOption"
    };

    // --- Fields to force true in DesktopStateImpl constructor ---
    private static final String[] DSI_FIELDS_TO_FORCE_TRUE_IN_CONSTRUCTOR = {
            "canEnterDesktopMode", "enableMultipleDesktops", "enterDesktopByDefaultOnFreeformDisplay", "isDesktopModeSupported"
    };

    // Specific fields to set in DesktopStateImpl constructor
    private static final String DSI_FIELD_ENFORCE_DEVICE_RESTRICTIONS = "enforceDeviceRestrictions";
    private static final String DSI_FIELD_CAN_INTERNAL_DISPLAY_HOST_DESKTOPS = "canInternalDisplayHostDesktops";

    // --- Static field names in DesktopExperienceFlags (Framework Enum) to force true ---
    private static final String[] DEF_ENUM_FLAGS_TO_FORCE_TRUE = {
            "ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE", "ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAYS",
            "ENABLE_MULTIPLE_DESKTOPS_BACKEND", "ENABLE_MULTIPLE_DESKTOPS_FRONTEND",
            "ENABLE_DESKTOP_TASKBAR_ON_FREEFORM_DISPLAYS",
            "ENABLE_TASKBAR_CONNECTED_DISPLAYS",
            "FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH" // MOVED HERE
    };

    // --- Static field names in DesktopExperienceFlags (Framework Enum) to force false ---
    private static final String[] DEF_ENUM_FLAGS_TO_FORCE_FALSE = {
            // FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH // REMOVED FROM HERE
    };

    // --- Methods in FeatureFlagsImpl to force true ---
    private static final String[] INTERNAL_FLAGS_METHODS_TO_FORCE_TRUE = {
            "enableDesktopTaskbarOnFreeformDisplays",
            "enableTaskbarConnectedDisplays",
            "enableDesktopWindowingMode",
            "formFactorBasedDesktopFirstSwitch" // MOVED HERE
    };

    // --- Methods in FeatureFlagsImpl to force false ---
    private static final String[] INTERNAL_FLAGS_METHODS_TO_FORCE_FALSE = {
            // "formFactorBasedDesktopFirstSwitch", // REMOVED FROM HERE
            "enableDisplayWindowingModeSwitching"
    };


    private static final Set<Object> sTargetDefEnumInstancesToForceTrue = new HashSet<>();
    private static final Set<Object> sTargetDefEnumInstancesToForceFalse = new HashSet<>();
    private static final Set<String> sInitializedPackagesForDefEnumFlags = new HashSet<>();


    private static final String PROP_ENFORCE_DEVICE_RESTRICTIONS = "persist.wm.debug.desktop_mode_enforce_device_restrictions";

    private static final String PACKAGE_SETTINGS = "com.android.settings";
    private static final String PACKAGE_PIXEL_LAUNCHER = "com.google.android.apps.nexuslauncher";
    private static final String PACKAGE_SYSTEMUI = "com.android.systemui";
    private static final String PACKAGE_ANDROID_FRAMEWORK = "android";
    private static final String PACKAGE_ANDROID_SHELL = "com.android.shell";


    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        boolean isTargetPackage = lpparam.packageName.equals(PACKAGE_SETTINGS) ||
                lpparam.packageName.equals(PACKAGE_PIXEL_LAUNCHER) ||
                lpparam.packageName.equals(PACKAGE_SYSTEMUI) ||
                lpparam.packageName.equals(PACKAGE_ANDROID_FRAMEWORK) ||
                lpparam.packageName.equals(PACKAGE_ANDROID_SHELL);

        if (!isTargetPackage) {
            return;
        }
        XposedBridge.log(TAG + ": Found target package: " + lpparam.packageName);

        hookInternalFeatureFlagsImpl(lpparam.classLoader, lpparam.packageName);
        hookDesktopExperienceFrameworkEnumFlags(lpparam.classLoader, lpparam.packageName);

        hookSystemProperties(lpparam.classLoader, lpparam.packageName);
        hookDesktopModeStatus(lpparam.classLoader, lpparam.packageName);
        hookDesktopStateImpl(lpparam.classLoader, lpparam.packageName);
    }

    private void hookInternalFeatureFlagsImpl(ClassLoader classLoader, final String packageName) {
        try {
            Class<?> featureFlagsImplClass = XposedHelpers.findClass(CLASS_INTERNAL_FEATURE_FLAGS_IMPL, classLoader);

            for (String methodName : INTERNAL_FLAGS_METHODS_TO_FORCE_TRUE) {
                try {
                    XposedHelpers.findAndHookMethod(featureFlagsImplClass, methodName, XC_MethodReplacement.returnConstant(true));
                    XposedBridge.log(TAG + ": Hooked " + CLASS_INTERNAL_FEATURE_FLAGS_IMPL + "#" + methodName + " to return TRUE in " + packageName);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": Error hooking " + methodName + " to TRUE in " + CLASS_INTERNAL_FEATURE_FLAGS_IMPL + " for " + packageName + ": " + t.getMessage());
                }
            }

            for (String methodName : INTERNAL_FLAGS_METHODS_TO_FORCE_FALSE) {
                try {
                    XposedHelpers.findAndHookMethod(featureFlagsImplClass, methodName, XC_MethodReplacement.returnConstant(false));
                    XposedBridge.log(TAG + ": Hooked " + CLASS_INTERNAL_FEATURE_FLAGS_IMPL + "#" + methodName + " to return FALSE in " + packageName);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": Error hooking " + methodName + " to FALSE in " + CLASS_INTERNAL_FEATURE_FLAGS_IMPL + " for " + packageName + ": " + t.getMessage());
                }
            }
            XposedBridge.log(TAG + ": Finished hooking methods in " + CLASS_INTERNAL_FEATURE_FLAGS_IMPL + " for " + packageName);

        } catch (XposedHelpers.ClassNotFoundError e) {
            if (packageName.equals(PACKAGE_ANDROID_FRAMEWORK)) {
                XposedBridge.log(TAG + ": Class not found: " + CLASS_INTERNAL_FEATURE_FLAGS_IMPL + " in " + packageName + " (this is unexpected for framework)");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": General error hooking " + CLASS_INTERNAL_FEATURE_FLAGS_IMPL + " in " + packageName + ": " + t.getMessage());
        }
    }


    private void hookSystemProperties(ClassLoader classLoader, final String packageName) {
        try {
            Class<?> systemPropertiesClass = XposedHelpers.findClass(CLASS_SYSTEM_PROPERTIES, classLoader);

            XposedHelpers.findAndHookMethod(
                    systemPropertiesClass,
                    "getBoolean",
                    String.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[0];
                            if (PROP_ENFORCE_DEVICE_RESTRICTIONS.equals(key)) {
                                param.setResult(false);
                                XposedBridge.log(TAG + ": SystemProperties.getBoolean for " + PROP_ENFORCE_DEVICE_RESTRICTIONS + " forced to false in " + packageName);
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + ": Hooked SystemProperties.getBoolean in " + packageName);
        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log(TAG + ": Class not found: " + CLASS_SYSTEM_PROPERTIES + " in " + packageName + " - " + e.getMessage());
        }
        catch (Throwable t) {
            XposedBridge.log(TAG + ": Error hooking SystemProperties.getBoolean in " + packageName + ": " + t.getMessage());
            XposedBridge.log(t);
        }
    }

    private void hookDesktopModeStatus(ClassLoader classLoader, final String packageName) {
        try {
            Class<?> desktopModeStatusClass = XposedHelpers.findClass(CLASS_DESKTOP_MODE_STATUS, classLoader);

            for (String methodName : DMS_METHODS_TO_FORCE_TRUE) {
                try {
                    XposedHelpers.findAndHookMethod(
                            desktopModeStatusClass, methodName, Context.class,
                            XC_MethodReplacement.returnConstant(true)
                    );
                    XposedBridge.log(TAG + ": Hooked " + CLASS_DESKTOP_MODE_STATUS + "#" + methodName + " in " + packageName);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": Error hooking " + CLASS_DESKTOP_MODE_STATUS + "#" + methodName + " in " + packageName + ": " + t.getMessage());
                }
            }
        } catch (XposedHelpers.ClassNotFoundError e) {
            // XposedBridge.log(TAG + ": Class not found: " + CLASS_DESKTOP_MODE_STATUS + " in " + packageName);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": General error hooking " + CLASS_DESKTOP_MODE_STATUS + " in " + packageName + ": " + t.getMessage());
        }
    }

    private void hookDesktopStateImpl(ClassLoader classLoader, final String packageName) {
        try {
            final Class<?> desktopStateImplClass = XposedHelpers.findClass(CLASS_DESKTOP_STATE_IMPL, classLoader);

            XposedHelpers.findAndHookConstructor(
                    desktopStateImplClass, Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object thisObject = param.thisObject;
                            XposedBridge.log(TAG + ": After DesktopStateImpl constructor in " + packageName);

                            for (String fieldName : DSI_FIELDS_TO_FORCE_TRUE_IN_CONSTRUCTOR) {
                                try {
                                    XposedHelpers.setBooleanField(thisObject, fieldName, true);
                                    XposedBridge.log(TAG + ": Set " + CLASS_DESKTOP_STATE_IMPL + "." + fieldName + " = true in " + packageName);
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + ": Error setting field " + CLASS_DESKTOP_STATE_IMPL + "." + fieldName + " to true in " + packageName + ": " + t.getMessage());
                                }
                            }

                            try {
                                XposedHelpers.setBooleanField(thisObject, DSI_FIELD_ENFORCE_DEVICE_RESTRICTIONS, false);
                                XposedBridge.log(TAG + ": Set " + CLASS_DESKTOP_STATE_IMPL + "." + DSI_FIELD_ENFORCE_DEVICE_RESTRICTIONS + " = false in " + packageName);
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": Error setting field " + CLASS_DESKTOP_STATE_IMPL + "." + DSI_FIELD_ENFORCE_DEVICE_RESTRICTIONS + " to false in " + packageName + ": " + t.getMessage());
                            }

                            try {
                                XposedHelpers.setBooleanField(thisObject, DSI_FIELD_CAN_INTERNAL_DISPLAY_HOST_DESKTOPS, false);
                                XposedBridge.log(TAG + ": Set " + CLASS_DESKTOP_STATE_IMPL + "." + DSI_FIELD_CAN_INTERNAL_DISPLAY_HOST_DESKTOPS + " = false in " + packageName);
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": Error setting field " + CLASS_DESKTOP_STATE_IMPL + "." + DSI_FIELD_CAN_INTERNAL_DISPLAY_HOST_DESKTOPS + " to false in " + packageName + ": " + t.getMessage());
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + ": Hooked constructor for " + CLASS_DESKTOP_STATE_IMPL + " in " + packageName);

            try {
                XposedHelpers.findAndHookMethod(desktopStateImplClass, "isDeviceEligibleForDesktopMode", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object thisObject = param.thisObject;
                        boolean enforceDeviceRestrictionsVal = XposedHelpers.getBooleanField(thisObject, "enforceDeviceRestrictions");
                        if (!enforceDeviceRestrictionsVal) {
                            param.setResult(true);
                            return;
                        }
                        boolean isProjectedDisplayDesktopModeTrue;
                        try {
                            ClassLoader cl = thisObject.getClass().getClassLoader();
                            Class<?> defClass = XposedHelpers.findClass(CLASS_DESKTOP_EXPERIENCE_FLAGS, cl);
                            Object flagInstance = XposedHelpers.getStaticObjectField(defClass, "ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE");
                            isProjectedDisplayDesktopModeTrue = (Boolean) XposedHelpers.callMethod(flagInstance, "isTrue");
                        } catch (Throwable t) {
                            isProjectedDisplayDesktopModeTrue = true;
                        }
                        boolean isDesktopModeSupportedVal = XposedHelpers.getBooleanField(thisObject, "isDesktopModeSupported");
                        boolean canInternalDisplayHostDesktopsVal = XposedHelpers.getBooleanField(thisObject, "canInternalDisplayHostDesktops");
                        boolean effectiveCanInternalDisplayHostDesktops = canInternalDisplayHostDesktopsVal;
                        if (isProjectedDisplayDesktopModeTrue) {
                            effectiveCanInternalDisplayHostDesktops = false;
                        }
                        boolean finalEligibility = isDesktopModeSupportedVal;
                        if (!isProjectedDisplayDesktopModeTrue) {
                            finalEligibility = finalEligibility && effectiveCanInternalDisplayHostDesktops;
                        }
                        param.setResult(finalEligibility);
                    }
                });
                XposedBridge.log(TAG + ": Hooked " + CLASS_DESKTOP_STATE_IMPL + "#isDeviceEligibleForDesktopMode with detailed logic in " + packageName);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Error hooking " + CLASS_DESKTOP_STATE_IMPL + "#isDeviceEligibleForDesktopMode with detailed logic in " + packageName + ": " + t.getMessage());
            }

            XC_MethodHook displaySupportHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object desktopStateImplInstance = param.thisObject;
                    Display display;

                    if (param.args[0] instanceof Display) {
                        display = (Display) param.args[0];
                    } else if (param.args[0] instanceof Integer) {
                        int displayId = (Integer) param.args[0];
                        DisplayManager displayManager = (DisplayManager) XposedHelpers.getObjectField(desktopStateImplInstance, "displayManager");
                        display = displayManager.getDisplay(displayId);
                    } else {
                        XposedBridge.log(TAG + ": Unknown arg type for isDesktopModeSupportedOnDisplay in " + packageName);
                        param.setResult(false);
                        return;
                    }

                    if (display == null) {
                        param.setResult(false);
                        XposedBridge.log(TAG + ": Null display in isDesktopModeSupportedOnDisplay hook in " + packageName);
                        return;
                    }

                    boolean canEnterDesktopMode = XposedHelpers.getBooleanField(desktopStateImplInstance, "canEnterDesktopMode");
                    if (!canEnterDesktopMode) {
                        param.setResult(false);
                        return;
                    }

                    int displayType = (Integer) XposedHelpers.callMethod(display, "getType");
                    Class<?> displayClass = display.getClass();
                    int typeInternalConstant = XposedHelpers.getStaticIntField(displayClass, "TYPE_INTERNAL");
                    int typeExternalConstant = XposedHelpers.getStaticIntField(displayClass, "TYPE_EXTERNAL");

                    if (displayType == typeInternalConstant) {
                        param.setResult(false);
                        XposedBridge.log(TAG + ": " + CLASS_DESKTOP_STATE_IMPL + "#isDesktopModeSupportedOnDisplay for INTERNAL display (" + display.getDisplayId() + ") forced to false in " + packageName);
                    } else if (displayType == typeExternalConstant) {
                        param.setResult(true);
                        XposedBridge.log(TAG + ": " + CLASS_DESKTOP_STATE_IMPL + "#isDesktopModeSupportedOnDisplay for EXTERNAL display (" + display.getDisplayId() + ") forced to true in " + packageName);
                    } else {
                        param.setResult(true);
                        XposedBridge.log(TAG + ": " + CLASS_DESKTOP_STATE_IMPL + "#isDesktopModeSupportedOnDisplay for OTHER display type (" + displayType + ", ID: " + display.getDisplayId() + ") forced to true in " + packageName);
                    }
                }
            };

            try {
                XposedHelpers.findAndHookMethod(desktopStateImplClass, "isDesktopModeSupportedOnDisplay", Display.class, displaySupportHook);
                XposedBridge.log(TAG + ": Hooked " + CLASS_DESKTOP_STATE_IMPL + "#isDesktopModeSupportedOnDisplay(Display) in " + packageName);
            } catch (Throwable t) { XposedBridge.log(TAG + ": Error hooking " + CLASS_DESKTOP_STATE_IMPL + "#isDesktopModeSupportedOnDisplay(Display) in " + packageName + ": " + t.getMessage()); }

            try {
                XposedHelpers.findAndHookMethod(desktopStateImplClass, "isDesktopModeSupportedOnDisplay", int.class, displaySupportHook);
                XposedBridge.log(TAG + ": Hooked " + CLASS_DESKTOP_STATE_IMPL + "#isDesktopModeSupportedOnDisplay(int) in " + packageName);
            } catch (Throwable t) { XposedBridge.log(TAG + ": Error hooking " + CLASS_DESKTOP_STATE_IMPL + "#isDesktopModeSupportedOnDisplay(int) in " + packageName + ": " + t.getMessage()); }

        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log(TAG + ": Class not found: " + CLASS_DESKTOP_STATE_IMPL + " in " + packageName);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": General error hooking " + CLASS_DESKTOP_STATE_IMPL + " in " + packageName + ": " + t.getMessage());
        }
    }

    private void hookDesktopExperienceFrameworkEnumFlags(ClassLoader classLoader, final String packageName) {
        try {
            final Class<?> defEnumClass = XposedHelpers.findClass(CLASS_DESKTOP_EXPERIENCE_FLAGS, classLoader);

            synchronized (sTargetDefEnumInstancesToForceTrue) {
                if (!sInitializedPackagesForDefEnumFlags.contains(packageName + defEnumClass.getName())) {
                    for (final String flagFieldName : DEF_ENUM_FLAGS_TO_FORCE_TRUE) {
                        try {
                            Object flagInstance = XposedHelpers.getStaticObjectField(defEnumClass, flagFieldName);
                            if (flagInstance != null) {
                                sTargetDefEnumInstancesToForceTrue.add(flagInstance);
                                XposedBridge.log(TAG + ": Cached TRUE FrameworkEnumFlag " + flagFieldName + " for " + packageName);
                            }
                        } catch (NoSuchFieldError e) {
                            XposedBridge.log(TAG + ": TRUE FrameworkEnumFlag field " + flagFieldName + " not found in " + defEnumClass.getName() + " for " + packageName);
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": Error caching TRUE FrameworkEnumFlag field " + flagFieldName + " from " + defEnumClass.getName() + " for " + packageName + ": " + t.getMessage());
                        }
                    }
                    for (final String flagFieldName : DEF_ENUM_FLAGS_TO_FORCE_FALSE) {
                        try {
                            Object flagInstance = XposedHelpers.getStaticObjectField(defEnumClass, flagFieldName);
                            if (flagInstance != null) {
                                sTargetDefEnumInstancesToForceFalse.add(flagInstance);
                                XposedBridge.log(TAG + ": Cached FALSE FrameworkEnumFlag " + flagFieldName + " for " + packageName);
                            }
                        } catch (NoSuchFieldError e) {
                            XposedBridge.log(TAG + ": FALSE FrameworkEnumFlag field " + flagFieldName + " not found in " + defEnumClass.getName() + " for " + packageName);
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": Error caching FALSE FrameworkEnumFlag field " + flagFieldName + " from " + defEnumClass.getName() + " for " + packageName + ": " + t.getMessage());
                        }
                    }
                    sInitializedPackagesForDefEnumFlags.add(packageName + defEnumClass.getName());
                }
            }

            XposedHelpers.findAndHookMethod(defEnumClass, "isTrue", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object currentFlagInstance = param.thisObject;
                    if (currentFlagInstance == null) return;

                    String flagNameForLog = "UnknownFrameworkEnumFlag";
                    try {
                        if (XposedHelpers.callMethod(currentFlagInstance, "getFlagName") instanceof String) {
                            flagNameForLog = (String) XposedHelpers.callMethod(currentFlagInstance, "getFlagName");
                        }
                    } catch (Throwable ignored) {}

                    if (sTargetDefEnumInstancesToForceTrue.contains(currentFlagInstance)) {
                        param.setResult(true);
                        XposedBridge.log(TAG + ": " + defEnumClass.getName() + "#isTrue() for " + flagNameForLog + " forced to TRUE in " + packageName);
                    } else if (sTargetDefEnumInstancesToForceFalse.contains(currentFlagInstance)) {
                        param.setResult(false);
                        XposedBridge.log(TAG + ": " + defEnumClass.getName() + "#isTrue() for " + flagNameForLog + " forced to FALSE in " + packageName);
                    }
                }
            });
            XposedBridge.log(TAG + ": Hooked " + defEnumClass.getName() + "#isTrue() in " + packageName);

        } catch (XposedHelpers.ClassNotFoundError e) {
            // XposedBridge.log(TAG + ": Class not found: " + CLASS_DESKTOP_EXPERIENCE_FLAGS + " in " + packageName);
        } catch (NoSuchMethodError e) {
            XposedBridge.log(TAG + ": Method isTrue() not found in " + CLASS_DESKTOP_EXPERIENCE_FLAGS + " in " + packageName);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": General error hooking " + CLASS_DESKTOP_EXPERIENCE_FLAGS + " in " + packageName + ": " + t.getMessage());
        }
    }
}