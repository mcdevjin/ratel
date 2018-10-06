package com.virjar.retal_driver;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.ArrayMap;

import java.io.File;
import java.util.ArrayList;

import dalvik.system.PathClassLoader;
import de.robv.android.xposed.XposedHelpers;
import me.weishu.exposed.ExposedBridge;

/**
 * Created by virjar on 2018/10/5.<br>
 * 驱动入口，加载原始的apk，并且挂载xposed模块函数
 */

public class RetalDriverApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        releaseApkFiles();

        Class<?> contextImplClazz = XposedHelpers.findClassIfExists("android.app.ContextImpl", base.getClassLoader());
        Object contextImpl = XposedHelpers.callMethod(contextImplClazz, "getImpl", base);
        Object loadApk = XposedHelpers.getObjectField(contextImpl, "mPackageInfo");
        ClassLoader parentClassLoader = RetalDriverApplication.class.getClassLoader();
        try {
            //  Class<?> aClass = XposedHelpers.findClass("android.app.LoadedApk", RetalDriverApplication.class.getClassLoader());
            parentClassLoader = (ClassLoader) XposedHelpers.getObjectField(loadApk, "mClassLoader");
        } catch (Exception e) {
            //ignore
        }
        PathClassLoader originClassLoader = new PathClassLoader(new File(CommonUtil.ratelWorkDir(this), Constant.originAPKFileName).getAbsolutePath(), parentClassLoader);
        XposedHelpers.setObjectField(loadApk, "mClassLoader", originClassLoader);
        super.attachBaseContext(base);
    }

    private void releaseApkFiles() {
        CommonUtil.releaseAssetResource(this, Constant.originAPKFileName);
        CommonUtil.releaseAssetResource(this, Constant.xposedBridgeApkFileName);
    }

    private String getOriginApplicationName() {
        try {
            ApplicationInfo ai = getPackageManager()
                    .getApplicationInfo(getPackageName(),
                            PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            if (bundle != null && bundle.containsKey(Constant.APPLICATION_CLASS_NAME)) {
                return bundle.getString(Constant.APPLICATION_CLASS_NAME);//className 是配置在xml文件中的。
            }
            return null;
        } catch (PackageManager.NameNotFoundException e) {
            //this exception will not happened
            throw new IllegalStateException(e);
        }
    }


    @Override
    @SuppressWarnings("unchecked")
    public void onCreate() {
        super.onCreate();
        String appClassName = getOriginApplicationName();
        if (appClassName == null) {
            loadXposedModule(this);
            return;
        }

        Class<?> activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", RetalDriverApplication.class.getClassLoader());
        //有值的话调用该Applicaiton
        Object currentActivityThread = XposedHelpers.callStaticMethod(activityThreadClass,
                "currentActivityThread");


        Object mBoundApplication = XposedHelpers.getObjectField("currentActivityThread", "mBoundApplication");

        Object loadedApkInfo = XposedHelpers.getObjectField(mBoundApplication, "info");
        //把当前进程的mApplication 设置成了null
        XposedHelpers.setObjectField(loadedApkInfo, "mApplication", null);
        Application oldApplication = (Application) XposedHelpers.getObjectField(currentActivityThread, "mInitialApplication");

        //http://www.codeceo.com/article/android-context.html
        ArrayList<Application> mAllApplications = (ArrayList<Application>) XposedHelpers.getObjectField(currentActivityThread, "mAllApplications");
        mAllApplications.remove(oldApplication);//删除oldApplication

        ApplicationInfo appinfoInLoadedApk = (ApplicationInfo) XposedHelpers.getObjectField(loadedApkInfo, "mApplicationInfo");

        ApplicationInfo appinfoInAppBindData = (ApplicationInfo) XposedHelpers.getObjectField(mBoundApplication, "appInfo");

        appinfoInLoadedApk.className = appClassName;
        appinfoInAppBindData.className = appClassName;

        loadXposedModule(this);
        //makeApplication 的时候，就会调用attachBaseContext方法
        Application app = (Application) XposedHelpers.callMethod(loadedApkInfo, "makeApplication", false, null);
        XposedHelpers.setObjectField(currentActivityThread, "mInitialApplication", app);

        ArrayMap mProviderMap = (ArrayMap) XposedHelpers.getObjectField(currentActivityThread, "mProviderMap");
        for (Object providerClientRecord : mProviderMap.values()) {
            Object localProvider = XposedHelpers.getObjectField(providerClientRecord, "mLocalProvider");
            XposedHelpers.setObjectField(localProvider, "mContext", app);
        }
        app.onCreate();

    }

    private void loadXposedModule(Application application) {
        ExposedBridge.initOnce(application, application.getApplicationInfo(), application.getClassLoader());
        File modulePath = new File(CommonUtil.ratelWorkDir(application), Constant.xposedBridgeApkFileName);
        ExposedBridge.loadModule(modulePath.getAbsolutePath(), null, null,
                application.getApplicationInfo(), application.getClassLoader());
    }
}
