package com.virjar.ratel;

import com.google.common.io.Files;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipFile;

import brut.androlib.Androlib;
import brut.androlib.ApkDecoder;
import brut.androlib.ApkOptions;
import brut.androlib.res.xml.ResXmlPatcher;

/**
 * Created by virjar on 2018/10/6.
 */

public class Main {

    private static final String driverAPKPath = "ratel-driver.apk";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.print("must pass 2 apk file ");
            System.exit(-1);
        }

        //检查输入参数
        File apk1 = new File(args[0]);
        File apk2 = new File(args[1]);
        if (!checkAPKFile(apk1)) {
            System.out.println(args[0] + " is not a illegal apk file");
            return;
        }

        if (!checkAPKFile(apk2)) {
            System.out.println(args[1] + " is not a illegal apk file");
            return;
        }

        //确定那个apk是原始apk，那个是xposed模块apk

        File xposedApk;
        File originApk;


        ApkFile apkFile1 = new ApkFile(apk1);
        byte[] xposedConfig = apkFile1.getFileData("assets/xposed_init");

        ApkFile apkFile2 = new ApkFile(apk2);
        byte[] xposedConfig2 = apkFile2.getFileData("assets/xposed_init");

        if (xposedConfig == null && xposedConfig2 == null) {
            System.out.println("两个文件必须有一个是xposed模块apk");
            return;
        }
        if (xposedConfig != null && xposedConfig2 != null) {
            System.out.println("两个文件都是xposed模块apk");
            return;
        }

        ApkMeta apkMeta;
        if (xposedConfig == null) {
            xposedApk = apk2;
            originApk = apk1;
            apkMeta = apkFile1.getApkMeta();
        } else {
            xposedApk = apk1;
            originApk = apk2;
            apkMeta = apkFile2.getApkMeta();
        }
        IOUtils.closeQuietly(apkFile1);
        IOUtils.closeQuietly(apkFile2);

        //工作目录准备
        File workDir = cleanWorkDir();
        System.out.println("clean working directory:" + workDir.getAbsolutePath());
        File driverAPKFile = new File(workDir, driverAPKPath);
        System.out.println("release ratel container apk ,into :" + driverAPKFile.getAbsolutePath());
        IOUtils.copy(Main.class.getClassLoader().getResourceAsStream(driverAPKPath), new FileOutputStream(driverAPKFile));

        System.out.println("对目标apk解包...");
        ApkDecoder decoder = new ApkDecoder();
        decoder.setApkFile(originApk);
        File originBuildDir = new File(workDir, "ratel_origin_apk");
        decoder.setOutDir(originBuildDir);
        //不对源码进行解码
        decoder.setDecodeSources(ApkDecoder.DECODE_SOURCES_NONE);
        decoder.setKeepBrokenResources(true);
        decoder.setForceDelete(true);
        decoder.setForceDecodeManifest(ApkDecoder.FORCE_DECODE_MANIFEST_FULL);
        decoder.setDecodeAssets(ApkDecoder.DECODE_ASSETS_NONE);
        decoder.decode();

        System.out.println("移出原apk中无用文件");
        File[] files = originBuildDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().equalsIgnoreCase(".dex")) {
                    file.delete();
                } else if (file.isDirectory()) {
                    String name = file.getName();
                    if (name.equalsIgnoreCase("unknown")
                            || name.equalsIgnoreCase("assets")
                            || name.equalsIgnoreCase("lib")
                            || name.equalsIgnoreCase("libs")
                            || name.equalsIgnoreCase("kotlin")
                            || name.startsWith("smali")) {
                        file.delete();
                    }
                }
            }
        }

        File assetsDir = new File(originBuildDir, "assets");
        assetsDir.mkdirs();
        System.out.println("嵌入扩展apk文件");
        Files.copy(originApk, new File(assetsDir, originAPKFileName));
        Files.copy(xposedApk, new File(assetsDir, xposedBridgeApkFileName));


        System.out.println("植入容器代码...");
        ZipFile zipFile = new ZipFile(driverAPKFile);
        InputStream inputStream = zipFile.getInputStream(zipFile.getEntry(DEX_FILE));
        IOUtils.copy(inputStream, new FileOutputStream(new File(originBuildDir, DEX_FILE)));
        IOUtils.closeQuietly(inputStream);
        IOUtils.closeQuietly(zipFile);

        System.out.println("修正AndroidManifest.xml...");

        File manifestFile = new File(originBuildDir, manifestFileName);
        Document document = ResXmlPatcher.loadDocument(manifestFile);
        NodeList applicationNodeList = document.getElementsByTagName("application");
        if (applicationNodeList.getLength() == 0) {
            throw new IllegalStateException("the manifest xml file must has application node");
        }
        Element item = (Element) applicationNodeList.item(0);
        String applicationClass = item.getAttribute("android:name");
        item.setAttribute("android:name", driverApplicationClass);
        if (StringUtils.isNotBlank(applicationClass)) {
            //原始的Application配置，防止到meta中，让驱动器负责加载原始Application
            Element applicationMeta = document.createElement("meta-data");
            applicationMeta.setAttribute("android:name", APPLICATION_CLASS_NAME);
            applicationMeta.setAttribute("android:value", applicationClass);
            Node firstChild = item.getFirstChild();
            if (firstChild != null) {
                item.insertBefore(applicationMeta, firstChild);
            } else {
                item.appendChild(applicationMeta);
            }
            // item.appendChild(applicationMeta);
        }
        ResXmlPatcher.saveDocument(manifestFile, document);

        System.out.println("重新打包apk...");
        File outFile = new File(workDir.getParentFile(),
                apkMeta.getPackageName() + "_" + apkMeta.getVersionName() + "_" + apkMeta.getVersionCode() + "_ratel_unsigned.apk");
        ApkOptions apkOptions = new ApkOptions();
        apkOptions.forceBuildAll = true;
        new Androlib(apkOptions).build(originBuildDir, outFile);

        System.out.println("清除工作目录..");
        workDir.delete();
        System.out.println("输出apk路径：" + outFile.getAbsolutePath());

    }

    private static final String APPLICATION_CLASS_NAME = "APPLICATION_CLASS_NAME";
    private static final String driverApplicationClass = "com.virjar.retal_driver.RetalDriverApplication";
    private static final String manifestFileName = "AndroidManifest.xml";
    private static final String DEX_FILE = "classes.dex";
    private static final String originAPKFileName = "ratel_origin_apk.apk";
    private static final String xposedBridgeApkFileName = "ratel_xposed_module.apk";

    private static boolean checkAPKFile(File file) {
        try (ApkFile apkFile = new ApkFile(file)) {
            apkFile.getApkMeta();
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    private static File cleanWorkDir() {
        File workDir = new File("ratel_work_dir");
        if (!workDir.exists()) {
            workDir.mkdirs();
            return workDir;
        }
        if (!workDir.isDirectory()) {
            workDir.delete();
        }
        File[] files = workDir.listFiles();
        if (files == null) {
            return workDir;
        }
        for (File file : files) {
            file.delete();
        }
        return workDir;
    }
}
