# scripts

构建与运行脚本，双击或在命令行运行均可。

| 脚本 | 用途 |
| --- | --- |
| `build.ps1` / `build.bat` | 打包后端，脚本内已固定 JDK 11 路径（直接用 `mvn package` 可能用到默认 Java 导致 Lombok 失效） |
| `start.ps1` / `start.bat` | 启动后端（8081），会先切到 `yunlan-backend/` 再拉 jar，保证上传目录落在后端目录下 |
| `open-mp.bat` | 查找并启动微信开发者工具，同时打开 `yunlan-mp/` 目录 |
| `gen_placeholder_img.py` | 按菜品/套餐名称生成本地占位图，写入 `upload/` 并回写数据库 `image` 字段（原始 OSS 图片已失效） |
| `selftest.py` | 接口回归自测：`python scripts/selftest.py` 跑全部，`python scripts/selftest.py day08` 只跑某模块 |

## 注意

- `gen_placeholder_img.py` 依赖 `pillow`：`pip install pillow`。
- `selftest.py` 需要服务已启动；在有 HTTP 代理的环境下要带 `NO_PROXY=localhost,127.0.0.1`。
- 自测每次运行会用随机后缀创建独立数据，可反复执行，跑完会留下少量 `自测*` 临时数据，可自行清理。
