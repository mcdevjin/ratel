package com.virjar.crackmodule;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2018/10/6.
 */

public class DemoAppHooker implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Class<?> aClass = XposedHelpers.findClass("com.virjar.ratel.demoapp.MainActivity", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(aClass, "text", XC_MethodReplacement.returnConstant("被hook后的文本"));
    }
}
