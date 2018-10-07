# ratel

## 项目介绍
在非root环境下，使用xposed，且不依赖于其他虚拟化容器环境。使得xposed有二次分包的能力。

## 原理
ratel本身是一个嵌入式沙盒，或者是一个加壳机，他可以给指定一个apk加壳，但是这个壳的代码很特殊，壳并不是做代码保护的，而是由壳实现AOP。
ratel在运行的时候，首先导入xposed环境，完成xposed模块加载，然后将控制权交给原始的app。由于原始app在运行之前，就被xposed框架给挟持了，所以原始app的行为将受到xposed模块的控制

### 依赖
xposed需要比较麻烦的安装环境，ratel的目标只是实现进程内的hook，所以ratel使用exposed替代xposed框架。底层使用阿里的dexposed（4.x一下）或者epic(5.x以上)，非常感谢epic作者

###限制
1. 由于进程内hook，无root权限，无法影响系统功能，也就是说修改系统app，或者Android framework的xposed模块无法生效
2. 不支持资源hook，受限于exposed
3. xposed模块加载入口，xposed加载一个模块的起始点是handleBindApplication，这个时机在apk运行的前期，但是由于ratel能够接触到最早的代码运行时机是application.attachBaseContext,所以如果你的xposed模块挂载时机在handleBindApplication到application.attachBaseContext，那么模块可能不会生效，因为retal call xposedModule的时候，事情已经发生了
4. 无法支持多个xposed模块同时生效，这个你可以尝试改着支持多个
5. xposed 模块没有独立进程能力了，也就是说对于那些有独立配置页面的xposed模块，独立配置页面将被隐藏。
6. 部分机型和系统版本不支持，比如5.x的Android系统，不支持64位模式，受限于epic作者大大
7. 尽量不要再魔改过的rom上面运行
8. 可能和原生的xposed环境冲突，建议不要再装有xposed环境的手机中运行ratel

### 子项目
apktool: 从apktool项目迁移过来，用来实现apk的重新打包
crackmodule：一个xposed模块demo，实现对demoapp的挟持
demoapp：一个app的demo，其中方法com.virjar.ratel.demoapp.MainActivity.text将会被crackmodule挟持
ratel-driver：驱动项目，作为打包产生的apk的入口，负责组装和加载其他模块
ratel-tool：一个java项目，作为ratel打包的工具入口，可以build为一个独立jar包，提供打包服务

###工具构建教程
1. ./gradlew ratel-driver:assembleDebug 构建driver项目
2. 将driver产生的apk，放置到ratel-tool的resource目录下面，并重命名为：ratel-driver.apk (cp ratel-driver/build/outpus/apk/debug/ratel-driver-debug.apk ratel-tool/src/main/resource/ratel-driver.apk)
3. 构建driver的独立jar包 ./gradlew ratel-tool:assemble 的到输出的工具类jar文件：ratel-tool/build/libs/ratel-tool-1.0.0.jar

### 工具使用教程
得到ratel jar包，执行java -jar 命令，传入两个apk文件名称即可
如：``java -jar ratel-tool/build/libs/ratel-tool-1.0.0.jar test/crackmodule-debug.apk test/demoapp-debug.apk``
命令执行完成之后，将会产生一个新的apk文件，``xxx_ratel_unsigned.apk``,该文件即为合并好的apk

对apk签名
``jarsigner  -verbose -keystore  ~/Downloads/dwjmantou.keystore com.virjar.ratel.demoapp_1.0_1_ratel_unsigned.apk dwjmantou.keystore``
具体签名方法，参考apktool apk签名教程

签名后，在手机中安装输出的apk


致谢：
https://github.com/android-hacker/exposed
https://github.com/iBotPeaches/Apktool


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

## 特别说明：
1. 不是所有系统都支持，但是应该支持目前市面上绝大多数手机
2. ratel-tool.jar 的参数，必须是两个apk文件，且其中一个为正常的apk，另一个为xposed模块apk，ratel-tool.jar的工作则是将他们糅成一个apk
3. 工具是给开发者使用的，最好不要随便拿apk和xposed模块来测试，c端模块会有很多限制功能无法使用，大部分情况应该是插入定制的xposed模块
4. 遇到问题，可以读一读epic文档 https://github.com/tiann/epic/blob/master/README_cn.md

