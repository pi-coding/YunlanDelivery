# sql

| 文件 | 说明 |
| --- | --- |
| `fastdine.sql` | 全量建库脚本：表结构 + 初始化数据（员工、分类、菜品、口味、套餐及明细）。已合并课程分段的 seed 数据，**只导入这一个文件即可完成初始化** |

```bash
mysql -uroot -p < sql/fastdine.sql
```

导入后基线：1 个员工（`admin`）、10 个分类、24 个菜品、24 条口味数据。

图片在原始的 OSS 地址已失效，脚本中的 `image` 字段改指本地 `upload/` 路径，导入后执行一次生成脚本：

```bash
python scripts/gen_placeholder_img.py
```

后续结构变更按 `dayNN-xxx.sql` 形式追加增量脚本。
