# MikuCarLauncher / A4L车机桌面 v2

v2 重点修复：

- 重新调整首页视觉比例，更接近 2560×720 参考图：左侧栏、导航卡片、音乐卡片、蓝牙卡片、常用应用、右侧 Racing Miku/A4L 横幅、车辆状态卡片、天气卡片。
- 内置 Racing Miku + A4L 横幅素材，用作右侧主视觉背景。
- 修复“选择小组件”逻辑：不再直接调用系统通用小组件选择器，改为软件内列出当前系统可用小组件，再绑定到导航卡片。
- 如果系统拒绝绑定小组件，会提示用户将本软件设为默认主页，或通过 ADB 授权 appwidget 绑定。

ADB 授权小组件绑定备用命令：

```bash
adb shell appwidget grantbind --package com.jlxc.a4ldashboard
```

如果车机系统不支持 `grantbind`，请先把本软件设为默认主页，再重新点击“选择小组件”。
