package com.virjar.retal_driver;

/**
 * Created by virjar on 2018/10/5.
 */

public class Constant {
    public static final String originAPKFileName = "ratel_origin_apk.apk";
    public static final String xposedBridgeApkFileName = "ratel_xposed_module.apk";
    public static final String retalWorkDirName = "ratel";

    /**
     * 如果原始的apk文件，配置了Application，那么我们需要加载原始的apk
     */
    static final String APPLICATION_CLASS_NAME = "APPLICATION_CLASS_NAME";
}
