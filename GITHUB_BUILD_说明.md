# GitHub Actions 云端生成 APK

1. 打开 github.com 登录，点 New repository，名称可填 `DayPlannerOffline`，创建仓库。
2. 下载本 ZIP 并解压，进入里面的 `DayPlannerOffline` 文件夹。确保能看到 `app`、`settings.gradle.kts` 和 `.github` 文件夹。
3. 在新仓库页面点击 `uploading an existing file`（或 Add file → Upload files），将 `DayPlannerOffline` **文件夹内的内容**上传到仓库根目录。注意：GitHub 网页上传可能不会保留以点开头的隐藏文件夹 `.github`。请在仓库页面检查 `.github/workflows/build-apk.yml` 是否存在；若不存在，选择 Add file → Create new file，在文件名中输入 `.github/workflows/build-apk.yml`，复制压缩包中同名文件的内容，提交。
4. 打开仓库的 Actions → Build Android APK → Run workflow → Run workflow。首次上传到 main 分支也会自动触发编译。
5. 等待构建成功（绿色勾），点进对应的构建记录，在 Artifacts 区域下载 `DayPlannerOffline-APK`。
6. 下载得到的 artifact ZIP 解压后是 `app-debug.apk`。将 APK 传到安卓手机，允许文件管理器安装未知应用后安装。

注意：GitHub 网页不能直接把整个源码 ZIP 当作仓库内容自动解压。必须上传解压后的文件。项目是离线应用，不要求登录或服务器。调试版 APK 适合个人安装测试；本项目尚未在当前环境进行真实 Android 编译验证。如果出现红色构建错误，请提供 Actions 日志以便修复。
