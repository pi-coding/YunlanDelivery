# -*- coding: utf-8 -*-
"""给 dish / setmeal 生成本地占位图，替换已失效的课程 OSS 图片地址

课程 seed 数据里的 image 指向课程作者的阿里云 OSS，
实测已返回 403，小程序里会全部裂图。这里按名称生成一张本地图，
放到 upload/<table>/<id>.png，再把 image 改成后端可访问的绝对地址。

用法：
    python scripts/gen_placeholder_img.py            # 处理 dish 和 setmeal
    python scripts/gen_placeholder_img.py dish       # 只处理菜品
"""
import os
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DB = "fastdine"
USER = "root"
PWD = "123456"
BASE = "http://localhost:8081"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

W, H = 480, 360
FONT = r"C:\Windows\Fonts\msyh.ttc"
FONT_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"

# 一组柔和的暖色，按序号轮换，避免整屏一个颜色
PALETTE = [
    ((255, 238, 221), (255, 205, 160)),
    ((255, 245, 225), (250, 214, 165)),
    ((240, 249, 235), (198, 230, 190)),
    ((255, 240, 240), (250, 200, 200)),
    ((238, 244, 255), (196, 214, 245)),
    ((253, 243, 250), (233, 199, 222)),
    ((245, 245, 235), (214, 214, 190)),
    ((255, 248, 230), (245, 226, 170)),
]

# 套餐用偏冷的色系，和单品区分开
SETMEAL_PALETTE = [
    ((236, 246, 252), (176, 212, 236)),
    ((240, 244, 250), (190, 205, 232)),
    ((238, 250, 248), (178, 224, 216)),
]


def query(sql):
    p = subprocess.run(
        [MYSQL, "--default-character-set=utf8mb4", "-u" + USER, "-p" + PWD, DB, "-N", "-B", "-e", sql],
        capture_output=True,
    )
    out = p.stdout.decode("utf-8", "replace").replace("\r", "")
    return [line.split("\t") for line in out.split("\n") if line.strip()]


def gradient(top, bottom):
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        c = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3))
        d.line([(0, y), (W, y)], fill=c)
    return img


def render(name, ident, palette, tag):
    top, bottom = palette
    img = gradient(top, bottom)
    d = ImageDraw.Draw(img)

    # 底部一条同色系深色带，让文字有对比
    d.rectangle([0, H - 96, W, H], fill=bottom)

    # 名称居中，长名字自动缩字号
    size = 46
    while size > 22:
        f = ImageFont.truetype(FONT_BOLD, size)
        if d.textlength(name, font=f) <= W - 60:
            break
        size -= 4
    f_name = ImageFont.truetype(FONT_BOLD, size)
    tw = d.textlength(name, font=f_name)
    d.text(((W - tw) / 2, (H - 96 - size) / 2 + 20), name, font=f_name, fill=(90, 60, 40))

    f_tag = ImageFont.truetype(FONT, 22)
    d.text((18, 14), tag, font=f_tag, fill=(255, 255, 255))
    return img


def process(table, prefix):
    out_dir = os.path.join(ROOT, "yunlan-backend", "upload", table)
    os.makedirs(out_dir, exist_ok=True)

    rows = query("select id, name from %s order by id;" % table)
    if not rows:
        print("[%s] 没有数据，跳过" % table)
        return

    cases = []
    ids = []
    colors = PALETTE if table == "dish" else SETMEAL_PALETTE
    for i, row in enumerate(rows):
        if len(row) < 2:
            continue
        ident, name = row[0].strip(), row[1].strip()
        palette = colors[i % len(colors)]
        img = render(name, ident, palette, "%s#%s" % (prefix, ident))
        img.save(os.path.join(out_dir, "%s.png" % ident), "PNG")
        cases.append("when %s then '%s/upload/%s/%s.png'" % (ident, BASE, table, ident))
        ids.append(ident)

    sql = "update %s set image = case id %s end where id in (%s);" % (table, " ".join(cases), ",".join(ids))
    subprocess.run(
        [MYSQL, "--default-character-set=utf8mb4", "-u" + USER, "-p" + PWD, DB, "-e", sql],
        capture_output=True,
    )
    print("[%s] 生成 %d 张图并更新 image" % (table, len(cases)))


def main():
    targets = sys.argv[1:] or ["dish", "setmeal"]
    for t in targets:
        if t not in ("dish", "setmeal"):
            print("只支持 dish / setmeal，跳过 %s" % t)
            continue
        process(t, "菜品" if t == "dish" else "套餐")


if __name__ == "__main__":
    main()
