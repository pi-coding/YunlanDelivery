# yunlan-backend

后端服务，Maven 多模块，Spring Boot 2.7.3，端口 **8081**。必须在本目录下启动 jar（上传路径以启动目录为基准）。

## 模块

| 模块 | 说明 |
| --- | --- |
| `yunlan-common` | 公共基础设施：`constant` 常量、`context` ThreadLocal、`enumeration` 枚举、`exception` 自定义异常、`json` 消息转换器、`properties` 配置绑定、`result` 统一返回体 `Result`/`PageResult`、`utils` 工具类 |
| `yunlan-pojo` | 数据对象：`entity` 与表一一对应、`dto` 入参（带 JSR-303 校验注解）、`vo` 出参 |
| `yunlan-server` | 业务层与接口层，打包产物 `yunlan-server-1.0-SNAPSHOT.jar` |

## yunlan-server 结构

| 包 | 内容 |
| --- | --- |
| `controller/admin` | 商家后台：`employee` 员工、`category` 分类、`dish` 菜品、`setmeal` 套餐、`shop` 营业状态、`order` 订单、`report` 报表、`workspace` 工作台、`common` 文件上传 |
| `controller/user` | 小程序用户端：`user` 登录、`category`/`dish`/`setmeal` 商品浏览、`shop` 营业状态、`shoppingCart` 购物车、`addressBook` 地址簿、`order` 下单与订单查询 |
| `service` / `service/impl` | 业务实现；Service 层不手工 set 公共字段，由切面统一处理 |
| `mapper` + `resources/mapper/*.xml` | MyBatis 持久层与 SQL |
| `config` | MVC（拦截器、跨域、消息转换器）、Redis、缓存、Knife4j 接口文档、WebSocket |
| `interceptor` | JWT 校验与员工/用户 id 写入 `BaseContext` |
| `aspect` | `@AutoFill` 公共字段自动填充切面 |
| `filter` | `ApiPathRewriteFilter`，把 `/api/**` 重写为 `/admin/**` |
| `handler` | `GlobalExceptionHandler` 全局异常处理 |
| `task` | `OrderTask` 超时订单定时任务 |
| `websocket` | `/ws/{sid}` 来单提醒与客户催单推送 |
| `resources/static` | 管理端 Vue 前端 dist（随 jar 一起提供，无需另起前端服务） |
| `resources/application.yml` | 主配置，业务开关集中在 `fastdine.*` 前缀下 |

## 启动

```bash
cd yunlan-backend
mvn -DskipTests package
java -jar yunlan-server/target/yunlan-server-1.0-SNAPSHOT.jar --server.port=8081
```

JDK 必须为 11。前置依赖：MySQL 8（先导入 [../sql/fastdine.sql](../sql/fastdine.sql)）、Redis（营业状态与商品缓存依赖）。

## upload/

运行后生成的菜品/套餐等上传文件，按 `yyyy/MM/dd/` 分目录存放，不进仓库。
