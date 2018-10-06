package com.virjar.ratel;

import java.io.File;

/**
 * Created by virjar on 2018/10/6.
 */

public class Main {
    public static void main(String[] args) {

        File workDir = cleanWorkDir();

        System.out.println(new File("work").getAbsolutePath());
        System.out.println("hello world");
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
