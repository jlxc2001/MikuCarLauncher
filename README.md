# MikuCarLauncher / A4L车机桌面 v3

第一版素材接入版：使用用户提供的白色 Audi A4L 主视觉图片，重排首页主视觉区域，继续保留车辆数据读取、左侧导航、导航小组件容器、音乐信息、蓝牙卡片、常用应用、车辆状态和天气卡片。

## 当前重点

- 2560×720 横屏全屏适配
- 左侧：首页 / 导航 / 音乐 / 车辆 / 全景 / 应用 / 我的
- 车辆按钮打开 `com.ts.MainUI/com.ts.can.audi.xhd.CanAudiWithCDExdActivity`
- 全景按钮打开 `com.baony.avm360/com.baony.ui.activity.AVMBVActivity`
- 主视觉区域接入 `a4l_hero_main.png`
- 车辆数据继续通过 `com.ts.can.carinfo.CarInfoService` 和 `TsCarService` 读取
- 小组件容器保留，后续继续针对车机 ROM 调整

## 编译

GitHub Actions → Build APK → Run workflow。
