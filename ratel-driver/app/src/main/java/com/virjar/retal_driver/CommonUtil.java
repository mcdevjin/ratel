package com.virjar.retal_driver;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by virjar on 2018/10/5.
 */

public class CommonUtil {
    public static File ratelWorkDir(Context context) {
        return new File(context.getFilesDir(), Constant.retalWorkDirName);
    }

    public static void releaseAssetResource(Context context, String name) {
        File destinationFileName = new File(ratelWorkDir(context), name);
        if (destinationFileName.exists()) {
            return;
        }
        synchronized (CommonUtil.class) {
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
}
