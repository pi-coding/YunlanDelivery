# yunlan-mp

微信小程序用户端，可直接用微信开发者工具打开的原生小程序工程。

| 路径 | 说明 |
| --- | --- |
| `app.js` / `app.json` / `app.wxss` | 小程序入口、全局配置（页面注册、tabBar、窗口样式）与全局样式 |
| `pages/` | 页面：`index` 首页（分类 + 商品）、`details` 商品详情、`pay` 订单提交、`order` 订单确认、`historyOrder` 历史订单、`address` 与 `addOrEditAddress` 地址簿、`my` 个人中心、`common` 通用占位页、`nonet` 断网提示 |
| `common/vendor.js` | 三方库与 API 请求封装，**后端地址 `baseUrl` 就在这里**，换机器/换端口只改这一处 |
| `common/main.js` | 全局 store 与启动逻辑（登录、购物车数量等） |
| `components/` | 复用组件 |
| `static/` | 图片等静态资源 |
| `uni_modules/` | uni-app 插件资源 |
| `node-modules/` | 编译产物里的依赖占位，无实际内容，已忽略 |
| `project.config.json` | 项目配置：AppID 为 `touristappid`、关闭 `urlCheck`、关闭 ES6→ES5 转译 |

## 运行

1. 先启动后端：`http://localhost:8081/user/shop/status` 应有响应。
2. 双击 [../scripts/open-mp.bat](../scripts/open-mp.bat)，自动拉起微信开发者工具并打开本目录。
3. 导入项目 → AppID 选「测试号」→ 点击编译（无需再执行 npm 构建）。

首次建议使用前在开发者工具里开启 **设置 → 安全设置 → 服务端口**。

## 相关约定

- 登录：未配置 `fastdine.wechat.appid/secret` 时，后端自动降级为本地 mock openid，无需真实 AppID 也能跑通全流程。
- 鉴权：用户端令牌放在请求头 `authentication`（与管理端 `token` 不同），由 `common/vendor.js` 统一注入。
- 域名：`project.config.json` 已关闭 `urlCheck`，本地可直连 `http://localhost:8081`；真机预览需在开发者工具里勾选「不校验合法域名」或使用局域网 IP。
