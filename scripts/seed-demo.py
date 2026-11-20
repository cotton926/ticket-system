# -*- coding: utf-8 -*-
"""生成演示工单数据。

刻意**全部走真实 REST 接口**而不是直接 INSERT：
这样流转日志、SLA 截止时间、乐观锁版本号都由业务代码自己算，
截图里的时间线和状态才跟真实运行一致（直接写库只会得到一堆没有日志的空壳）。

用法：
    python scripts/seed-demo.py [base_url]
默认 http://localhost:8080
"""
import json
import sys
import urllib.request
import urllib.error
import urllib.parse

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080") + "/api/v1"

TOKENS = {}


def call(method, path, token=None, body=None, headers=None):
    url = BASE + path
    data = None
    hdrs = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        hdrs["Content-Type"] = "application/json"
    if token:
        hdrs["Authorization"] = "Bearer " + token
    if headers:
        hdrs.update(headers)

    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        payload = json.loads(e.read().decode("utf-8"))

    if payload.get("code") != 0:
        raise RuntimeError("%s %s -> %s %s" % (method, path, payload.get("code"), payload.get("message")))
    return payload.get("data")


def login(username, password="123456"):
    if username in TOKENS:
        return TOKENS[username]
    d = call("POST", "/auth/login", body={"username": username, "password": password})
    TOKENS[username] = d["token"]
    return d["token"]


def create(user, title, content, type_id, priority):
    tk = login(user)
    tok = call("GET", "/idempotent-token", tk)["token"]
    d = call("POST", "/tickets", tk, {"title": title, "content": content,
                                      "typeId": type_id, "priority": priority},
             {"X-Idempotent-Token": tok})
    return d["id"], d["ticketNo"]


def do(user, tid, action, body=None):
    return call("PUT", "/tickets/%d/%s" % (tid, action), login(user), body or {})


SEED = [
    ("财务部打印机无法连接共享打印队列", "4 楼财务部 HP M403 打印机，今天上午开始所有电脑都提示「打印机脱机」。已重启打印机、重装驱动，问题依旧。影响月度报表打印，请尽快处理。", 4, 2),
    ("新员工入职需开通 OA 与邮箱账号", "下周一有 3 位新同事入职（市场部），需要开通 OA、企业邮箱、钉钉账号，并加入对应部门群。工位在 6 楼 A 区。", 2, 2),
    ("三楼会议室无线网络频繁掉线", "三楼 301 会议室开视频会议时，Wi-Fi 每隔 5 分钟断一次，重连后音视频卡顿严重。有线网络正常。", 3, 3),
    ("ERP 登录提示账号已锁定", "今早登录 ERP 提示「账号已锁定，请联系管理员」。昨天正常使用，没有改过密码。急用，下午要出库存报表。", 1, 1),
    ("申请安装 VS Code 开发环境", "新配的笔记本需要安装 VS Code、Node.js 20、Git，用于前端项目开发。需要管理员权限安装。", 4, 4),
    ("办公区空调出风口异响影响办公", "5 楼东侧靠窗的空调出风口持续发出金属摩擦声，声音较大，周边 8 个工位受影响。", 5, 4),
    ("外部邮件接收延迟超过 30 分钟", "上午开始外部发来的邮件延迟严重，最长一封延迟 47 分钟。内部邮件正常。已确认不是对方服务器问题。", 3, 2),
    ("报销系统附件上传失败提示 413", "提交差旅报销时上传发票 PDF（约 8MB）失败，提示 413 Request Entity Too Large。小额图片可以上传。", 1, 1),
    ("申请会议室长期预约权限", "项目组每周二、周四下午固定使用 502 会议室，需要开通长期预约权限，周期暂定 3 个月。", 5, 4),
    ("笔记本电池续航异常半天耗光", "办公笔记本满电后只能使用 2 小时左右，之前是 6 小时。电池健康度显示 68%。", 4, 3),
]


def main():
    print("== 1. 提交工单 ==")
    ids = []
    for i, (title, content, tid, pri) in enumerate(SEED):
        user = "employee2" if i % 2 == 0 else "employee"
        t, no = create(user, title, content, tid, pri)
        ids.append((t, user))
        print("   %-18s %s  (%s)" % (no, title[:22], user))

    print("== 2. 分派 ==")
    # 1,2,4,7,8,10 派给李工；3,6 派给王调度
    for idx in (1, 2, 4, 7, 8, 10):
        do("admin", ids[idx - 1][0], "assign", {"assigneeId": 3, "remark": "按区域分派给运维组李工处理"})
    for idx in (3, 6):
        do("admin", ids[idx - 1][0], "assign", {"assigneeId": 2, "remark": "涉及网络与物业，转王调度协调"})
    print("   已分派 8 条，保留 2 条待分派")

    print("== 3. 接单 ==")
    for idx in (1, 4, 8):
        do("handler", ids[idx - 1][0], "accept", {"remark": "已接单，10 分钟内联系提交人"})
    for idx in (3, 6):
        do("dispatcher", ids[idx - 1][0], "accept", {"remark": "已安排现场排查"})
    print("   已接单 5 条")

    print("== 4. 完成 / 验收 / 挂起 ==")
    # 1 号：完成 -> 验收通过 -> 已关闭
    do("handler", ids[0][0], "finish", {"remark": "更换打印机网卡并重配 IP，已恢复共享，现场测试正常"})
    do("employee2", ids[0][0], "verify", {"pass": True, "remark": "已确认可以正常打印，感谢"})
    # 8 号：完成 -> 待验收（留一条待验收给演示看）
    do("handler", ids[7][0], "finish", {"remark": "已调整 Nginx client_max_body_size 至 20m，重新上传验证通过"})
    # 4 号：挂起（等厂商）
    do("handler", ids[3][0], "suspend", {"remark": "ERP 账号锁定需厂商侧解锁，已提交工单等待回复，预计明天上午恢复"})
    print("   1 条已关闭 / 1 条待验收 / 1 条挂起")

    print("\n全部完成。可登录 admin / 123456 查看。")


if __name__ == "__main__":
    main()
