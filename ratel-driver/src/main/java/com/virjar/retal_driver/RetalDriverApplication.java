package com.virjar.retal_driver;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;

import com.taobao.android.dexposed.utility.Runtime;

import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;

import dalvik.system.PathClassLoader;
import de.robv.android.xposed.ExposedHelper;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import me.weishu.exposed.ExposedBridge;

import static com.taobao.android.dexposed.DexposedBridge.log;

/**
 * Created by virjar on 2018/10/5.<br>
 * 驱动入口，加载原始的apk，并且挂载xposed模块函数
 */

public class RetalDriverApplication extends Application {
    public static final String originAPKFileName = "ratel_origin_apk.apk";
    public static final String xposedBridgeApkFileName = "ratel_xposed_module.apk";
    public static final String retalWorkDirName = "ratel";

    /**
     * 如果原始的apk文件，配置了Application，那么我们需要加载原始的apk
     */
    static final String APPLICATION_CLASS_NAME = "APPLICATION_CLASS_NAME";

    @Override
    protected void attachBaseContext(Context base) {
        //第一步需要call supper，否则Application并不完整，还无法作为context使用，当然此时base context是可用状态
        super.attachBaseContext(base);
        //exposed框架，在driver下面定义，所以需要在替换classloader之前，完成exposed框架的so库加载
        ExposedBridge.initOnce(this, getApplicationInfo(), getClassLoader());

        if (!checkSupport()) {
            throw new IllegalStateException("epic 不支持的版本");
        }
        //释放两个apk，一个是xposed模块，一个是原生的apk，原生apk替换为当前的Application作为真正的宿主，xposed模块apk在Application被替换之前作为补丁代码注入到当前进程
        releaseApkFiles();

        //替换classloader
        Class<?> contextImplClazz = XposedHelpers.findClassIfExists("android.app.ContextImpl", base.getClassLoader());
        Object contextImpl = XposedHelpers.callStaticMethod(contextImplClazz, "getImpl", base);
        Object loadApk = XposedHelpers.getObjectField(contextImpl, "mPackageInfo");
        ClassLoader parentClassLoader = RetalDriverApplication.class.getClassLoader();
        try {
            //  Class<?> aClass = XposedHelpers.findClass("android.app.LoadedApk", RetalDriverApplication.class.getClassLoader());
            parentClassLoader = (ClassLoader) XposedHelpers.getObjectField(loadApk, "mClassLoader");
        } catch (Exception e) {
            //ignore
        }
        String originApkSourceDir = new File(ratelWorkDir(this), originAPKFileName).getAbsolutePath();
        PathClassLoader originClassLoader = new PathClassLoader(originApkSourceDir, parentClassLoader);
        XposedHelpers.setObjectField(loadApk, "mClassLoader", originClassLoader);

        //context中的resource，仍然绑定在老的apk环境下，现在把他们迁移
        ApplicationInfo appinfoInLoadedApk = (ApplicationInfo) XposedHelpers.getObjectField(loadApk, "mApplicationInfo");
        appinfoInLoadedApk.sourceDir = originApkSourceDir;
        XposedHelpers.setObjectField(loadApk, "mAppDir", originApkSourceDir);
        XposedHelpers.setObjectField(loadApk, "mResDir", originApkSourceDir);
        XposedHelpers.setObjectField(loadApk, "mResources", null);
        Resources resources = (Resources) XposedHelpers.callMethod(loadApk, "getResources", currentActivityThread());
        if (resources != null) {
            XposedHelpers.setObjectField(contextImpl, "mResources", resources);
        }
        //替换之后，再也无法访问容器apk里面的资源了，容器中的所有资源全部被替换为原始apk的资源
        loadResources(originApkSourceDir);
    }

    private void releaseApkFiles() {
        releaseAssetResource(this, originAPKFileName);
        releaseAssetResource(this, xposedBridgeApkFileName);
    }

    private String getOriginApplicationName() {
        try {
            ApplicationInfo ai = getPackageManager()
                    .getApplicationInfo(getPackageName(),
                            PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            if (bundle != null && bundle.containsKey(APPLICATION_CLASS_NAME)) {
                return bundle.getString(APPLICATION_CLASS_NAME);//className 是配置在xml文件中的。
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

        //有值的话调用该Applicaiton
        Object currentActivityThread = currentActivityThread();
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
        File modulePath = new File(ratelWorkDir(application), xposedBridgeApkFileName);
        boolean ret = loadModule(modulePath.getAbsolutePath(),
                application.getApplicationInfo(), application.getClassLoader());
        log("模块加载：" + ret);
    }

    public static File ratelWorkDir(Context context) {
        return new File(context.getFilesDir(), retalWorkDirName);
    }

    public static void releaseAssetResource(Context context, String name) {
        File destinationFileName = new File(ratelWorkDir(context), name);
        if (destinationFileName.exists()) {
            return;
        }
        File parentDir = destinationFileName.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }
        synchronized (RetalDriverApplication.class) {
            if (destinationFileName.exists()) {
                return;
            }
            AssetManager assets = context.getAssets();
            try (InputStream inputStream = assets.open(name)) {
                IOUtils.copy(inputStream, new FileOutputStream(destinationFileName));
            } catch (IOException e) {
                Log.e("weijia", "release xposed bridge apk file failed", e);
                throw new IllegalStateException(e);
            }
        }

    }

    public static Object currentActivityThread() {
        return XposedHelpers.getStaticObjectField(XposedHelpers.findClass("android.app.ActivityThread", RetalDriverApplication.class.getClassLoader()), "sCurrentActivityThread");
    }

    //以下是加载资源
    protected AssetManager mAssetManager;//资源管理器
    protected Resources mResources;//资源
    protected Resources.Theme mTheme;//主题

    protected void loadResources(String dexPath) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, dexPath);
            mAssetManager = assetManager;
        } catch (Exception e) {
            Log.i("inject", "loadResource error:" + Log.getStackTraceString(e));
            e.printStackTrace();
        }
        Resources superRes = super.getResources();
        superRes.getDisplayMetrics();
        superRes.getConfiguration();
        mResources = new Resources(mAssetManager, superRes.getDisplayMetrics(), superRes.getConfiguration());
        mTheme = mResources.newTheme();
        mTheme.setTo(super.getTheme());
    }

    @Override
    public AssetManager getAssets() {
        return mAssetManager == null ? super.getAssets() : mAssetManager;
    }

    @Override
    public Resources getResources() {
        return mResources == null ? super.getResources() : mResources;
    }

    @Override
    public Resources.Theme getTheme() {
        return mTheme == null ? super.getTheme() : mTheme;
    }


    private static boolean checkSupport() {
        if (!Runtime.isArt()) {
            return true;
        }
        try {
            Class.forName("me.weishu.epic.art.Epic");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean loadModule(final String moduleApkPath,
                                     final ApplicationInfo currentApplicationInfo, ClassLoader appClassLoader) {


        //dexposed这里，会读取xposedinstaller，但是我们并没有xposed installer，所以忽略掉xposed installers的过滤
        log("Loading modules from " + moduleApkPath);

        if (!new File(moduleApkPath).exists()) {
            log(moduleApkPath + " does not exist");
            return false;
        }

        ClassLoader hostClassLoader = ExposedBridge.class.getClassLoader();
        ClassLoader appClassLoaderWithXposed = ExposedBridge.getAppClassLoaderWithXposed(appClassLoader);

        //ClassLoader mcl = new DexClassLoader(moduleApkPath, moduleOdexDir, moduleLibPath, hostClassLoader);
        ClassLoader mcl = new PathClassLoader(moduleApkPath, hostClassLoader);
        InputStream is = mcl.getResourceAsStream("assets/xposed_init");
        if (is == null) {
            log("assets/xposed_init not found in the APK");
            return false;
        }

        BufferedReader moduleClassesReader = new BufferedReader(new InputStreamReader(is));
        try {
            String moduleClassName;
            while ((moduleClassName = moduleClassesReader.readLine()) != null) {
                moduleClassName = moduleClassName.trim();
                if (moduleClassName.isEmpty() || moduleClassName.startsWith("#"))
                    continue;

                try {
                    log("  Loading class " + moduleClassName);
                    Class<?> moduleClass = mcl.loadClass(moduleClassName);

                    if (!ExposedHelper.isIXposedMod(moduleClass)) {
                        log("    This class doesn't implement any sub-interface of IXposedMod, skipping it");
                        continue;
                    } else if (IXposedHookInitPackageResources.class.isAssignableFrom(moduleClass)) {
                        log("    This class requires resource-related hooks (which are disabled), skipping it.");
                        continue;
                    }

                    final Object moduleInstance = moduleClass.newInstance();
                    if (moduleInstance instanceof IXposedHookZygoteInit) {
                        ExposedHelper.callInitZygote(moduleApkPath, moduleInstance);
                    }

                    if (moduleInstance instanceof IXposedHookLoadPackage) {
                        // hookLoadPackage(new IXposedHookLoadPackage.Wrapper((IXposedHookLoadPackage) moduleInstance));
                        IXposedHookLoadPackage.Wrapper wrapper = new IXposedHookLoadPackage.Wrapper((IXposedHookLoadPackage) moduleInstance);
                        XposedBridge.CopyOnWriteSortedSet<XC_LoadPackage> xc_loadPackageCopyOnWriteSortedSet = new XposedBridge.CopyOnWriteSortedSet<>();
                        xc_loadPackageCopyOnWriteSortedSet.add(wrapper);
                        XC_LoadPackage.LoadPackageParam lpparam = new XC_LoadPackage.LoadPackageParam(xc_loadPackageCopyOnWriteSortedSet);
                        lpparam.packageName = currentApplicationInfo.packageName;
                        lpparam.processName = currentApplicationInfo.processName;
                        lpparam.classLoader = appClassLoaderWithXposed;
                        lpparam.appInfo = currentApplicationInfo;
                        lpparam.isFirstApplication = true;
                        XC_LoadPackage.callAll(lpparam);
                    }

                    if (moduleInstance instanceof IXposedHookInitPackageResources) {
                        // hookInitPackageResources(new IXposedHookInitPackageResources.Wrapper((IXposedHookInitPackageResources) moduleInstance));
                        // TODO: 17/12/1 Support Resource hook
                        log("not support hook resource,the hook" + moduleInstance.getClass() + " will be ignore");
                    }

                    return true;
                } catch (Throwable t) {
                    log(t);
                }
            }
        } catch (IOException e) {
            log(e);
        } finally {
            try {
                is.close();
            } catch (IOException ignored) {
            }
        }
        return false;
    }
}
