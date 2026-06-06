# A4L Dashboard / A4L车机桌面

这是为 JLXC 的奥迪 A4L 车机项目准备的第一版 Dashboard Launcher。

## 当前版本：v0.1.0-alpha

已实现：

- 2560×720 / 32:9 横屏全屏界面
- 隐藏 Android 原生状态栏和导航栏
- 左侧导航栏：首页、导航、音乐、车辆、全景、应用、我的
- 车辆按钮打开：`com.ts.MainUI/com.ts.can.audi.xhd.CanAudiWithCDExdActivity`
- 全景按钮打开：`com.baony.avm360/com.baony.ui.activity.AVMBVActivity`
- 应用抽屉，可长按隐藏应用
- 设置页：车主名、车牌、默认导航/音乐/战斗模式/运动模式 App、天气城市、天气 API Key
- 高德地图车机版小组件容器，可通过“选择小组件”加入导航小组件
- 音乐信息读取：当前播放标题、歌手、封面、播放/暂停/上一曲/下一曲
- 蓝牙设备卡片：尝试显示已配对设备名，失败时显示 `Miku Phone`
- 常用应用栏：长按可替换应用和重命名
- 车辆数据读取：复用 `ICarInfoService.requestCarBaseInfo()` 与 `ITsSpeechCar`
- 显示车速、转速、续航、油量、车门、后备箱、前机盖、左右转向、远光、双闪、前后雷达
- 日间/夜间模式切换
- 天气接口：预留高德 Web 服务天气 API Key 和城市/adcode 设置

## 使用建议

1. 第一次安装后，建议将本 App 设置为默认主页/Launcher。
2. 导航小组件：进入“我的/设置”或导航卡片右上角，选择高德地图车机版小组件。
3. 音乐信息：需要在系统设置中允许本 App 的通知读取权限。
4. 天气：在设置中填写高德开放平台 Web 服务天气 Key，城市默认 `360300`（江西萍乡）。
5. 车辆数据：默认 1 秒轮询一次，避免高频调用造成 MainUI 不稳定。

## 安全说明

第一版不主动调用高风险 `requestCarDoorInfo()` / `GetCarDoorInfo()`。车门状态优先从 `baseInfo[61-66]` 读取。

## 编译

GitHub Actions → Build APK → Run workflow。
