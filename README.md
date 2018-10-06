# ratel

#### 项目介绍
在非root环境下，使用xposed，且不依赖于其他虚拟化容器环境。是的xposed有二次分包的能力

#### 软件架构
软件架构说明


#### 安装教程

1. xxxx
2. xxxx
3. xxxx

#### 使用说明

1. xxxx
2. xxxx
3. xxxx

#### 参与贡献

1. Fork 本项目
2. 新建 Feat_xxx 分支
3. 提交代码
4. 新建 Pull Request


### log
```
dengweijiadeMacBook-Pro:ratel virjar$ java -jar ratel-tool/build/libs/ratel-tool-1.0.0.jar test/crackmodule-debug.apk test/demoapp-debug.apk
clean working directory:/Users/virjar/git/ratel/ratel_work_dir
release ratel container apk ,into :/Users/virjar/git/ratel/ratel_work_dir/ratel-driver.apk
对目标apk解包...
十月 06, 2018 10:19:24 下午 brut.androlib.ApkDecoder decode
信息: Using Apktool @version@ on demoapp-debug.apk
十月 06, 2018 10:19:24 下午 brut.androlib.res.AndrolibResources loadMainPkg
信息: Loading resource table...
十月 06, 2018 10:19:25 下午 brut.androlib.res.AndrolibResources decodeManifestWithResources
信息: Decoding AndroidManifest.xml with resources...
十月 06, 2018 10:19:25 下午 brut.androlib.res.AndrolibResources loadFrameworkPkg
信息: Loading resource table from file: /Users/virjar/Library/apktool/framework/1.apk
十月 06, 2018 10:19:28 下午 brut.androlib.res.AndrolibResources adjustPackageManifest
信息: Regular manifest package...
十月 06, 2018 10:19:28 下午 brut.androlib.res.AndrolibResources decode
信息: Decoding file-resources...
十月 06, 2018 10:19:30 下午 brut.androlib.res.AndrolibResources decode
信息: Decoding values */* XMLs...
十月 06, 2018 10:19:30 下午 brut.androlib.Androlib decodeSourcesRaw
信息: Copying raw classes.dex file...
十月 06, 2018 10:19:30 下午 brut.androlib.Androlib decodeRawFiles
信息: Copying assets and libs...
十月 06, 2018 10:19:30 下午 brut.androlib.Androlib decodeUnknownFiles
信息: Copying unknown files...
十月 06, 2018 10:19:30 下午 brut.androlib.Androlib writeOriginalFiles
信息: Copying original files...
移出原apk中无用文件
嵌入扩展apk文件
植入容器代码...
修正AndroidManifest.xml...
重新打包apk...
十月 06, 2018 10:19:31 下午 brut.androlib.Androlib build
信息: Using Apktool @version@
十月 06, 2018 10:19:31 下午 brut.androlib.Androlib buildSourcesRaw
信息: Copying ratel_work_dir/ratel_origin_apk classes.dex file...
十月 06, 2018 10:19:31 下午 brut.androlib.Androlib buildResourcesFull
信息: Building resources...
十月 06, 2018 10:19:32 下午 brut.androlib.Androlib buildApk
信息: Building apk file...
十月 06, 2018 10:19:33 下午 brut.androlib.Androlib buildUnknownFiles
信息: Copying unknown files/dir...
十月 06, 2018 10:19:34 下午 brut.androlib.Androlib build
信息: Built apk...
清除工作目录..
输出apk路径：/Users/virjar/git/ratel/com.virjar.ratel.demoapp_1.0_1_ratel_unsigned.apk
```