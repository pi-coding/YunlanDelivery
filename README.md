<div align="center">

# 云岚外卖

**商家后台 · 微信小程序 · 完整外卖点餐闭环**

[![Stack](https://img.shields.io/badge/stack-Spring%20Boot%20%C2%B7%20MiniProgram-BrightGreen)](https://github.com/pi-coding/YunlanDelivery)
[![Backend](https://img.shields.io/badge/Spring%20Boot-2.7.3-6DB33F)](https://github.com/pi-coding/YunlanDelivery)
[![Data](https://img.shields.io/badge/data-MySQL%208%20%2B%20Redis-4479A1)](https://github.com/pi-coding/YunlanDelivery)
[![API](https://img.shields.io/badge/REST%20API-75%20endpoints-blueviolet)](https://github.com/pi-coding/YunlanDelivery)
[![Test](https://img.shields.io/badge/selftest-196%20assertions-informational)](https://github.com/pi-coding/YunlanDelivery)
[![License](https://img.shields.io/badge/license-%E6%9C%AA%E5%A3%B0%E6%98%8E-lightgrey)](https://github.com/pi-coding/YunlanDelivery)

一套可以直接跑起来的外卖系统：**商家在后台管菜单、接单、看报表；顾客在小程序下单、支付、催单。**
两端共用一套后端与同一套订单状态机，Redis 承担缓存与营业状态，WebSocket 负责来单实时提醒。

</div>

---

## 目录

- [界面预览](#界面预览)
- [功能特性](#功能特性)
- [快速开始](#快速开始)
- [微信小程序](#微信小程序)
- [接口概览](#接口概览)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [常见问题](#常见问题)

## 界面预览

<table>
  <tr>
    <td width="50%"><img src="https://cdn.jsdelivr.net/gh/pi-coding/YunlanDelivery@main/preview/dashboard.png" alt="工作台"></td>
    <td width="50%"><img src="https://cdn.jsdelivr.net/gh/pi-coding/YunlanDelivery@main/preview/order.png" alt="订单管理"></td>
  </tr>
  <tr>
    <td align="center"><b>工作台</b> · 今日营业额、待接单/待派送、订单流水</td>
    <td align="center"><b>订单管理</b> · 接单 / 拒单 / 派送 / 完成 / 取消</td>
  </tr>
  <tr>
    <td width="50%"><img src="https://cdn.jsdelivr.net/gh/pi-coding/YunlanDelivery@main/preview/dish.png" alt="菜品管理"></td>
    <td width="50%"><img src="https://cdn.jsdelivr.net/gh/pi-coding/YunlanDelivery@main/preview/setmeal.png" alt="套餐管理"></td>
  </tr>
  <tr>
    <td align="center"><b>菜品管理</b> · 分类/名称/售卖状态检索，口味与配图</td>
    <td align="center"><b>套餐管理</b> · 套餐与菜品明细的组合维护</td>
  </tr>
  <tr>
    <td width="50%"><img src="https://cdn.jsdelivr.net/gh/pi-coding/YunlanDelivery@main/preview/statistics.png" alt="数据统计"></td>
    <td width="50%"><img src="https://cdn.jsdelivr.net/gh/pi-coding/YunlanDelivery@main/preview/login.png" alt="登录页"></td>
  </tr>
  <tr>
    <td align="center"><b>数据统计</b> · 营业额 / 用户 / 订单趋势 + 销量 TOP10</td>
    <td align="center"><b>商家登录</b> · JWT 鉴权，令牌 2 小时</td>
  </tr>
</table>

<p align="center">
  <img src="https://cdn.jsdelivr.net/gh/pi-coding/YunlanDelivery@main/preview/mp-home.png" width="300" alt="小程序 · 商品列表">
</p>
<p align="center"><b>小程序用户端</b> · 店铺信息、分类导航、菜品列表与底部实时购物车</p>

## 功能特性

**商家后台**（Web，后端内嵌）

- 员工管理：新增/编辑/启停用、修改密码；登录态走 JWT，密码只存摘要
- 分类与菜品：分类增删改查、菜品口味维护、批量删除、一键启停售
- 套餐管理：套餐 + 明细组合，起售价与库存状态跟随明细同步
- 店铺营业：营业中/打烊一键切换，状态存 Redis，C 端首页即时生效
- 订单管理：条件检索（订单号/手机号/时间区间）、接单、拒单、派送、完成、取消、退款
- 工作台：今日营业额、有效订单、完成率、客单价、新增用户与待办订单
- 统计报表：营业额/用户/订单趋势、销量 TOP10，按日/周/月区间查询，支持 **Excel 导出**

**小程序用户端**

- 微信登录：未配置 AppID 时自动降级为本地 mock，克隆下来即可跑通全流程
- 商品浏览：分类导航、菜品详情（口味选择）、套餐详情
- 购物车：同菜品同口味去重、加减数量、清空
- 下单支付：地址簿、餐具与备注、金额服务端重算；支付可走本地模拟或真实微信支付
- 订单中心：历史订单、订单详情、再来一单、取消订单、**催单**

**工程能力**

- JWT 双端鉴权：管理端 `token`、用户端 `authentication` 两套令牌互不通用
- Redis 缓存：C 端商品查询缓存，管理端写操作自动失效，避免脏读
- WebSocket 实时提醒：来单推送 type=1、客户催单 type=2，推送失败不影响主流程
- 定时任务：待付款超 15 分钟自动取消、派送中超 60 分钟自动完成，阈值可配
- 数据一致性：公共字段由切面自动填充，下单金额一律服务端重算，订单/地址逐接口校验归属
- 安全加固：上传校验扩展名 + 文件头魔数、分页上限、JSR-303 参数校验、全局异常处理、登录日志脱敏

## 快速开始

**环境要求**：JDK 11、Maven、MySQL 8、Redis（JDK 17+ 会让旧版 Lombok 静默失效）

```bash
git clone https://github.com/pi-coding/YunlanDelivery.git
cd YunlanDelivery

# 1. 建库并导入数据（含菜品 / 套餐种子数据）
mysql -uroot -p < sql/fastdine.sql

# 2. 启动 Redis
redis-server

# 3. 打包并启动
cd yunlan-backend
mvn -DskipTests package
java -jar yunlan-server/target/yunlan-server-1.0-SNAPSHOT.jar --server.port=8081
```

> 必须在 `yunlan-backend/` 目录下启动：上传路径是相对路径，以启动目录为基准。
> Windows 下也可直接双击 `scripts\build.bat` 与 `scripts\start.bat`。

| 入口 | 地址 |
| --- | --- |
| 商家后台 | <http://localhost:8081> |
| 接口文档 | <http://localhost:8081/doc.html> |

默认账号 `admin` / `123456`。数据库与 Redis 连接在 `yunlan-server/src/main/resources/application-dev.yml` 中修改。

导入数据后菜品图是占位图，想要真实图片走后台「菜品管理 → 上传图片」即可：

```bash
python scripts/gen_placeholder_img.py    # 重新生成本地占位图
```

## 微信小程序

1. 先启动后端，确认 <http://localhost:8081/user/shop/status> 有响应。
2. 用微信开发者工具导入 `yunlan-mp/`，AppID 选**测试号**，点编译。
3. 后端地址统一在 `yunlan-mp/common/vendor.js` 的 `baseUrl`，换机器/换端口只改这一处。

`project.config.json` 已关闭 `urlCheck`，本地 `http://localhost:8081` 可直接请求，无需勾选「不校验合法域名」。
首页会静默调用 `/user/user/login` 换取令牌，后续请求统一带 `authentication` 头。

## 接口概览

统一返回 `{code, msg, data}`：成功 `code=1`，业务失败 `code=0`，令牌失效返回 **HTTP 401**。

| 端 | 前缀 | 模块 |
| --- | --- | --- |
| 商家后台 | `/admin` | `employee` 员工 · `category` 分类 · `dish` 菜品 · `setmeal` 套餐 · `shop` 营业状态 · `order` 订单 · `report` 报表 · `workspace` 工作台 · `common` 上传/下载 |
| 小程序 | `/user` | `user` 登录 · `category` / `dish` / `setmeal` 商品 · `shop` 营业状态 · `shoppingCart` 购物车 · `addressBook` 地址簿 · `order` 下单与查询 |
| 支付回调 | `/notify` | `paySuccess` 支付成功通知（幂等处理） |

完整参数与示例见 <http://localhost:8081/doc.html>（Knife4j 在线文档）。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Spring Boot 2.7.3、Spring MVC、MyBatis、Druid、PageHelper |
| 数据 | MySQL 8（业务数据）、Redis（营业状态、商品缓存、Spring Cache） |
| 鉴权 | JWT（jjwt）+ 拦截器，双端独立密钥 |
| 实时 | WebSocket（`/ws/{sid}`）+ 定时任务（`@Scheduled`） |
| 文件 | 本地磁盘存储，静态资源映射 `/upload`，魔数校验 |
| 报表 | Apache POI 动态生成 Excel，ECharts 展示趋势 |
| 前端 | Vue 2 管理端 dist（内嵌 static/）、微信小程序原生工程 |

## 项目结构

```text
YunlanDelivery/
├── sql/fastdine.sql              全量建库 + 种子数据
├── scripts/                      构建、启动、自测、占位图生成
├── preview/                      README 界面截图
├── yunlan-backend/               Spring Boot 多模块
│   ├── yunlan-common/            常量、异常、统一返回、JWT、上下文、切面
│   ├── yunlan-pojo/              entity / dto / vo
│   ├── yunlan-server/            controller(admin|user) / service / mapper / config
│   │   └── src/main/resources/   mapper XML · application*.yml · static 管理端前端
│   └── upload/                   上传文件（运行后生成，不入库）
└── yunlan-mp/                    微信小程序用户端
    ├── pages/                    首页 / 详情 / 购物车 / 下单 / 订单 / 地址 / 我的
    └── common/                   API 封装（baseUrl 在此）、全局 store
```

每个目录内都有独立的 `README.md`，说明该目录下各文件的作用。

## 常见问题

**Q：启动后图片 404？**
A：工作目录不对。上传路径相对启动目录，务必在 `yunlan-backend/` 下执行 jar。

**Q：`/admin/shop`、`/user/shop` 报 500？**
A：Redis 没启动。营业状态与商品缓存都走 Redis，先起 Redis 再起服务。

**Q：打包报「找不到符号 log / getXxx」？**
A：JDK 版本不对。Spring Boot 2.7 + 旧版 Lombok 必须用 JDK 11。

**Q：小程序请求全失败？**
A：开发者工具未开启服务端口，或 `common/vendor.js` 里的 `baseUrl` 端口与实际不一致。

**Q：没有小程序 AppID 能跑吗？**
A：可以。后端 `fastdine.wechat.appid` 留空时登录降级为 mock openid，功能不受影响。

## License

仓库暂未声明开源许可证，默认保留所有权利。使用前请开 Issue 说明用途。
