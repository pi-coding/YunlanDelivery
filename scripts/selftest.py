# -*- coding: utf-8 -*-
"""云岚外卖接口自测脚本

用法：
    python selftest.py            # 跑全部已实现章节
    python selftest.py day02      # 只跑 day02（员工管理 + 分类管理）

约定：
    每个章节写一个 run_dayNN() 函数，内部用 check() 记录断言，最后统一汇总。
    后续 day03…day12 按同样方式追加函数，并在 ALL 列表里注册即可。
"""
import base64
import json
import os
import socket
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8081"

# 每次运行带唯一后缀，保证脚本可以反复执行（不依赖上次是否清理干净）
RUN_ID = str(int(time.time()))[-6:]
EMP_USERNAME = "selftest_" + RUN_ID
CAT_NAME = "自测分类" + RUN_ID
DISH_NAME = "自测菜品" + RUN_ID

_results = []
_token = {"value": None}


# ---------------------------------------------------------------- 基础工具
def call(method, path, body=None, token=None, token_name="token"):
    """发起请求，返回 (HTTP状态码, 响应文本)
    token_name：管理端用 token，用户端用 authentication
    """
    headers = {}
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json;charset=UTF-8"
    if token:
        headers[token_name] = token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "ignore")


def ucall(method, path, body=None, token=None):
    """C 端请求：令牌放在 authentication 头里"""
    return call(method, path, body, token, token_name="authentication")


def call_raw(method, path, token=None):
    """下载类接口用：返回 (状态码, 响应头 dict, 原始字节)"""
    headers = {}
    if token:
        headers["token"] = token
    req = urllib.request.Request(BASE + path, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, dict(resp.headers), resp.read()
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read()


def check(label, cond, detail=""):
    _results.append((label, "PASS" if cond else "FAIL", str(detail)[:150]))


def ok(body):
    """业务成功：code == 1"""
    return '"code":1' in body.replace(" ", "")


def data_of(body):
    return json.loads(body).get("data")


def login():
    """管理员登录，返回 token（缓存）"""
    if _token["value"]:
        return _token["value"]
    st, body = call("POST", "/admin/employee/login", {"username": "admin", "password": "123456"})
    check("day02 员工登录 admin/123456", st == 200 and ok(body), body)
    _token["value"] = (data_of(body) or {}).get("token") if ok(body) else None
    return _token["value"]


_utoken = {"value": None}


def user_token():
    """C 端（小程序游客）登录，返回用户端 token（缓存）"""
    if _utoken["value"]:
        return _utoken["value"]
    st, body = call("POST", "/user/user/login", {"code": "selftest-code"})
    _utoken["value"] = (data_of(body) or {}).get("token") if ok(body) else None
    return _utoken["value"]


# ---------------------------------------------------------------- day02
def run_day02():
    token = login()
    if not token:
        return

    # 令牌校验
    st, _ = call("GET", "/admin/employee/page?page=1&pageSize=10")
    check("day02 无令牌访问返回401", st == 401, "HTTP %s" % st)

    # 新增员工
    emp = {"name": "自测张三", "username": EMP_USERNAME, "phone": "13800001111",
           "sex": "1", "idNumber": "110101199003077777"}
    st, body = call("POST", "/admin/employee", emp, token)
    check("day02 新增员工", st == 200 and ok(body), body)

    st, body = call("POST", "/admin/employee", emp, token)
    check("day02 重复用户名提示已存在", "已存在" in body, body)

    # 分页 + 模糊查询
    st, body = call("GET", "/admin/employee/page?page=1&pageSize=10", None, token)
    d = data_of(body) or {}
    records = d.get("records") or []
    check("day02 员工分页查询", st == 200 and d.get("total") is not None and records, "total=%s" % d.get("total"))
    check("day02 时间格式 yyyy-MM-dd HH:mm",
          bool(records) and len(str(records[0].get("createTime"))) == 16,
          records[0].get("createTime") if records else "无数据")

    emp_id = next((r["id"] for r in records if r["username"] == EMP_USERNAME), None)
    check("day02 新增记录创建人=当前登录id",
          any(r["username"] == EMP_USERNAME and r["createUser"] == 1 for r in records),
          [(r["username"], r["createUser"]) for r in records])

    st, body = call("GET", "/admin/employee/page?page=1&pageSize=10&name=%E8%87%AA%E6%B5%8B", None, token)
    check("day02 按姓名模糊查询", st == 200 and (data_of(body) or {}).get("total", 0) >= 1, body)

    # 启用禁用
    st, _ = call("POST", "/admin/employee/status/0?id=%s" % emp_id, None, token)
    st2, body2 = call("GET", "/admin/employee/%s" % emp_id, None, token)
    check("day02 禁用员工账号", st == 200 and (data_of(body2) or {}).get("status") == 0, body2)

    st, body = call("POST", "/admin/employee/login", {"username": EMP_USERNAME, "password": "123456"})
    check("day02 禁用账号禁止登录", "锁定" in body, body)
    call("POST", "/admin/employee/status/1?id=%s" % emp_id, None, token)

    # 回显 + 编辑
    st, body = call("GET", "/admin/employee/%s" % emp_id, None, token)
    d = data_of(body) or {}
    check("day02 根据id回显(密码脱敏)", d.get("password") == "****", d)

    upd = {"id": emp_id, "name": "自测张三丰", "username": EMP_USERNAME,
           "phone": "13900002222", "sex": "0", "idNumber": "110101199003077777"}
    st, body = call("PUT", "/admin/employee", upd, token)
    st2, body2 = call("GET", "/admin/employee/%s" % emp_id, None, token)
    d = data_of(body2) or {}
    check("day02 编辑员工", st == 200 and d.get("name") == "自测张三丰", d.get("name"))

    # 修改密码：只能改当前登录员工的，改完必须立刻还原，否则后续用例和手动登录都会挂
    # （课程前端有「修改密码」弹窗，但课程后端没这个接口，不补点了就是 404）
    NEW_PWD = "selftest2026"
    try:
        st, body = call("PUT", "/admin/employee/editPassword",
                        {"oldPassword": "123456", "newPassword": NEW_PWD, "affirmPassword": NEW_PWD}, token)
        check("day02 修改密码成功", st == 200 and ok(body), body[:80])

        st, body = call("POST", "/admin/employee/login", {"username": "admin", "password": NEW_PWD})
        check("day02 新密码可登录", ok(body), body[:80])

        st, body = call("PUT", "/admin/employee/editPassword",
                        {"oldPassword": "wrongone", "newPassword": "abc123456", "affirmPassword": "abc123456"},
                        token)
        check("day02 原密码错误被拒", "原密码不正确" in body, body[:80])
    finally:
        # 无论上面哪一步失败，都要把 admin 密码改回 123456
        st, body = call("POST", "/admin/employee/login", {"username": "admin", "password": NEW_PWD})
        t2 = (data_of(body) or {}).get("token")
        if t2:
            call("PUT", "/admin/employee/editPassword",
                 {"oldPassword": NEW_PWD, "newPassword": "123456", "affirmPassword": "123456"}, t2)
        st, body = call("POST", "/admin/employee/login", {"username": "admin", "password": "123456"})
        check("day02 密码已还原为默认值", ok(body), body[:80])

    # 分类管理
    cat = {"type": 1, "name": CAT_NAME, "sort": 99}
    st, body = call("POST", "/admin/category", cat, token)
    check("day02 新增分类", st == 200 and ok(body), body)

    st, body = call("GET", "/admin/category/page?page=1&pageSize=10&name=%E8%87%AA%E6%B5%8B", None, token)
    page = data_of(body) or {}
    cat_row = next((r for r in page.get("records", []) if r["name"] == CAT_NAME), None)
    check("day02 分类分页查询", st == 200 and cat_row is not None, page)
    check("day02 新增分类默认禁用", (cat_row or {}).get("status") == 0, cat_row)
    cat_id = (cat_row or {}).get("id")

    st, _ = call("POST", "/admin/category/status/1?id=%s" % cat_id, None, token)
    st2, body2 = call("GET", "/admin/category/page?page=1&pageSize=10&name=%E8%87%AA%E6%B5%8B", None, token)
    check("day02 启用分类", ((data_of(body2) or {}).get("records") or [{}])[0].get("status") == 1, body2)

    st, body = call("PUT", "/admin/category", {"id": cat_id, "name": CAT_NAME + "B", "sort": 98}, token)
    st2, body2 = call("GET", "/admin/category/page?page=1&pageSize=10&name=%E8%87%AA%E6%B5%8B", None, token)
    check("day02 修改分类",
          ((data_of(body2) or {}).get("records") or [{}])[0].get("name") == CAT_NAME + "B", body2)

    st, body = call("GET", "/admin/category/list?type=1", None, token)
    lst = data_of(body) or []
    check("day02 按类型查询分类(仅启用)",
          st == 200 and all(x["status"] == 1 and x["type"] == 1 for x in lst), "count=%s" % len(lst))

    st, _ = call("DELETE", "/admin/category?id=%s" % cat_id, None, token)
    st2, body2 = call("GET", "/admin/category/page?page=1&pageSize=10&name=%E8%87%AA%E6%B5%8B", None, token)
    check("day02 删除分类", st == 200 and (data_of(body2) or {}).get("total") == 0, body2)

    # 删除「已被菜品关联的分类」应当被拦截：动态取一个挂着菜品的分类，不写死 id
    st, body = call("GET", "/admin/dish/page?page=1&pageSize=1", None, token)
    busy = ((data_of(body) or {}).get("records") or [{}])[0]
    busy_cat = busy.get("categoryId")
    if busy_cat:
        st, body = call("DELETE", "/admin/category?id=%s" % busy_cat, None, token)
        check("day02 删除已关联菜品的分类被拦截", "不能删除" in body, body)
    else:
        check("day02 删除已关联菜品的分类被拦截", False, "库中没有菜品数据，无法验证")

    # ---------------- 参数校验与分页兜底 ----------------
    st, body = call("POST", "/admin/employee",
                    {"name": "缺用户名", "phone": "13800001111", "sex": "1",
                     "idNumber": "110101199003077777"}, token)
    check("day02 参数校验-用户名必填", "用户名不能为空" in body, body)

    st, body = call("POST", "/admin/employee",
                    {"name": "手机号错", "username": "st_bad_" + RUN_ID, "phone": "123",
                     "sex": "1", "idNumber": "110101199003077777"}, token)
    check("day02 参数校验-手机号格式", "手机号格式不正确" in body, body)

    st, body = call("POST", "/admin/category", {"type": 1, "name": "", "sort": 1}, token)
    check("day02 参数校验-分类名必填", "分类名称不能为空" in body, body)

    st, body = call("GET", "/admin/employee/page?page=1&pageSize=99999", None, token)
    recs = (data_of(body) or {}).get("records") or []
    check("day02 分页条数上限不超过100", st == 200 and len(recs) <= 100, "records=%s" % len(recs))

    st, body = call("GET", "/admin/employee/page?page=-1&pageSize=0", None, token)
    check("day02 非法分页参数可兜底", st == 200 and (data_of(body) or {}).get("records") is not None, body[:80])


# ---------------------------------------------------------------- day03
# 1x1 透明 PNG，用于文件上传自测
PNG_1PX = ("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8"
           "z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")


def upload_raw(token, filename, content, content_type="image/png"):
    """multipart 上传指定字节内容，返回 (状态码, 响应文本)"""
    boundary = "----FastDineBoundary"
    body = b""
    body += ("--%s\r\n" % boundary).encode()
    body += ('Content-Disposition: form-data; name="file"; filename="%s"\r\n' % filename).encode()
    body += ("Content-Type: %s\r\n\r\n" % content_type).encode()
    body += content + b"\r\n"
    body += ("--%s--\r\n" % boundary).encode()
    req = urllib.request.Request(
        BASE + "/admin/common/upload", data=body, method="POST",
        headers={"Content-Type": "multipart/form-data; boundary=%s" % boundary, "token": token})
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "ignore")


def upload(token, filename="selftest.png"):
    """上传内置的 1x1 PNG"""
    return upload_raw(token, filename, base64.b64decode(PNG_1PX))


def run_day03():
    token = login()
    if not token:
        return

    # 1. 文件上传
    st, body = upload(token)
    url = data_of(body) if ok(body) else None
    check("day03 文件上传", st == 200 and bool(url), body)

    # 上传后的地址可直接访问
    # 上传接口返回的是带域名的绝对地址（小程序 image 不认相对路径），
    # 所以这里不能再无脑拼 BASE，兼容两种形态
    if url:
        target = url if str(url).startswith("http") else BASE + url
        try:
            code = urllib.request.urlopen(target).status
        except Exception as e:
            code = getattr(e, "code", "ERR")
        check("day03 上传后的图片可访问", code == 200, "%s -> %s" % (url, code))
        check("day03 上传地址是绝对地址", str(url).startswith("http"), str(url)[:60])

    # 非法扩展名被拒
    st, body = upload(token, "bad.txt")
    check("day03 非法扩展名被拒绝", "上传失败" in body or "仅支持" in body, body)

    # 扩展名合法但内容不是图片：靠文件头魔数拦住
    st, body = upload_raw(token, "fake.png", b"this is definitely not an image")
    check("day03 伪装成图片的文件被拒绝", "不是有效的图片" in body, body)

    # 内容是 PNG，却改名成 .jpg 上传：扩展名与内容不一致
    st, body = upload_raw(token, "mismatch.jpg", base64.b64decode(PNG_1PX))
    check("day03 扩展名与内容不一致被拒绝", "不一致" in body, body)

    # 2. 新增菜品（含两个口味）
    #    分类 id 不写死，取一个当前启用中的菜品分类（种子数据被清理后脚本仍可跑）
    st, body = call("GET", "/admin/category/list?type=1", None, token)
    cats = data_of(body) or []
    cat_id = cats[0]["id"] if cats else None
    check("day03 取到可用菜品分类", cat_id is not None, "count=%s" % len(cats))

    dish = {"name": DISH_NAME, "categoryId": cat_id, "price": 12.5, "image": url or "/upload/x.png",
            "description": "自测用", "status": 0,
            "flavors": [{"name": "辣度", "value": "[\"不辣\",\"微辣\"]"},
                        {"name": "温度", "value": "[\"常温\",\"热\"]"}]}
    st, body = call("POST", "/admin/dish", dish, token)
    check("day03 新增菜品", st == 200 and ok(body), body)

    # 3. 分页查询（按分类 + 名称）
    st, body = call("GET", "/admin/dish/page?page=1&pageSize=10&categoryId=" + str(cat_id)
                    + "&name=%E8%87%AA%E6%B5%8B", None, token)
    page = data_of(body) or {}
    row = None
    for r in page.get("records", []):
        if r.get("name") == DISH_NAME:
            row = r
    check("day03 菜品分页查询", st == 200 and row is not None, page.get("total"))
    check("day03 分页带出分类名称", bool(row and row.get("categoryName")), row.get("categoryName") if row else None)
    dish_id = row.get("id") if row else None

    # 4. 根据 id 查询（口味回显）
    st, body = call("GET", "/admin/dish/%s" % dish_id, None, token)
    d = data_of(body) or {}
    check("day03 根据id查询带口味", st == 200 and len(d.get("flavors") or []) == 2,
          "flavors=%s" % len(d.get("flavors") or []))

    # 5. 自动填充：dish 表创建时间/修改时间无默认值，非空即说明切面已填充
    check("day03 公共字段自动填充(时间)", d.get("updateTime") is not None, "updateTime=%s" % d.get("updateTime"))

    # 6. 修改菜品（价格、口味改为 1 条）
    upd = {"id": dish_id, "name": DISH_NAME + "B", "categoryId": cat_id, "price": 20.0, "image": url or "",
           "description": "改过", "status": 0,
           "flavors": [{"name": "辣度", "value": "[\"特辣\"]"}]}
    st, body = call("PUT", "/admin/dish", upd, token)
    st2, body2 = call("GET", "/admin/dish/%s" % dish_id, None, token)
    d2 = data_of(body2) or {}
    check("day03 修改菜品", st == 200 and d2.get("name") == DISH_NAME + "B"
          and float(d2.get("price") or 0) == 20.0, "%s %s" % (d2.get("name"), d2.get("price")))
    check("day03 修改后口味替换为1条", len(d2.get("flavors") or []) == 1,
          "flavors=%s" % len(d2.get("flavors") or []))

    # 7. 起售 / 起售中不许删
    st, _ = call("POST", "/admin/dish/status/1?id=%s" % dish_id, None, token)
    st2, body2 = call("GET", "/admin/dish/%s" % dish_id, None, token)
    check("day03 菜品起售", (data_of(body2) or {}).get("status") == 1, body2)

    # 7.1 管理端菜品列表（套餐管理里「选择菜品」用）：只返回起售中的
    st, body = call("GET", "/admin/dish/list?categoryId=%s" % cat_id, None, token)
    lst = data_of(body) or []
    check("day03 菜品列表只返回起售中",
          st == 200 and any(x.get("name") == DISH_NAME + "B" for x in lst)
          and all(x.get("status") == 1 for x in lst),
          "count=%s" % len(lst))

    st, body = call("DELETE", "/admin/dish?ids=%s" % dish_id, None, token)
    check("day03 起售中不能删除", "起售中的菜品不能删除" in body, body)

    # 8. 停售后再删除
    call("POST", "/admin/dish/status/0?id=%s" % dish_id, None, token)
    st, body = call("DELETE", "/admin/dish?ids=%s" % dish_id, None, token)
    st2, body2 = call("GET", "/admin/dish/%s" % dish_id, None, token)
    check("day03 停售后可删除", st == 200 and (data_of(body2) is None or data_of(body2).get("id") is None),
          body)

    # 9. 自动填充回归：员工新增仍需 createUser
    emp = {"name": "自测李四", "username": "st03_" + RUN_ID, "phone": "13700003333",
           "sex": "1", "idNumber": "110101199003078888"}
    call("POST", "/admin/employee", emp, token)
    st, body = call("GET", "/admin/employee/page?page=1&pageSize=10&name=%E8%87%AA%E6%B5%8B%E6%9D%8E%E5%9B%9B", None, token)
    rec = (data_of(body) or {}).get("records") or []
    check("day03 员工创建人自动填充", bool(rec) and rec[0].get("createUser") == 1,
          rec[0].get("createUser") if rec else "无记录")

    # 10. 查询不存在的菜品：应返回业务成功 + data 为空，不能抛 500
    st, body = call("GET", "/admin/dish/99999999", None, token)
    check("day03 查询不存在的菜品不报500", st == 200 and data_of(body) is None,
          "HTTP %s %s" % (st, body[:80]))


# ---------------------------------------------------------------- day04
def run_day04():
    """管理端套餐管理（day04 项目实战）"""
    token = login()
    if not token:
        return

    # 取一个套餐分类（type=2）
    st, body = call("GET", "/admin/category/list?type=2", None, token)
    cats = data_of(body) or []
    setmeal_cat = cats[0]["id"] if cats else None
    check("day04 取到套餐分类", setmeal_cat is not None, "count=%s" % len(cats))

    # 取两个起售中的菜品用于组装套餐
    st, body = call("GET", "/admin/dish/page?page=1&pageSize=2&status=1", None, token)
    dishes = (data_of(body) or {}).get("records") or []
    check("day04 取到起售菜品", len(dishes) >= 2, "count=%s" % len(dishes))
    d1 = dishes[0]
    d2 = dishes[1]

    name = "自测套餐" + RUN_ID
    setmeal = {
        "categoryId": setmeal_cat, "name": name, "price": 88.8,
        "image": "/upload/selftest.png", "description": "自测用", "status": 0,
        "setmealDishes": [
            {"dishId": d1["id"], "name": d1["name"], "price": d1["price"], "copies": 1},
            {"dishId": d2["id"], "name": d2["name"], "price": d2["price"], "copies": 2},
        ],
    }
    st, body = call("POST", "/admin/setmeal", setmeal, token)
    check("day04 新增套餐", st == 200 and ok(body), body)

    # 分页查询
    st, body = call("GET", "/admin/setmeal/page?page=1&pageSize=10&name=%E8%87%AA%E6%B5%8B", None, token)
    page = data_of(body) or {}
    row = next((r for r in page.get("records", []) if r["name"] == name), None)
    check("day04 套餐分页查询", row is not None, page.get("total"))
    check("day04 分页带出分类名称", bool(row and row.get("categoryName")),
          row.get("categoryName") if row else None)
    sid = row.get("id") if row else None

    # 根据id查询（带关联菜品）
    st, body = call("GET", "/admin/setmeal/%s" % sid, None, token)
    d = data_of(body) or {}
    check("day04 根据id查询带关联菜品", len(d.get("setmealDishes") or []) == 2,
          "dishes=%s" % len(d.get("setmealDishes") or []))

    # 修改：改名称价格 + 关联菜品换成 1 条
    upd = {"id": sid, "categoryId": setmeal_cat, "name": name + "B", "price": 99.9,
           "image": "/upload/selftest.png", "description": "改过", "status": 0,
           "setmealDishes": [{"dishId": d1["id"], "name": d1["name"], "price": d1["price"], "copies": 3}]}
    st, body = call("PUT", "/admin/setmeal", upd, token)
    st2, body2 = call("GET", "/admin/setmeal/%s" % sid, None, token)
    dv = data_of(body2) or {}
    check("day04 修改套餐", st == 200 and dv.get("name") == name + "B"
          and float(dv.get("price") or 0) == 99.9, "%s %s" % (dv.get("name"), dv.get("price")))
    check("day04 修改后关联菜品替换为1条", len(dv.get("setmealDishes") or []) == 1,
          "dishes=%s" % len(dv.get("setmealDishes") or []))

    # 起售
    st, _ = call("POST", "/admin/setmeal/status/1?id=%s" % sid, None, token)
    st2, body2 = call("GET", "/admin/setmeal/%s" % sid, None, token)
    check("day04 套餐起售", (data_of(body2) or {}).get("status") == 1, body2[:80])

    # 起售中不能删除
    st, body = call("DELETE", "/admin/setmeal?ids=%s" % sid, None, token)
    check("day04 起售中不能删除套餐", "起售中的套餐不能删除" in body, body)

    # C 端联动：刚起售的套餐应当出现在小程序端
    ut = user_token()
    if ut:
        st, body = ucall("GET", "/user/setmeal/list?categoryId=" + str(setmeal_cat), token=ut)
        names = [x.get("name") for x in (data_of(body) or [])]
        check("day04 起售套餐出现在C端", name + "B" in names, names[:5])
        st, body = ucall("GET", "/user/setmeal/dish/%s" % sid, token=ut)
        check("day04 C端套餐菜品列表有数据", len(data_of(body) or []) == 1,
              "count=%s" % len(data_of(body) or []))
    else:
        check("day04 起售套餐出现在C端", False, "拿不到 C 端令牌")

    # 停售后可删除
    call("POST", "/admin/setmeal/status/0?id=%s" % sid, None, token)
    st, body = call("DELETE", "/admin/setmeal?ids=%s" % sid, None, token)
    st2, body2 = call("GET", "/admin/setmeal/%s" % sid, None, token)
    check("day04 停售后可删除套餐", st == 200 and data_of(body2) is None,
          "HTTP %s / %s" % (st, body2[:60]))

    # 套餐内含停售菜品时不允许起售
    call("POST", "/admin/dish/status/0?id=%s" % d1["id"], None, token)
    bad = {
        "categoryId": setmeal_cat, "name": name + "含停售", "price": 66.6,
        "image": "/upload/selftest.png", "description": "", "status": 0,
        "setmealDishes": [{"dishId": d1["id"], "name": d1["name"], "price": d1["price"], "copies": 1}],
    }
    call("POST", "/admin/setmeal", bad, token)
    st, body = call("GET", "/admin/setmeal/page?page=1&pageSize=10&name=%E8%87%AA%E6%B5%8B", None, token)
    bad_row = next((r for r in (data_of(body) or {}).get("records", [])
                    if r["name"] == name + "含停售"), None)
    bad_id = (bad_row or {}).get("id")
    st, body = call("POST", "/admin/setmeal/status/1?id=%s" % bad_id, None, token)
    check("day04 套餐内含停售菜品不能起售", "未启售菜品" in body, body[:80])
    # 收尾：菜品恢复起售，删除该套餐
    call("POST", "/admin/dish/status/1?id=%s" % d1["id"], None, token)
    call("DELETE", "/admin/setmeal?ids=%s" % bad_id, None, token)

    # 参数校验
    st, body = call("POST", "/admin/setmeal", {"categoryId": setmeal_cat, "price": 10, "image": "x"}, token)
    check("day04 参数校验-套餐名必填", "套餐名称不能为空" in body, body[:80])


# ---------------------------------------------------------------- day05
def run_day05():
    """店铺营业状态（存 Redis）"""
    token = login()
    if not token:
        return

    st, body = call("PUT", "/admin/shop/1", None, token)
    check("day05 设置营业中", st == 200 and ok(body), body[:80])

    st, body = call("GET", "/admin/shop/status", None, token)
    check("day05 管理端查询营业状态", st == 200 and data_of(body) == 1, body[:80])

    ut = user_token()
    st, body = ucall("GET", "/user/shop/status", token=ut)
    check("day05 C端查询营业状态", st == 200 and data_of(body) == 1, body[:80])

    # 小程序「联系商家」用，号码来自 fastdine.shop.phone
    st, body = ucall("GET", "/user/shop/phone", token=ut)
    check("day05 C端查询商家电话", st == 200 and isinstance(data_of(body), str) and data_of(body), body[:80])

    st, body = call("PUT", "/admin/shop/0", None, token)
    st2, body2 = call("GET", "/admin/shop/status", None, token)
    check("day05 切换为打烊", st == 200 and data_of(body2) == 0, body2[:80])

    # C 端只能查，不能改
    st, _ = ucall("PUT", "/user/shop/1", None, ut)
    check("day05 C端不能修改营业状态", st in (404, 405), "HTTP %s" % st)

    # 恢复营业，避免影响之后手动调试小程序
    call("PUT", "/admin/shop/1", None, token)


# ---------------------------------------------------------------- day06
def run_day06():
    """C 端（微信小程序）接口：微信登录 + 商品浏览"""
    admin_token = login()
    if not admin_token:
        return

    # 1. 未登录访问被拦截
    st, _ = call("GET", "/user/category/list?type=1")
    check("day06 未带令牌访问C端接口返回401", st == 401, "HTTP %s" % st)

    # 2. 微信登录（本地未配置 AppID，自动降级为 mock）
    st, body = call("POST", "/user/user/login", {"code": "selftest-code"})
    d = data_of(body) or {}
    token = d.get("token")
    check("day06 微信登录(本地降级mock)", st == 200 and bool(token), body[:120])
    check("day06 登录返回openid", bool(d.get("openid")), d.get("openid"))

    # 3. 同一 openid 再次登录应命中同一用户，不重复注册
    st, body = call("POST", "/user/user/login", {"code": "another-code"})
    d2 = data_of(body) or {}
    check("day06 重复登录不重复建用户", d2.get("id") == d.get("id"),
          "%s vs %s" % (d2.get("id"), d.get("id")))

    # 4. code 必填
    st, body = call("POST", "/user/user/login", {})
    check("day06 登录参数校验-code必填", "登录凭证不能为空" in body, body[:80])

    # 5. 分类列表（只返回启用中的）
    st, body = ucall("GET", "/user/category/list?type=1", token=token)
    cats = data_of(body) or []
    check("day06 C端分类列表", st == 200 and len(cats) > 0, "count=%s" % len(cats))
    check("day06 C端只返回启用分类",
          bool(cats) and all(c.get("status") == 1 for c in cats),
          [c.get("status") for c in cats][:8])

    # 6. 菜品列表：先取一个正挂着起售菜品的分类
    st, body = call("GET", "/admin/dish/page?page=1&pageSize=1&status=1", None, admin_token)
    busy = ((data_of(body) or {}).get("records") or [{}])[0]
    cat_id, dish_id = busy.get("categoryId"), busy.get("id")
    check("day06 取到起售菜品用于验证", bool(cat_id and dish_id),
          "categoryId=%s dishId=%s" % (cat_id, dish_id))

    st, body = ucall("GET", "/user/dish/list?categoryId=" + str(cat_id), token=token)
    dishes = data_of(body) or []
    check("day06 C端菜品列表", st == 200 and len(dishes) > 0, "count=%s" % len(dishes))
    check("day06 C端菜品含口味字段", bool(dishes) and "flavors" in dishes[0],
          dishes[0].get("flavors") if dishes else None)
    check("day06 C端只返回起售菜品",
          bool(dishes) and all(x.get("status") == 1 for x in dishes),
          [x.get("status") for x in dishes][:8])

    # 7. 停售的菜品不出现在 C 端，起售后重新出现
    call("POST", "/admin/dish/status/0?id=%s" % dish_id, None, admin_token)
    st, body = ucall("GET", "/user/dish/list?categoryId=" + str(cat_id), token=token)
    ids = [x.get("id") for x in (data_of(body) or [])]
    check("day06 停售菜品不返回C端", st == 200 and dish_id not in ids,
          "dishId=%s ids=%s" % (dish_id, ids[:6]))

    call("POST", "/admin/dish/status/1?id=%s" % dish_id, None, admin_token)
    st, body = ucall("GET", "/user/dish/list?categoryId=" + str(cat_id), token=token)
    ids = [x.get("id") for x in (data_of(body) or [])]
    check("day06 起售后重新出现在C端", dish_id in ids, "dishId=%s ids=%s" % (dish_id, ids[:6]))

    # 8. 套餐接口（库里暂无套餐数据，验证接口可用且返回列表结构）
    st, body = ucall("GET", "/user/setmeal/list?categoryId=13", token=token)
    check("day06 C端套餐列表可用", st == 200 and isinstance(data_of(body), list), body[:80])
    st, body = ucall("GET", "/user/setmeal/dish/1", token=token)
    check("day06 C端套餐菜品列表可用", st == 200 and isinstance(data_of(body), list), body[:80])

    # 9. 两端令牌互不通用（用户端 token 拿去访问管理端应当 401）
    st, _ = call("GET", "/admin/employee/page?page=1&pageSize=10", None, token)
    check("day06 用户端令牌不能访问管理端", st == 401, "HTTP %s" % st)

    # 10. 退出
    st, body = ucall("POST", "/user/user/logout", token=token)
    check("day06 退出登录", st == 200 and ok(body), body[:80])


# ---------------------------------------------------------------- day07
def run_day07():
    """day07 缓存商品（Spring Cache + Redis）+ C 端购物车"""
    token = login()
    if not token:
        return
    ut = user_token()
    if not ut:
        check("day07 拿不到C端令牌", False, "")
        return

    # ---------------- 缓存 ----------------
    # 取一个挂着起售菜品的分类
    st, body = call("GET", "/admin/dish/page?page=1&pageSize=1&status=1", None, token)
    busy = ((data_of(body) or {}).get("records") or [{}])[0]
    cat_id, dish_id = busy.get("categoryId"), busy.get("id")

    # 第一次查询写入缓存，第二次应命中缓存且数据一致
    st, body = ucall("GET", "/user/dish/list?categoryId=" + str(cat_id), token=ut)
    first = [x.get("id") for x in (data_of(body) or [])]
    st, body = ucall("GET", "/user/dish/list?categoryId=" + str(cat_id), token=ut)
    second = [x.get("id") for x in (data_of(body) or [])]
    check("day07 菜品缓存查询结果一致", first == second, "%s vs %s" % (first[:4], second[:4]))

    # 小程序首页调 getCategoryList() 是**不带参数**的：@Cacheable 的 key 求值为 null 时
    # Spring 会抛 "Null key returned for cache operation"，整个接口 500，
    # 表现就是小程序首页店铺卡片正常、分类和菜品全空。这三条专门守住这个坑。
    st, body = ucall("GET", "/user/category/list", token=ut)
    all_cats = data_of(body) or []
    check("day07 C端分类不带type不报错", st == 200 and ok(body) and len(all_cats) > 0,
          "HTTP %s len=%s" % (st, len(all_cats)))

    st, body = ucall("GET", "/user/dish/list", token=ut)
    check("day07 C端菜品不带分类不报错", st == 200 and ok(body), "HTTP %s 需200" % st)

    st, body = ucall("GET", "/user/setmeal/list", token=ut)
    check("day07 C端套餐不带分类不报错", st == 200 and ok(body), "HTTP %s 需200" % st)

    # 管理端停售会触发 @CacheEvict，如果缓存没清，C 端还会看到旧数据
    call("POST", "/admin/dish/status/0?id=%s" % dish_id, None, token)
    st, body = ucall("GET", "/user/dish/list?categoryId=" + str(cat_id), token=ut)
    after = [x.get("id") for x in (data_of(body) or [])]
    check("day07 管理端改菜品后C端缓存被清理", dish_id not in after,
          "dishId=%s after=%s" % (dish_id, after[:4]))
    call("POST", "/admin/dish/status/1?id=%s" % dish_id, None, token)

    # ---------------- 购物车 ----------------
    # 干净的起点
    ucall("DELETE", "/user/shoppingCart/clean", token=ut)

    st, _ = call("GET", "/user/shoppingCart/list")
    check("day07 未登录查看购物车返回401", st == 401, "HTTP %s" % st)

    st, body = ucall("POST", "/user/shoppingCart/add", {"dishId": dish_id, "dishFlavor": "微辣"}, token=ut)
    check("day07 添加购物车(菜品)", st == 200 and ok(body), body)

    st, body = ucall("GET", "/user/shoppingCart/list", token=ut)
    carts = data_of(body) or []
    check("day07 购物车列表", len(carts) == 1, "count=%s" % len(carts))
    check("day07 购物车带出名称与金额",
          bool(carts) and bool(carts[0].get("name")) and bool(carts[0].get("amount")),
          carts[0] if carts else None)

    # 同菜品同口味再加一次 → 数量累加，不新增行
    ucall("POST", "/user/shoppingCart/add", {"dishId": dish_id, "dishFlavor": "微辣"}, token=ut)
    st, body = ucall("GET", "/user/shoppingCart/list", token=ut)
    carts = data_of(body) or []
    check("day07 同菜品同口味数量累加",
          len(carts) == 1 and carts[0].get("number") == 2,
          [(c.get("dishFlavor"), c.get("number")) for c in carts])

    # 同菜品不同口味 → 独立成行
    ucall("POST", "/user/shoppingCart/add", {"dishId": dish_id, "dishFlavor": "特辣"}, token=ut)
    st, body = ucall("GET", "/user/shoppingCart/list", token=ut)
    carts = data_of(body) or []
    check("day07 同菜品不同口味独立成行", len(carts) == 2,
          [(c.get("dishFlavor"), c.get("number")) for c in carts])

    # 减一
    ucall("POST", "/user/shoppingCart/sub", {"dishId": dish_id, "dishFlavor": "微辣"}, token=ut)
    st, body = ucall("GET", "/user/shoppingCart/list", token=ut)
    nums = sorted([c.get("number") for c in (data_of(body) or [])])
    check("day07 减一后数量正确", nums == [1, 1], nums)

    # 数量为 1 时再减 → 删除该行
    ucall("POST", "/user/shoppingCart/sub", {"dishId": dish_id, "dishFlavor": "微辣"}, token=ut)
    st, body = ucall("GET", "/user/shoppingCart/list", token=ut)
    carts = data_of(body) or []
    check("day07 数量为1时再减删除该行",
          len(carts) == 1 and carts[0].get("dishFlavor") == "特辣",
          [(c.get("dishFlavor"), c.get("number")) for c in carts])

    # 套餐分支：临时建一个套餐，测完删掉
    st, body = call("GET", "/admin/category/list?type=2", None, token)
    setmeal_cat = (data_of(body) or [{}])[0].get("id")
    st, body = call("GET", "/admin/dish/page?page=1&pageSize=1&status=1", None, token)
    d = ((data_of(body) or {}).get("records") or [{}])[0]
    tmp_name = "购物车测试" + RUN_ID
    call("POST", "/admin/setmeal", {
        "categoryId": setmeal_cat, "name": tmp_name, "price": 55.5,
        "image": "/upload/selftest.png", "description": "", "status": 0,
        "setmealDishes": [{"dishId": d["id"], "name": d["name"], "price": d["price"], "copies": 1}],
    }, token)
    st, body = call("GET", "/admin/setmeal/page?page=1&pageSize=100", None, token)
    row = next((r for r in (data_of(body) or {}).get("records", []) if r["name"] == tmp_name), None)
    tmp_sid = (row or {}).get("id")

    st, body = ucall("POST", "/user/shoppingCart/add", {"setmealId": tmp_sid}, token=ut)
    check("day07 添加购物车(套餐)", st == 200 and ok(body), body)
    st, body = ucall("GET", "/user/shoppingCart/list", token=ut)
    check("day07 套餐进入购物车",
          any(c.get("setmealId") == tmp_sid for c in (data_of(body) or [])), body[:80])

    # 参数校验
    st, body = ucall("POST", "/user/shoppingCart/add", {}, token=ut)
    check("day07 菜品套餐都没传时报错", "不能同时为空" in body, body[:80])

    st, body = ucall("POST", "/user/shoppingCart/add", {"dishId": 99999999}, token=ut)
    check("day07 添加不存在的菜品报错", "菜品不存在" in body, body[:80])

    # 清空
    st, body = ucall("DELETE", "/user/shoppingCart/clean", token=ut)
    st2, body2 = ucall("GET", "/user/shoppingCart/list", token=ut)
    check("day07 清空购物车", st == 200 and len(data_of(body2) or []) == 0, body2[:60])

    # 收尾：删掉临时套餐
    if tmp_sid:
        call("POST", "/admin/setmeal/status/0?id=%s" % tmp_sid, None, token)
        call("DELETE", "/admin/setmeal?ids=%s" % tmp_sid, None, token)


# ---------------------------------------------------------------- day08
def run_day08():
    """day08 地址簿 + 用户下单 + 订单支付（本地模拟）+ 订单查询/取消/再来一单"""
    token = login()
    if not token:
        return
    ut = user_token()
    if not ut:
        check("day08 拿不到C端令牌", False, "")
        return

    # ---------------- 地址簿 ----------------
    st, _ = call("GET", "/user/addressBook/list")
    check("day08 未登录访问地址簿返回401", st == 401, "HTTP %s" % st)

    addr1 = {
        "consignee": "自测收货人" + RUN_ID,
        "phone": "13800001111",
        "sex": "1",
        "provinceName": "广东省", "cityName": "深圳市", "districtName": "南山区",
        "detail": "科技园路 1 号 " + RUN_ID,
        "label": "公司",
        "isDefault": 0,
    }
    st, body = ucall("POST", "/user/addressBook", addr1, token=ut)
    check("day08 新增地址", st == 200 and ok(body), body[:80])

    st, body = ucall("GET", "/user/addressBook/list", token=ut)
    addr_list = data_of(body) or []
    mine = [a for a in addr_list if a.get("consignee") == addr1["consignee"]]
    addr1_id = (mine[0] if mine else {}).get("id")
    check("day08 地址列表带出新增地址",
          len(mine) == 1 and mine[0].get("phone") == "13800001111", "count=%s" % len(addr_list))

    # 手机号格式 + 必填校验
    bad = dict(addr1, phone="123")
    st, body = ucall("POST", "/user/addressBook", bad, token=ut)
    check("day08 手机号格式校验", "手机号格式不正确" in body, body[:80])
    bad2 = dict(addr1, detail="")
    st, body = ucall("POST", "/user/addressBook", bad2, token=ut)
    check("day08 地址必填校验", "不能为空" in body, body[:80])

    # 第二个地址 + 设默认
    addr2 = dict(addr1, consignee="自测默认" + RUN_ID, phone="13900002222", detail="深南大道 2 号", isDefault=1)
    ucall("POST", "/user/addressBook", addr2, token=ut)
    st, body = ucall("GET", "/user/addressBook/list", token=ut)
    addr2_row = next((a for a in (data_of(body) or []) if a.get("consignee") == addr2["consignee"]), None)
    addr2_id = (addr2_row or {}).get("id")
    st, body = ucall("GET", "/user/addressBook/default", token=ut)
    check("day08 新增时标记默认生效", (data_of(body) or {}).get("id") == addr2_id, body[:100])

    # 把第一个地址设为默认，默认地址应跟着换，且全局只有一个默认
    st, body = ucall("PUT", "/user/addressBook/default", {"id": addr1_id}, token=ut)
    check("day08 设置默认地址", st == 200 and ok(body), body[:80])
    st, body = ucall("GET", "/user/addressBook/default", token=ut)
    check("day08 默认地址已切换", (data_of(body) or {}).get("id") == addr1_id, body[:100])
    st, body = ucall("GET", "/user/addressBook/list", token=ut)
    defaults = [a for a in (data_of(body) or []) if a.get("isDefault") == 1]
    check("day08 默认地址只有一个", len(defaults) == 1, [a.get("id") for a in defaults])

    # 详情 + 修改
    st, body = ucall("GET", "/user/addressBook/" + str(addr1_id), token=ut)
    check("day08 根据id查询地址", (data_of(body) or {}).get("id") == addr1_id, body[:80])
    st, body = ucall("PUT", "/user/addressBook", {"id": addr1_id, "consignee": "自测改名" + RUN_ID}, token=ut)
    check("day08 修改地址", st == 200 and ok(body), body[:80])
    st, body = ucall("GET", "/user/addressBook/" + str(addr1_id), token=ut)
    check("day08 修改后回读一致", (data_of(body) or {}).get("consignee") == "自测改名" + RUN_ID, body[:80])
    st, body = ucall("PUT", "/user/addressBook", {"id": addr1_id, "phone": "999"}, token=ut)
    check("day08 修改时手机号格式校验", "手机号格式不正确" in body, body[:80])

    # 不存在的地址
    st, body = ucall("GET", "/user/addressBook/99999999", token=ut)
    check("day08 查询不存在地址报错", "地址不存在" in body, body[:80])

    # ---------------- 下单 ----------------
    ucall("DELETE", "/user/shoppingCart/clean", token=ut)
    st, body = ucall("POST", "/user/order/submit", {"addressBookId": addr1_id, "payMethod": 1}, token=ut)
    check("day08 购物车为空不能下单", "购物车数据为空" in body, body[:80])

    st, body = call("GET", "/admin/dish/page?page=1&pageSize=1&status=1", None, token)
    dish = ((data_of(body) or {}).get("records") or [{}])[0]
    dish_id = dish.get("id")
    ucall("POST", "/user/shoppingCart/add", {"dishId": dish_id, "dishFlavor": "微辣"}, token=ut)
    ucall("POST", "/user/shoppingCart/add", {"dishId": dish_id, "dishFlavor": "微辣"}, token=ut)
    ucall("POST", "/user/shoppingCart/add", {"dishId": dish_id, "dishFlavor": "特辣"}, token=ut)

    st, body = ucall("GET", "/user/shoppingCart/list", token=ut)
    carts = data_of(body) or []
    expected = sum(float(c.get("amount") or 0) * int(c.get("number") or 0) for c in carts) + 2
    expected = round(expected, 2)

    st, body = ucall("POST", "/user/order/submit", {
        "addressBookId": addr1_id, "payMethod": 1, "remark": "selftest-" + RUN_ID,
        "packAmount": 2, "deliveryStatus": 1, "tablewareNumber": 1, "tablewareStatus": 0,
        "amount": 0.01,
    }, token=ut)
    submit = data_of(body) or {}
    order_id, order_no = submit.get("id"), submit.get("orderNumber")
    check("day08 下单成功", st == 200 and ok(body) and bool(order_no), body[:120])
    # 前端故意传 amount=0.01，服务端必须按购物车自己算
    check("day08 金额以服务端计算为准", float(submit.get("orderAmount") or 0) == expected,
          "server=%s expect=%s" % (submit.get("orderAmount"), expected))

    st, body = ucall("GET", "/user/shoppingCart/list", token=ut)
    check("day08 下单后购物车被清空", len(data_of(body) or []) == 0, body[:60])

    # ---------------- 订单查询 ----------------
    st, body = ucall("GET", "/user/order/orderDetail/" + str(order_id), token=ut)
    detail = data_of(body) or {}
    check("day08 订单详情带出明细",
          len(detail.get("orderDetailList") or []) == 2, "明细 %s 条" % len(detail.get("orderDetailList") or []))
    check("day08 订单带出地址簿收货信息",
          detail.get("consignee") == "自测改名" + RUN_ID and detail.get("phone") == "13800001111",
          "%s / %s" % (detail.get("consignee"), detail.get("phone")))
    check("day08 订单初始为待付款", detail.get("status") == 1 and detail.get("payStatus") == 0,
          "status=%s payStatus=%s" % (detail.get("status"), detail.get("payStatus")))

    st, body = ucall("GET", "/user/order/historyPage?page=1&pageSize=10", token=ut)
    records = (data_of(body) or {}).get("records") or []
    check("day08 历史订单包含新订单", any(r.get("id") == order_id for r in records),
          "共 %s 条" % len(records))

    st, body = ucall("GET", "/user/order/orderDetail/99999999", token=ut)
    check("day08 查询不存在订单报错", "订单不存在" in body, body[:80])

    # ---------------- 支付（本地模拟） ----------------
    st, body = ucall("PUT", "/user/order/payment", {"orderNumber": order_no, "payMethod": 1}, token=ut)
    pay = data_of(body) or {}
    check("day08 模拟支付返回调起参数",
          st == 200 and ok(body) and "mock" in str(pay.get("packageStr")), body[:120])

    # 支付回调（微信服务器无 token 直接 POST）
    # 注意：本用例依赖 fastdine.wechat.pay-enabled=false —— 此时回调不做解密、
    # 直接按微信要求回 SUCCESS。开启真实支付后这里会走解密，需另配真实报文。
    st, body = call("POST", "/notify/paySuccess",
                    {"resource": {"ciphertext": "x", "nonce": "y", "associated_data": "z"}})
    check("day08 支付回调免登录且应答SUCCESS",
          st == 200 and "SUCCESS" in body, "HTTP %s %s" % (st, body[:60]))

    st, body = ucall("GET", "/user/order/orderDetail/" + str(order_id), token=ut)
    detail = data_of(body) or {}
    check("day08 支付后转为待接单已支付",
          detail.get("status") == 2 and detail.get("payStatus") == 1,
          "status=%s payStatus=%s" % (detail.get("status"), detail.get("payStatus")))

    st, body = ucall("PUT", "/user/order/payment", {"orderNumber": order_no, "payMethod": 1}, token=ut)
    check("day08 重复支付被拦截", "该订单已支付" in body, body[:80])

    # ---------------- 取消 + 再来一单 ----------------
    st, body = ucall("PUT", "/user/order/cancel/" + str(order_id), token=ut)
    check("day08 取消订单", st == 200 and ok(body), body[:80])
    st, body = ucall("GET", "/user/order/orderDetail/" + str(order_id), token=ut)
    detail = data_of(body) or {}
    check("day08 取消后状态为已取消且标记退款",
          detail.get("status") == 6 and detail.get("payStatus") == 2,
          "status=%s payStatus=%s" % (detail.get("status"), detail.get("payStatus")))

    st, body = ucall("PUT", "/user/order/cancel/" + str(order_id), token=ut)
    check("day08 已取消订单不能再取消", "订单状态错误" in body, body[:80])

    st, body = ucall("GET", "/user/order/historyPage?page=1&pageSize=10&status=6", token=ut)
    records = (data_of(body) or {}).get("records") or []
    check("day08 按状态筛选历史订单",
          bool(records) and all(r.get("status") == 6 for r in records), "共 %s 条" % len(records))

    ucall("DELETE", "/user/shoppingCart/clean", token=ut)
    st, body = ucall("POST", "/user/order/repetition/" + str(order_id), token=ut)
    check("day08 再来一单", st == 200 and ok(body), body[:80])
    st, body = ucall("GET", "/user/shoppingCart/list", token=ut)
    check("day08 再来一单把明细写回购物车", len(data_of(body) or []) == 2,
          "购物车 %s 行" % len(data_of(body) or []))

    # 收尾：清空购物车 + 删掉自测地址
    ucall("DELETE", "/user/shoppingCart/clean", token=ut)
    ucall("DELETE", "/user/addressBook?id=" + str(addr1_id), token=ut)
    if addr2_id:
        ucall("DELETE", "/user/addressBook?id=" + str(addr2_id), token=ut)
    st, body = ucall("GET", "/user/addressBook/list", token=ut)
    left = [a for a in (data_of(body) or []) if a.get("consignee") in
            ("自测改名" + RUN_ID, "自测默认" + RUN_ID, "自测收货人" + RUN_ID)]
    check("day08 删除地址后列表不再包含", len(left) == 0, "残留 %s" % len(left))


# ---------------------------------------------------------------- day09
def run_day09():
    """day09 管理端订单管理：条件搜索 / 状态统计 / 接单 / 拒单 / 派送 / 完成 / 取消"""
    token = login()
    if not token:
        return
    ut = user_token()
    if not ut:
        check("day09 拿不到C端令牌", False, "")
        return

    st, _ = call("GET", "/admin/order/conditionSearch?page=1&pageSize=10")
    check("day09 未登录访问管理端订单返回401", st == 401, "HTTP %s" % st)

    # 准备一个地址 + 一个起售菜品
    addr = {
        "consignee": "订单测试" + RUN_ID, "phone": "13700003333", "sex": "1",
        "provinceName": "广东省", "cityName": "深圳市", "districtName": "福田区",
        "detail": "车公庙 3 号", "label": "家", "isDefault": 1,
    }
    ucall("POST", "/user/addressBook", addr, token=ut)
    st, body = ucall("GET", "/user/addressBook/list", token=ut)
    addr_id = next((a.get("id") for a in (data_of(body) or []) if a.get("consignee") == addr["consignee"]), None)

    st, body = call("GET", "/admin/dish/page?page=1&pageSize=1&status=1", None, token)
    dish = ((data_of(body) or {}).get("records") or [{}])[0]
    dish_id, dish_name = dish.get("id"), dish.get("name")

    def make_order(paid=True):
        """下一单（可选支付），返回订单 id"""
        ucall("DELETE", "/user/shoppingCart/clean", token=ut)
        ucall("POST", "/user/shoppingCart/add", {"dishId": dish_id, "dishFlavor": "微辣"}, token=ut)
        st, body = ucall("POST", "/user/order/submit",
                         {"addressBookId": addr_id, "payMethod": 1, "remark": "selftest-" + RUN_ID}, token=ut)
        o = data_of(body) or {}
        if paid:
            ucall("PUT", "/user/order/payment", {"orderNumber": o.get("orderNumber"), "payMethod": 1}, token=ut)
        return o.get("id"), o.get("orderNumber")

    def admin_detail(oid):
        st, body = call("GET", "/admin/order/details/" + str(oid), None, token)
        return st, (data_of(body) or {})

    # ---------------- 条件搜索 + 统计 ----------------
    order1, order1_no = make_order()
    st, body = admin_detail(order1)
    check("day09 支付后订单进入待接单", (body or {}).get("status") == 2, str(body)[:90])

    st, body = call("GET", "/admin/order/conditionSearch?page=1&pageSize=10&status=2", None, token)
    records = (data_of(body) or {}).get("records") or []
    row = next((r for r in records if r.get("id") == order1), None)
    check("day09 条件搜索能查到待接单订单", row is not None, "共 %s 条" % len(records))
    check("day09 订单列表带出菜品摘要",
          bool(row) and (dish_name + "*1;") in (row.get("orderDishes") or ""),
          (row or {}).get("orderDishes"))

    st, body = call("GET", "/admin/order/conditionSearch?page=1&pageSize=10&phone=13700003333", None, token)
    records = (data_of(body) or {}).get("records") or []
    check("day09 按手机号搜索", any(r.get("id") == order1 for r in records), "共 %s 条" % len(records))

    st, body = call("GET", "/admin/order/statistics", None, token)
    stat = data_of(body) or {}
    check("day09 状态统计-待接单至少1",
          (stat.get("toBeConfirmed") or 0) >= 1 and stat.get("confirmed") is not None,
          stat)

    # ---------------- 接单 -> 派送 -> 完成 ----------------
    st, body = call("PUT", "/admin/order/confirm", {"id": order1}, token)
    check("day09 接单", st == 200 and ok(body), body[:80])
    st, body = admin_detail(order1)
    check("day09 接单后状态为已接单", (body or {}).get("status") == 3, str(body)[:90])

    st, body = call("PUT", "/admin/order/confirm", {"id": order1}, token)
    check("day09 重复接单被拦截", "订单状态错误" in body, body[:80])

    st, body = call("PUT", "/admin/order/delivery/" + str(order1), None, token)
    check("day09 派送", st == 200 and ok(body), body[:80])
    st, body = admin_detail(order1)
    check("day09 派送后状态为派送中", (body or {}).get("status") == 4, str(body)[:90])

    st, body = call("PUT", "/admin/order/complete/" + str(order1), None, token)
    check("day09 完成", st == 200 and ok(body), body[:80])
    st, body = admin_detail(order1)
    detail1 = body or {}
    check("day09 完成后状态为已完成且写入送达时间",
          detail1.get("status") == 5 and bool(detail1.get("deliveryTime")),
          "status=%s deliveryTime=%s" % (detail1.get("status"), detail1.get("deliveryTime")))

    st, body = call("PUT", "/admin/order/delivery/" + str(order1), None, token)
    check("day09 已完成订单不能再派送", "订单状态错误" in body, body[:80])
    st, body = call("PUT", "/admin/order/cancel", {"id": order1, "cancelReason": "测试"}, token)
    check("day09 已完成订单不能取消", "订单状态错误" in body, body[:80])
    st, body = call("PUT", "/admin/order/confirm", {"id": 99999999}, token)
    check("day09 操作不存在订单报错", "订单不存在" in body, body[:80])

    # ---------------- 拒单（已支付 -> 退款） ----------------
    order2, _ = make_order()
    st, body = call("PUT", "/admin/order/rejection",
                    {"id": order2, "rejectionReason": "菜品已售完" + RUN_ID}, token)
    check("day09 拒单", st == 200 and ok(body), body[:80])
    st, body = admin_detail(order2)
    d2 = body or {}
    check("day09 拒单后已取消并标记退款",
          d2.get("status") == 6 and d2.get("payStatus") == 2,
          "status=%s payStatus=%s" % (d2.get("status"), d2.get("payStatus")))
    check("day09 拒单原因已记录", ("菜品已售完" + RUN_ID) == d2.get("rejectionReason"), d2.get("rejectionReason"))

    st, body = call("PUT", "/admin/order/rejection", {"id": order2, "rejectionReason": "x"}, token)
    check("day09 已取消订单不能拒单", "订单状态错误" in body, body[:80])

    # ---------------- 管理端取消（接单后取消） ----------------
    order3, _ = make_order()
    call("PUT", "/admin/order/confirm", {"id": order3}, token)
    st, body = call("PUT", "/admin/order/cancel",
                    {"id": order3, "cancelReason": "店铺打烊" + RUN_ID}, token)
    check("day09 管理端取消已接单订单", st == 200 and ok(body), body[:80])
    st, body = admin_detail(order3)
    d3 = body or {}
    check("day09 取消后已退款并记录原因",
          d3.get("status") == 6 and d3.get("payStatus") == 2 and d3.get("cancelReason") == "店铺打烊" + RUN_ID,
          "status=%s reason=%s" % (d3.get("status"), d3.get("cancelReason")))

    # 收尾：清购物车 + 删地址
    ucall("DELETE", "/user/shoppingCart/clean", token=ut)
    if addr_id:
        ucall("DELETE", "/user/addressBook?id=" + str(addr_id), token=ut)


# ---------------------------------------------------------------- day10
class WSClient:
    """极简 WebSocket 客户端：只用标准库做握手 + 读文本帧，避免装第三方包。
    只够用来验证「服务端能推、客户端能收」，不做发送和断线重连。
    """

    def __init__(self, path, timeout=10):
        self.sock = socket.create_connection(("127.0.0.1", 8081), timeout)
        key = base64.b64encode(os.urandom(16)).decode()
        req = ("GET " + path + " HTTP/1.1\r\n"
               "Host: 127.0.0.1:8081\r\n"
               "Upgrade: websocket\r\n"
               "Connection: Upgrade\r\n"
               "Sec-WebSocket-Key: " + key + "\r\n"
               "Sec-WebSocket-Version: 13\r\n\r\n")
        self.sock.sendall(req.encode())
        buf = b""
        while b"\r\n\r\n" not in buf:
            chunk = self.sock.recv(4096)
            if not chunk:
                break
            buf += chunk
        head, _, rest = buf.partition(b"\r\n\r\n")
        self.status_line = head.decode("latin-1").splitlines()[0] if head else ""
        self.buf = rest

    def _recv_exact(self, n):
        while len(self.buf) < n:
            chunk = self.sock.recv(4096)
            if not chunk:
                raise IOError("websocket closed")
            self.buf += chunk
        data, self.buf = self.buf[:n], self.buf[n:]
        return data

    def _read_frame(self):
        b1, b2 = self._recv_exact(2)
        opcode, masked, length = b1 & 0x0F, b2 & 0x80, b2 & 0x7F
        if length == 126:
            length = struct.unpack(">H", self._recv_exact(2))[0]
        elif length == 127:
            length = struct.unpack(">Q", self._recv_exact(8))[0]
        mask = self._recv_exact(4) if masked else b""
        payload = self._recv_exact(length)
        if masked:
            payload = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
        return opcode, payload

    def wait_text(self, timeout=12):
        """等一条文本帧，超时返回 None；ping/pong/close 直接跳过"""
        deadline = time.time() + timeout
        while time.time() < deadline:
            self.sock.settimeout(max(0.5, deadline - time.time()))
            try:
                opcode, payload = self._read_frame()
            except (socket.timeout, TimeoutError):
                return None
            except IOError:
                return None
            if opcode == 1:
                return payload.decode("utf-8", "ignore")
        return None

    def close(self):
        try:
            self.sock.close()
        except Exception:
            pass


MYSQL_EXE = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"


def db_exec(sql):
    """直接改库（仅本地自测用：定时任务需要把 order_time 改到过去）。
    没有 mysql 客户端时返回 False，对应用例跳过。
    """
    pwd = os.environ.get("FASTDINE_DB_PASS", "123456")
    try:
        subprocess.run([MYSQL_EXE, "-uroot", "-p" + pwd, "fastdine", "-e", sql],
                       capture_output=True, timeout=20, check=True)
        return True
    except Exception as e:
        print("   [跳过] 数据库操作失败（定时任务用例）：%s" % e)
        return False


def run_day10():
    """day10 WebSocket 来单提醒 / 客户催单 + 订单状态定时处理"""
    token = login()
    if not token:
        return
    ut = user_token()
    if not ut:
        check("day10 拿不到C端令牌", False, "")
        return

    # 准备地址 + 起售菜品
    addr = {
        "consignee": "定时测试" + RUN_ID, "phone": "13600004444", "sex": "1",
        "provinceName": "广东省", "cityName": "深圳市", "districtName": "宝安区",
        "detail": "宝源路 4 号", "label": "公司", "isDefault": 1,
    }
    ucall("POST", "/user/addressBook", addr, token=ut)
    st, body = ucall("GET", "/user/addressBook/list", token=ut)
    addr_id = next((a.get("id") for a in (data_of(body) or []) if a.get("consignee") == addr["consignee"]), None)

    st, body = call("GET", "/admin/dish/page?page=1&pageSize=1&status=1", None, token)
    dish_id = (((data_of(body) or {}).get("records") or [{}])[0]).get("id")

    def make_order(paid=True):
        ucall("DELETE", "/user/shoppingCart/clean", token=ut)
        ucall("POST", "/user/shoppingCart/add", {"dishId": dish_id, "dishFlavor": "微辣"}, token=ut)
        st, body = ucall("POST", "/user/order/submit",
                         {"addressBookId": addr_id, "payMethod": 1, "remark": "selftest-" + RUN_ID}, token=ut)
        o = data_of(body) or {}
        if paid:
            ucall("PUT", "/user/order/payment", {"orderNumber": o.get("orderNumber"), "payMethod": 1}, token=ut)
        return o.get("id"), o.get("orderNumber")

    def admin_order(oid):
        st, body = call("GET", "/admin/order/details/" + str(oid), None, token)
        return data_of(body) or {}

    # ---------------- WebSocket 来单提醒 ----------------
    ws = WSClient("/ws/selftest" + RUN_ID)
    check("day10 WebSocket 握手成功", "101" in ws.status_line, ws.status_line)

    order_id, order_no = make_order(paid=False)
    ucall("PUT", "/user/order/payment", {"orderNumber": order_no, "payMethod": 1}, token=ut)
    msg = ws.wait_text()
    pushed = json.loads(msg) if msg else {}
    check("day10 支付后管理端收到来单提醒",
          pushed.get("type") == 1 and pushed.get("orderId") == order_id,
          msg)

    # ---------------- 客户催单 ----------------
    st, body = ucall("GET", "/user/order/reminder/" + str(order_id), token=ut)
    msg = ws.wait_text()
    pushed = json.loads(msg) if msg else {}
    check("day10 客户催单管理端收到推送",
          st == 200 and ok(body) and pushed.get("type") == 2 and pushed.get("orderId") == order_id,
          msg)

    # ---------------- 定时任务：超时未支付自动取消 / 派送中超时自动完成 ----------------
    unpaid_id, _ = make_order(paid=False)

    delivery_id, _ = make_order(paid=True)
    call("PUT", "/admin/order/confirm", {"id": delivery_id}, token)
    call("PUT", "/admin/order/delivery/" + str(delivery_id), None, token)

    backdated = db_exec("update orders set order_time = date_sub(now(), interval 20 minute) where id = %s"
                        % unpaid_id) and \
        db_exec("update orders set order_time = date_sub(now(), interval 70 minute) where id = %s"
                % delivery_id)

    if backdated:
        # 任务间隔默认 30s，最多等 45s
        deadline = time.time() + 45
        unpaid_status = delivery_status_now = None
        while time.time() < deadline:
            unpaid_status = admin_order(unpaid_id).get("status")
            delivery_status_now = admin_order(delivery_id).get("status")
            if unpaid_status == 6 and delivery_status_now == 5:
                break
            time.sleep(3)
        check("day10 超时未支付自动取消", unpaid_status == 6, "status=%s" % unpaid_status)
        check("day10 派送中超时自动完成", delivery_status_now == 5, "status=%s" % delivery_status_now)
        detail = admin_order(unpaid_id)
        check("day10 自动取消记录原因", "超时" in str(detail.get("cancelReason")), detail.get("cancelReason"))

        # 已取消的订单不能催单
        st, body = ucall("GET", "/user/order/reminder/" + str(unpaid_id), token=ut)
        check("day10 已取消订单不能催单", "订单状态错误" in body, body[:80])

    ws.close()

    # 收尾
    ucall("DELETE", "/user/shoppingCart/clean", token=ut)
    if addr_id:
        ucall("DELETE", "/user/addressBook?id=" + str(addr_id), token=ut)


# ---------------------------------------------------------------- day11
def run_day11():
    """day11 统计报表：营业额 / 用户 / 订单 / 销量 TOP10 + 工作台概览"""
    token = login()
    if not token:
        return
    ut = user_token()
    if not ut:
        check("day11 拿不到C端令牌", False, "")
        return

    today = time.strftime("%Y-%m-%d")
    yesterday = time.strftime("%Y-%m-%d", time.localtime(time.time() - 86400))

    st, _ = call("GET", "/admin/report/turnoverStatistics?begin=" + today + "&end=" + today)
    check("day11 未登录访问报表返回401", st == 401, "HTTP %s" % st)

    # 造一条「已完成」订单，让营业额/完成率有真实数据可断言
    addr = {
        "consignee": "报表测试" + RUN_ID, "phone": "13500005555", "sex": "1",
        "provinceName": "广东省", "cityName": "深圳市", "districtName": "龙岗区",
        "detail": "坂田 5 号", "label": "家", "isDefault": 1,
    }
    ucall("POST", "/user/addressBook", addr, token=ut)
    st, body = ucall("GET", "/user/addressBook/list", token=ut)
    addr_id = next((a.get("id") for a in (data_of(body) or []) if a.get("consignee") == addr["consignee"]), None)

    st, body = call("GET", "/admin/dish/page?page=1&pageSize=1&status=1", None, token)
    dish_id = (((data_of(body) or {}).get("records") or [{}])[0]).get("id")

    ucall("DELETE", "/user/shoppingCart/clean", token=ut)
    ucall("POST", "/user/shoppingCart/add", {"dishId": dish_id, "dishFlavor": "微辣"}, token=ut)
    st, body = ucall("POST", "/user/order/submit",
                     {"addressBookId": addr_id, "payMethod": 1, "remark": "selftest-" + RUN_ID}, token=ut)
    order = data_of(body) or {}
    order_id, order_amount = order.get("id"), float(order.get("orderAmount") or 0)
    ucall("PUT", "/user/order/payment", {"orderNumber": order.get("orderNumber"), "payMethod": 1}, token=ut)
    call("PUT", "/admin/order/confirm", {"id": order_id}, token)
    call("PUT", "/admin/order/delivery/" + str(order_id), None, token)
    call("PUT", "/admin/order/complete/" + str(order_id), None, token)

    # ---------------- 营业额 ----------------
    st, body = call("GET", "/admin/report/turnoverStatistics?begin=" + today + "&end=" + today, None, token)
    d = data_of(body) or {}
    dates = [x for x in (d.get("dateList") or "").split(",") if x]
    turnovers = [float(x) for x in (d.get("turnoverList") or "").split(",") if x]
    check("day11 营业额统计返回区间每一天", dates == [today] and len(turnovers) == 1, d.get("dateList"))
    check("day11 营业额包含刚完成的订单",
          bool(turnovers) and turnovers[0] >= order_amount,
          "turnover=%s order=%s" % (turnovers, order_amount))

    st, body = call("GET", "/admin/report/turnoverStatistics?begin=" + yesterday + "&end=" + today, None, token)
    d = data_of(body) or {}
    dates = [x for x in (d.get("dateList") or "").split(",") if x]
    check("day11 跨天区间补齐日期", dates == [yesterday, today], dates)

    # ---------------- 用户 / 订单 / TOP10 ----------------
    st, body = call("GET", "/admin/report/userStatistics?begin=" + yesterday + "&end=" + today, None, token)
    d = data_of(body) or {}
    total_users = [x for x in (d.get("totalUserList") or "").split(",") if x]
    new_users = [x for x in (d.get("newUserList") or "").split(",") if x]
    check("day11 用户统计三列长度一致",
          len(total_users) == 2 and len(new_users) == 2, d)
    check("day11 累计用户数递增", len(total_users) == 2 and int(total_users[1]) >= int(total_users[0]),
          total_users)

    st, body = call("GET", "/admin/report/ordersStatistics?begin=" + today + "&end=" + today, None, token)
    d = data_of(body) or {}
    total_count, valid_count = d.get("totalOrderCount"), d.get("validOrderCount")
    rate = d.get("orderCompletionRate")
    check("day11 订单统计含有效订单", isinstance(total_count, int) and total_count >= 1
          and isinstance(valid_count, int) and valid_count >= 1,
          "total=%s valid=%s" % (total_count, valid_count))
    check("day11 订单完成率计算正确",
          isinstance(rate, float) and abs(rate - round(valid_count * 1.0 / total_count, 2)) < 0.011,
          "rate=%s 期望≈%s" % (rate, round(valid_count * 1.0 / total_count, 2)))

    st, body = call("GET", "/admin/report/top10?begin=" + yesterday + "&end=" + today, None, token)
    d = data_of(body) or {}
    names = [x for x in (d.get("nameList") or "").split(",") if x]
    numbers = [x for x in (d.get("numberList") or "").split(",") if x]
    check("day11 销量TOP10名称与数量对齐", len(names) == len(numbers), "names=%s" % names)
    check("day11 TOP10最多10条", len(names) <= 10, len(names))

    # ---------------- 工作台 ----------------
    st, body = call("GET", "/admin/workspace/todayData", None, token)
    d = data_of(body) or {}
    check("day11 今日营业额不含糊", isinstance(d.get("turnover"), (int, float))
          and float(d.get("turnover")) >= order_amount,
          "turnover=%s order=%s" % (d.get("turnover"), order_amount))
    check("day11 今日客单价与完成率有值",
          float(d.get("unitPrice") or 0) > 0 and 0 <= float(d.get("orderCompletionRate") or 0) <= 1, d)

    # 管理端 dist 里调的是 businessData，不是 todayData，两个路径都得能访问
    st, body = call("GET", "/admin/workspace/businessData", None, token)
    check("day11 工作台 businessData 路径可用",
          st == 200 and (data_of(body) or {}).get("turnover") is not None, body[:80])

    st, body = call("GET", "/admin/workspace/overviewOrders", None, token)
    d = data_of(body) or {}
    parts = sum(int(d.get(k) or 0) for k in
                ("waitingOrders", "deliveredOrders", "completedOrders", "cancelledOrders"))
    check("day11 订单概览合计不超过全部订单",
          all(d.get(k) is not None for k in
              ("waitingOrders", "deliveredOrders", "completedOrders", "cancelledOrders", "allOrders"))
          and parts <= int(d.get("allOrders") or 0),
          d)

    st, body = call("GET", "/admin/dish/page?page=1&pageSize=1", None, token)
    dish_total = int((data_of(body) or {}).get("total") or 0)
    st, body = call("GET", "/admin/workspace/overviewDishes", None, token)
    d = data_of(body) or {}
    check("day11 菜品概览起售+停售=总数",
          int(d.get("sold") or 0) + int(d.get("discontinued") or 0) == dish_total,
          "sold=%s discontinued=%s total=%s" % (d.get("sold"), d.get("discontinued"), dish_total))

    st, body = call("GET", "/admin/setmeal/page?page=1&pageSize=1", None, token)
    setmeal_total = int((data_of(body) or {}).get("total") or 0)
    st, body = call("GET", "/admin/workspace/overviewSetmeals", None, token)
    d = data_of(body) or {}
    check("day11 套餐概览起售+停售=总数",
          int(d.get("sold") or 0) + int(d.get("discontinued") or 0) == setmeal_total,
          "sold=%s discontinued=%s total=%s" % (d.get("sold"), d.get("discontinued"), setmeal_total))

    # 收尾
    ucall("DELETE", "/user/shoppingCart/clean", token=ut)
    if addr_id:
        ucall("DELETE", "/user/addressBook?id=" + str(addr_id), token=ut)


# ---------------------------------------------------------------- day12
def run_day12():
    """day12 运营数据报表 Excel 导出（校验真实 xlsx 内容，不只看状态码）"""
    import io
    import zipfile

    token = login()
    if not token:
        return

    st, _, _ = call_raw("GET", "/admin/report/export")
    check("day12 未登录导出报表返回401", st == 401, "HTTP %s" % st)

    # 单日区间：行数可精确断言（标题 + 汇总表头 + 汇总 + 明细表头 + 1 天明细）
    today = time.strftime("%Y-%m-%d")
    st, headers, data = call_raw("GET", "/admin/report/export?begin=" + today + "&end=" + today, token)
    check("day12 导出返回200", st == 200, "HTTP %s" % st)
    check("day12 响应的ContentType是xlsx",
          "spreadsheetml" in (headers.get("Content-Type") or ""), headers.get("Content-Type"))
    check("day12 响应带下载文件名",
          "attachment" in (headers.get("Content-Disposition") or ""), headers.get("Content-Disposition"))
    check("day12 内容是合法 xlsx(PK 开头)", data[:2] == b"PK" and len(data) > 3000, "%s 字节" % len(data))

    zf = zipfile.ZipFile(io.BytesIO(data))
    names = zf.namelist()
    sheets = [n for n in names if n.startswith("xl/worksheets/sheet")]
    check("day12 xlsx 内含工作表", bool(sheets), names[:4])

    shared = ""
    if "xl/sharedStrings.xml" in names:
        shared = zf.read("xl/sharedStrings.xml").decode("utf-8", "ignore")
    heads = ["日期", "营业额", "有效订单", "订单完成率", "平均客单价", "新增用户"]
    check("day12 表头字段齐全", all(h in shared for h in heads),
          "缺失：%s" % [h for h in heads if h not in shared])
    check("day12 标题含区间日期", today in shared, shared[:120])

    if sheets:
        sheet_xml = zf.read(sheets[0]).decode("utf-8", "ignore")
        rows = sheet_xml.count("<row ")
        check("day12 单日区间共5行", rows == 5, "实际 %s 行" % rows)
        check("day12 明细行写入日期与数值", today in shared, today)

    # 默认近 30 天：行数应明显更多
    st, _, data30 = call_raw("GET", "/admin/report/export", token)
    zf30 = zipfile.ZipFile(io.BytesIO(data30))
    sheets30 = [n for n in zf30.namelist() if n.startswith("xl/worksheets/sheet")]
    rows30 = zf30.read(sheets30[0]).decode("utf-8", "ignore").count("<row ") if sheets30 else 0
    check("day12 默认导出近30天", st == 200 and 30 <= rows30 <= 35, "共 %s 行" % rows30)
    check("day12 默认文件比单日大", len(data30) > len(data), "%s > %s" % (len(data30), len(data)))


# ---------------------------------------------------------------- 入口
ALL = [
    ("day02", run_day02),
    ("day03", run_day03),
    ("day04", run_day04),
    ("day05", run_day05),
    ("day06", run_day06),
    ("day07", run_day07),
    ("day08", run_day08),
    ("day09", run_day09),
    ("day10", run_day10),
    ("day11", run_day11),
    ("day12", run_day12),
]


def main():
    args = [a.lower() for a in sys.argv[1:]]
    cases = [c for c in ALL if not args or c[0] in args]
    if not cases:
        print("未找到章节：%s，可选 %s" % (args, [c[0] for c in ALL]))
        return 2

    for _, func in cases:
        func()

    width = max(len(r[0]) for r in _results) + 2
    for label, status, detail in _results:
        print(("%-" + str(width) + "s %s | %s") % (label, status, detail))
    passed = sum(1 for r in _results if r[1] == "PASS")
    print("\n结果：%d/%d 通过" % (passed, len(_results)))
    return 0 if passed == len(_results) else 1


if __name__ == "__main__":
    sys.exit(main())
