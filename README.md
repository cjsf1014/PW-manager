## 中文 / Chinese

本仓库（GitHub：https://github.com/cjsf1014/PW-manager）提供 `app` 原生 Android 工程，支持通过 Android Studio 或命令行构建 APK。已移除所有明文签名与敏感配置，便于二次开发与发布。

### 环境准备
- JDK 17（在 IDE/命令行使用）
- Android SDK（compileSdkVersion 33）
- Gradle Wrapper 已随仓库提供

### 克隆与首次打开
1. 克隆仓库：
   ```bash
   git clone https://github.com/cjsf1014/PW-manager.git
   cd PW-manager/open_code
   ```
2. 指定 Android SDK 路径（二选一）：
   - 在 `open_code/local.properties` 中设置：
     ```
     sdk.dir=C:/Users/<你的用户名>/AppData/Local/Android/Sdk
     ```
     Windows 路径建议使用正斜杠 `/`。
   - 或设置环境变量：`ANDROID_SDK_ROOT` 指向 SDK 目录。
3. 用 Android Studio 打开 `open_code`，等待 Gradle 同步完成。

### 快速构建
- Debug 构建：
  ```bash
  ./gradlew :app:assembleDebug
  ```
- Release 构建（开源版默认关闭 R8 与资源收缩）：
  ```bash
  ./gradlew :app:assembleRelease -x lint
  ```
产物路径：`app/build/outputs/apk/<buildType>/<buildType><versionName>.apk`

### 版本号与命名规则
- 版本从 `9.0.0` 开始，每次构建自动递增补丁号 `0.0.1`。
- 规则位置：版本计算 `open_code/app/build.gradle:25–33`；输出命名 `open_code/app/build.gradle:69–73`。
- 当前版本存储：`open_code/gradle.properties`（`VERSION_MAJOR/MINOR/PATCH`）。

### 发布签名（环境变量注入）
`open_code/app/build.gradle:22` 已支持通过环境变量自动启用签名，无需改脚本。设置下列变量后，再执行 `assembleRelease`：
- `ANDROID_KEYSTORE_PATH` keystore 路径
- `ANDROID_KEYSTORE_PASSWORD` keystore 密码
- `ANDROID_KEY_ALIAS` 私钥别名
- `ANDROID_KEY_PASSWORD` 私钥密码

示例（Windows PowerShell）：
```powershell
setx ANDROID_KEYSTORE_PATH "C:\\path\\to\\your.keystore"
setx ANDROID_KEYSTORE_PASSWORD "<your-keystore-pass>"
setx ANDROID_KEY_ALIAS "<your-key-alias>"
setx ANDROID_KEY_PASSWORD "<your-key-pass>"
# 重新打开终端后执行
./gradlew :app:assembleRelease -x lint
```

示例（macOS/Linux Bash）：
```bash
export ANDROID_KEYSTORE_PATH=/path/to/your.keystore
export ANDROID_KEYSTORE_PASSWORD=<your-keystore-pass>
export ANDROID_KEY_ALIAS=<your-key-alias>
export ANDROID_KEY_PASSWORD=<your-key-pass>
./gradlew :app:assembleRelease -x lint
```

仅本地测试可使用系统 debug keystore：
```powershell
$env:ANDROID_KEYSTORE_PATH = "$env:USERPROFILE\.android\debug.keystore"
$env:ANDROID_KEYSTORE_PASSWORD = "android"
$env:ANDROID_KEY_ALIAS = "androiddebugkey"
$env:ANDROID_KEY_PASSWORD = "android"
./gradlew :app:assembleRelease -x lint
```

### R8 与资源收缩
- 开源版默认关闭，位置：`open_code/app/build.gradle:46–50`。
- 如需瘦身：开启 `minifyEnabled true`、`shrinkResources true`，并在 `app/proguard-rules.pro` 添加必要 keep 规则。

### Java 与编译配置
- 使用 Java 17：`open_code/app/build.gradle:53–55`。
- 依赖与 SDK 版本：`open_code/variables.gradle`、`open_code/gradle.properties`。

### 常见问题
- “SDK location not found”：设置 `local.properties` 的 `sdk.dir` 或 `ANDROID_SDK_ROOT`，路径使用 `/`。
- R8/资源收缩导致运行时问题：默认关闭或增加 keep 规则。
- `Using flatDir should be avoided`：Gradle 警告，后续可将 `libs` 迁移为 Maven 坐标。

### 目录结构（关键部分）
- `app/src/main/java/com/vaultai/app/**` 源码
- `app/src/main/res/**` 资源
- `app/src/main/AndroidManifest.xml` 清单
- `app/proguard-rules.pro` R8 配置
- `build.gradle`、`settings.gradle`、`variables.gradle`、`gradle.properties` 工程配置
- `gradle/wrapper/*`、`gradlew*` Gradle Wrapper

### 贡献指南
1. Fork 仓库并创建特性分支。
2. 本地运行 `assembleDebug`/`assembleRelease` 验证。
3. 发起 Pull Request 描述变更内容与影响。

### 许可证
在仓库根添加 LICENSE（MIT/Apache-2.0 等），并在代码或 README 中注明。

---

## English

This repository (GitHub: https://github.com/cjsf1014/PW-manager) provides a native Android project under `app`. Sensitive signing configs are removed for safe open-source use.

### Prerequisites
- JDK 17
- Android SDK (compileSdkVersion 33)
- Gradle Wrapper included

### Clone & Open
```bash
git clone https://github.com/cjsf1014/PW-manager.git
cd PW-manager/open_code
```
Set Android SDK path (either):
- In `open_code/local.properties`:
  ```
  sdk.dir=C:/Users/<your-username>/AppData/Local/Android/Sdk
  ```
  Prefer `/` on Windows.
- Or set `ANDROID_SDK_ROOT` to your SDK.

Open `open_code` in Android Studio.

### Build
```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease -x lint
```
Artifacts: `app/build/outputs/apk/<buildType>/<buildType><versionName>.apk`

### Versioning & Naming
- Starts at `9.0.0`, auto bumps patch on each build.
- Logic: version calc at `open_code/app/build.gradle:25–33`, naming at `open_code/app/build.gradle:69–73`.
- Values stored in `open_code/gradle.properties`.

### Release Signing (Environment Variables)
`open_code/app/build.gradle:22` enables signing automatically when these env vars exist:
- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Windows PowerShell:
```powershell
setx ANDROID_KEYSTORE_PATH "C:\\path\\to\\your.keystore"
setx ANDROID_KEYSTORE_PASSWORD "<your-keystore-pass>"
setx ANDROID_KEY_ALIAS "<your-key-alias>"
setx ANDROID_KEY_PASSWORD "<your-key-pass>"
./gradlew :app:assembleRelease -x lint
```

macOS/Linux Bash:
```bash
export ANDROID_KEYSTORE_PATH=/path/to/your.keystore
export ANDROID_KEYSTORE_PASSWORD=<your-keystore-pass>
export ANDROID_KEY_ALIAS=<your-key-alias>
export ANDROID_KEY_PASSWORD=<your-key-pass>
./gradlew :app:assembleRelease -x lint
```

Debug keystore for local testing:
```powershell
$env:ANDROID_KEYSTORE_PATH = "$env:USERPROFILE\.android\debug.keystore"
$env:ANDROID_KEYSTORE_PASSWORD = "android"
$env:ANDROID_KEY_ALIAS = "androiddebugkey"
$env:ANDROID_KEY_PASSWORD = "android"
./gradlew :app:assembleRelease -x lint
```

### R8 & Shrinking
- Disabled by default at `open_code/app/build.gradle:46–50`.
- Enable `minifyEnabled true` and `shrinkResources true` and add keep rules if needed.

### Java & Compilation
- Java 17 (`open_code/app/build.gradle:53–55`).
- Versions: `variables.gradle`, `gradle.properties`.

### FAQ
- “SDK location not found”: set `sdk.dir` or `ANDROID_SDK_ROOT`, use `/` on Windows.
- R8 issues: keep rules or disable.
- `flatDir` warning: consider migrating `libs` to Maven dependencies.

### Structure
- `app/src/main/java/com/vaultai/app/**` sources
- `app/src/main/res/**` resources
- `app/src/main/AndroidManifest.xml` manifest
- `app/proguard-rules.pro` R8 config
- `build.gradle`, `settings.gradle`, `variables.gradle`, `gradle.properties`
- `gradle/wrapper/*`, `gradlew*`

### Contributing
1. Fork, branch
2. Build locally
3. PR with description

### License
Add a LICENSE (MIT/Apache-2.0, etc.) at repo root.

