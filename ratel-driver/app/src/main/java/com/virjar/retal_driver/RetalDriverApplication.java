package com.virjar.retal_driver;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import dalvik.system.PathClassLoader;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by virjar on 2018/10/5.<br>
 * 驱动入口，加载原始的apk，并且挂载xposed模块函数
 */

public class RetalDriverApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        // 配置动态加载环境
        Object currentActivityThread = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", RetalDriverApplication.class.getClassLoader())
                , "currentActivityThread");//获取主线程对象 http://blog.csdn.net/myarrow/article/details/14223493
        String packageName = this.getPackageName();//当前apk的包名
        ArrayMap mPackages = (ArrayMap) XposedHelpers.getObjectField(currentActivityThread, "mPackages");
        WeakReference wr = (WeakReference) mPackages.get(packageName);

        releaseApkFiles();

        ClassLoader parentClassLoader = RetalDriverApplication.class.getClassLoader();
        try {
            //  Class<?> aClass = XposedHelpers.findClass("android.app.LoadedApk", RetalDriverApplication.class.getClassLoader());
            parentClassLoader = (ClassLoader) XposedHelpers.getObjectField(wr.get(), "mClassLoader");
        } catch (Exception e) {
            //ignore
        }
        PathClassLoader originClassLoader = new PathClassLoader(new File(CommonUtil.ratelWorkDir(this), Constant.originAPKFileName).getAbsolutePath(), parentClassLoader);
        XposedHelpers.setObjectField(wr.get(), "mClassLoader", originClassLoader);
        super.attachBaseContext(base);
    }

    private void releaseApkFiles() {
        CommonUtil.releaseAssetResource(this, Constant.originAPKFileName);
        CommonUtil.releaseAssetResource(this, Constant.xposedBridgeApkFileName);
    }


    @Override
    @SuppressWarnings("unchecked")
    public void onCreate() {
        super.onCreate();

        Log.i("demo", "onCreate");
        // 如果源应用配置有Appliction对象，则替换为源应用Applicaiton，以便不影响源程序逻辑。
        String appClassName = null;
        try {
            ApplicationInfo ai = getPackageManager()
                    .getApplicationInfo(getPackageName(),
                            PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            if (bundle != null && bundle.containsKey(Constant.APPLICATION_CLASS_NAME)) {
                appClassName = bundle.getString(Constant.APPLICATION_CLASS_NAME);//className 是配置在xml文件中的。
            } else {
                Log.i("demo", "have no application class name");
                return;
            }
        } catch (PackageManager.NameNotFoundException e) {
            //this exception will not happened
            throw new IllegalStateException(e);
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

        ApplicationInfo appinfo_In_LoadedApk = (ApplicationInfo) XposedHelpers.getObjectField(loadedApkInfo, "mApplicationInfo");

        ApplicationInfo appinfo_In_AppBindData = (ApplicationInfo) XposedHelpers.getObjectField(mBoundApplication, "appInfo");

        appinfo_In_LoadedApk.className = appClassName;
        appinfo_In_AppBindData.className = appClassName;
        Application app = (Application) XposedHelpers.callMethod(loadedApkInfo, "makeApplication", false, null);
        XposedHelpers.setObjectField(currentActivityThread, "mInitialApplication", app);

        ArrayMap mProviderMap = (ArrayMap) XposedHelpers.getObjectField(currentActivityThread, "mProviderMap");
        for (Object providerClientRecord : mProviderMap.values()) {
            Object localProvider = XposedHelpers.getObjectField(providerClientRecord, "mLocalProvider");
            XposedHelpers.setObjectField(localProvider, "mContext", app);
        }
        app.onCreate();

    }
}
