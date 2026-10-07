# 西楚记账 小白运行教程

本教程使用虚构账单。本地功能、生产 HTTPS 和签名 Release APK 已完成真实验收，见 [PHASE_STATUS](PHASE_STATUS.md)。只操作当前 `XichuFinance` 仓库；Android 在 `android`，后端在 `backend`。

## 只想安装使用，不开发

开发机上的正式安装包是 `dist/XichuFinance-v1.0.2.apk`，旁边有 SHA256 校验文件。把 APK 复制到 Android 8.0 以上手机，在文件管理器中点击安装；系统提示时允许此来源安装，然后打开“西楚记账”。从同签名的旧版更新时直接覆盖安装，不要卸载旧版。选择“打开本地账本”，或注册登录独立的云端账本。云端使用生产 HTTPS 接口。

**安装使用不需要 Android Studio、WSL 或 Docker。** 后面的 Step 1–10 是源码开发和本地后端教程。GitHub 克隆只包含源码，其他开发者需用自己的密钥构建；仓库不包含项目所有者的私有签名密钥。安装失败若提示签名冲突，请先保存已有数据，勿直接卸载有真实数据的旧 App。

## Step 1：准备 Android 环境

**执行什么 / 在哪里：** 安装 Android Studio，在 SDK Manager 安装 Android SDK 36.1、Build Tools 和 Platform Tools，并创建 Pixel 7 / Android 15 模拟器。用 Android Studio 打开仓库的 `android` 文件夹。Gradle JDK 使用 JDK 21。

**应该看到：** Gradle 同步完成；项目内有 `app` 模块。版本组合是 AGP 9.1.1、Gradle 9.3.1、Kotlin 2.2.10，详见 [环境审计](PROJECT_002_PREFLIGHT.md)。

**常见失败 / 解决：** 找不到 SDK 时，在 Android Studio 设置正确的 SDK 目录，或在 `android/local.properties` 写入自己的 `sdk.dir`。Windows 路径建议使用 `/`，例如 `sdk.dir=D:/Android/Sdk`。此文件不提交 Git。JDK 错误时修改 Gradle JDK，避免随意升级所有依赖。

## Step 2：准备 Backend 环境

**执行什么 / 在哪里：** 安装 Python 3.12 和 Docker Desktop，启动 Docker Desktop。Windows 使用 WSL 2 引擎；需要启用硬件虚拟化和 WSL。新开 PowerShell，在仓库根目录检查：

```powershell
python --version
docker version
docker compose version
```

**应该看到：** Python 版本、Docker Client 和 Server 版本，以及 Compose 版本。Docker Desktop 左下角显示 Engine running。

**常见失败 / 解决：** 只有 Client、没有 Server 时，先启动 Docker Desktop。`Virtualization support not detected` 需要检查 Windows 的虚拟化/WSL 条件；安装 WSL 软件本身不代表固件虚拟化已启用。`docker` 找不到时，新开终端，检查 Docker 安装目录是否加入 PATH。已有 Docker 数据时不要直接删除 Docker 目录或重置数据。参考 [Docker 官方 Windows 安装说明](https://docs.docker.com/desktop/setup/install/windows-install/)。

## Step 3：生成本地配置

**执行什么 / 在哪里：** 在仓库根目录运行：

```powershell
python scripts/setup_local_env.py
```

**应该看到：** 创建/保留本地 `.env` 的提示。脚本生成随机数据库密码和 JWT 密钥，不显示它们，也不覆盖现有配置。

**常见失败 / 解决：** Python 命令无效时使用 `py -3.12`。不要将 `.env` 发到聊天、提交 Git 或上传到公开仓库；`.env.example` 是无密钥的公开示例。

## Step 4：启动 API 和 PostgreSQL

**执行什么 / 在哪里：** 仓库根目录：

```powershell
docker compose --project-name xichufinance up -d --build --wait
docker compose --project-name xichufinance ps
```

**应该看到：** `api`、`db` 两个容器 healthy。浏览器打开 [本地 API 文档](http://127.0.0.1:8000/docs)，`GET /health` 返回 `{"status":"healthy"}`。数据库没有对外发布 5432 端口。

**常见失败 / 解决：** Docker Hub/PyPI 无法连接时，可采用已验证的官方镜像公共 ECR 和清华 Python 镜像：`python scripts/setup_local_env.py --ecr --tuna`，再重试构建，细节见 [Backend 文档](BACKEND_PHASE_3.md)。8000 已占用时先检查占用服务。数据库密码发生不匹配时，恢复原 `.env` 密码；不要删除数据卷来解决。不要运行带 `--volumes` 的清理命令。

## Step 5：构建并启动 Android Debug

**执行什么 / 在哪里：** 启动模拟器。在 `android` 文件夹运行：

```powershell
.\gradlew.bat :app:assembleDebug
```

在 Android Studio 点 Run 安装启动，或使用已加入 PATH 的 ADB：

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.xichugeek.finance/.MainActivity
```

**应该看到：** 西楚记账登录页。Debug 使用 `http://10.0.2.2:8000/`，这是 Android 模拟器访问宿主电脑的地址。

**常见失败 / 解决：** `adb devices` 为空时启动模拟器；连接失败时检查 Step 4。`10.0.2.2` 仅适用于标准 Android 模拟器，真机需要单独配置可访问的开发地址。Debug APK 是开发产物，不能替代签名 Release APK。

## Step 6：打开账本

**执行什么 / 在哪里：** 可以点“打开本地账本”，查看虚构示例并本地记账。也可以使用自己的测试邮箱和至少 8 字符密码“注册并登录”云端账本。

**应该看到：** 本地模式显示“仅保存在此设备”；云端成功显示“已同步到此设备”。云端注册不会自动导入本地示例。

**常见失败 / 解决：** 邮箱已注册时改为登录。v1 没有密码重置/邮件验证流程，测试时自行安全保存密码。云端需要联网；离线可以读已经同步的缓存，不能排队提交修改。

## Step 7：账户、分类和交易

**执行什么 / 在哪里：** 账户页添加自定义名称，例如“虚构钱包”。分类页有预置分类，也可添加。交易页点“添加”，填写正数金额、描述、日期并选择账户和分类。详情页可编辑或删除。

**应该看到：** 保存后交易列表和统计更新。金额最多两位小数，收入/支出方向由类型决定。账户余额等于期初余额加收入减支出。

**常见失败 / 解决：** `0`、负数、`1.001` 均无效。先创建账户；已关联交易的账户不能删除。请勿在账户名称或描述输入银行卡号、CVV、支付密码或身份证。

## Step 8：CSV 预览后确认导入

**执行什么 / 在哪里：** 登录云端账本。创建“支付宝”“银行卡”“微信”三个同名测试账户。将 `examples/sample_transactions.csv` 复制到模拟器 Download，或使用文件选择器可访问的位置。例如从仓库根目录：

```powershell
adb push examples/sample_transactions.csv /sdcard/Download/sample_transactions.csv
```

首页打开“CSV 账单导入”，选择文件，检查有效/错误/重复行，再点“确认导入”。

**应该看到：** 选择文件仅预览；确认后写入。再次导入同一文件时重复行被跳过。

**常见失败 / 解决：** 必须是 UTF-8 标准 CSV；账户名称须匹配。预览 15 分钟过期后重新选择。最多 512 KiB / 500 行，较大账单请拆分。样例日期为 2026 年 9 月，统计要切换到对应月份。微信/支付宝/银行原始导出文件没有验证支持，先转换成 [标准格式](CSV_FORMAT.md)。

## Step 9：统计、分类规则与问账单

**执行什么 / 在哪里：** 首页进入统计分析，切换月份看趋势、分类占比、最大支出。设置页进入自动分类规则，添加“咖啡 → 娱乐”等测试规则，再在交易编辑器点“自动分类”。云端账本可进入“问问我的账单”，输入或选择示例问题，再点查询。

**应该看到：** 用户规则优先于内置关键词；建议可手动改。缺失分类的 CSV 行也采用分类规则。Ask 显示数据库计算的精确金额，UI 显示 `AI Enhancement Disabled`。

**常见失败 / 解决：** 未识别的问题会显示支持范围；Ask 是有限中文问题模板，需要联网和云端登录。没有付费 AI Key 也可完成以上功能。切换账户或用户后，务必使用对应账本的分类/规则。

## Step 10：运行验证

**执行什么 / 在哪里：** 仓库根目录运行真实本地 HTTP 验证和秘密模式检查：

```powershell
python scripts/validate_backend.py
python scripts/secret_review.py
```

后端单元测试需在 `backend` 目录安装隔离测试环境：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m pytest -q
```

Android 在 `android` 目录执行 ` .\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest`；模拟器与 Backend 应已启动。

**应该看到：** pytest/Gradle 测试通过，真实 HTTP 输出 `BACKEND_LOCAL = PASS`。脚本默认只允许本机 HTTP，创建虚构用户并清理其交易/账户，保留测试用户及分类。生产 HTTPS 模式需要专门参数以及服务器所有者对虚构数据写入的授权，不能把本地验证命令直接用于他人的生产服务。

**常见失败 / 解决：** pytest 从仓库根目录启动会找不到 `app`；改到 `backend`。设备 UI 测试要求默认 Debug API 地址和运行中的本地服务。Gradle 设备测试可能在完成后卸载测试 APK/应用，需要重新安装后体验。

## Step 11：生产服务器

**执行什么 / 在哪里：** 本项目所有者的生产接口已部署到 `https://finance-api.demo.xichugeek.com/`。浏览器访问它的 `/health` 或 `/docs`。自行部署时，先按 [SERVER_DEPLOYMENT](SERVER_DEPLOYMENT.md) 只读审计资源、现有服务、DNS 和备份，再让服务器所有者确认改动/回滚范围，之后才执行文档中的 SSH 命令。

**应该看到：** health 返回 healthy，HTTPS 证书有效；独立 API/PostgreSQL healthy，数据库无公网端口，原有站点仍可访问。只在获得授权后运行生产虚构账单验收和备份/独立恢复演练。

**常见失败 / 解决：** DNS 未生效时检查新增 A 记录和公共解析；证书失败时检查域名及 80/443，勿跳过 TLS 校验。数据库密码错误应恢复原私密配置，不能删数据卷。具体备份位置、恢复、回滚和现有 Caddy 单文件挂载注意事项均在部署文档。

## Step 12：构建签名 Release

**执行什么 / 在哪里：** 项目所有者的密钥和配置已在仓库外安全保存并单独备份。在仓库根目录运行：

```powershell
python scripts/build_release.py --instrumentation --bundle
```

其他开发者需要按 [ANDROID_RELEASE](ANDROID_RELEASE.md) 创建自己的 PKCS12 密钥，设置四个私密签名变量/JSON；它们不能提交到 Git。默认配置文件位置是 `%USERPROFILE%\.xichufinance\signing\signing.credentials.json`。使用自己部署的域名时要先验证生产 HTTPS。

**应该看到：** 干净构建、JVM 测试和 Lint 通过；`dist` 中有 `XichuFinance-v1.0.2.apk`、SHA256 文件以及可选 AAB。APK 必须继续经过 apksigner 签名检查和真实安装验收；构建脚本本身不会代替运行检查。

**常见失败 / 解决：** 缺少签名配置时恢复仓库外的私密文件，不要改用 Debug 密钥。丢失密钥会妨碍兼容更新，必须安全备份。证书/网络失败时检查连接，不要放宽 HTTPS 校验。

## Step 13：安装、检查和使用 Release

**执行什么 / 在哪里：** 仓库根目录，ADB 已加入 PATH；用自己的设备序号替换示例：

```powershell
Get-FileHash dist/XichuFinance-v1.0.2.apk -Algorithm SHA256
adb devices
adb -s emulator-5554 install -r dist/XichuFinance-v1.0.2.apk
adb -s emulator-5554 shell am start -W -n com.xichugeek.finance/.MainActivity
```

**应该看到：** 校验值与 sidecar 一致，安装 Success，启动 status ok。登录/注册后创建测试账户、增删改交易、预览并确认 CSV、重复导入、看统计、问账单，再关闭打开检查数据。自动 Release 工作流命令在构建指南；它会写入虚构生产数据，运行前须取得服务所有者授权。

**常见失败 / 解决：** Debug/Release 签名不同，更新不兼容时先保护原数据。云端断网/连接超时会保留缓存，恢复联网后去设置点“刷新云端账本”；写入超时结果可能不确定，先刷新确认再重试。令牌 12 小时过期后重新登录。真实手机/OEM 行为未完成全覆盖验证。

当前名称与图标更新见 [v1.0.2 验收与截图](RELEASE_NOTES_v1.0.2.md)，此前完整功能结果见 [v1.0.1 实际验收](UI_REFRESH_v1.0.1.md)。v1 没有完整账号删除、密码找回和自动备份服务；使用敏感数据前阅读 [隐私](PRIVACY.md) 和 [安全](SECURITY.md)。
