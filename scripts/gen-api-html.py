# -*- coding: utf-8 -*-
"""把 /v3/api-docs 的 OpenAPI 规格渲染成单文件离线 HTML 接口文档。

用途：内网/离线环境或预览面板访问不到 localhost 时，直接双击打开即可查阅。
不依赖任何 CDN、不发起网络请求。

用法：
    python scripts/gen-api-html.py docs/api-docs.json docs/api-docs.html
"""
import json
import sys
import html

METHOD_ORDER = ["get", "post", "put", "delete", "patch"]

# 按路径前缀分组，比 springdoc 默认的 xxx-controller 可读
GROUPS = [
    ("auth", "认证与会话", "A"),
    ("idempotent", "幂等令牌", "I"),
    ("tickets", "工单核心", "T"),
    ("stats", "统计", "S"),
    ("users", "用户与权限", "U"),
]
DEFAULT_GROUP = ("其他", "X")


def esc(s):
    return html.escape(str(s if s is not None else ""), quote=True)


class Spec:
    def __init__(self, doc):
        self.doc = doc
        self.schemas = doc.get("components", {}).get("schemas", {}) or {}

    def deref(self, node):
        """展开一层 $ref；非 ref 原样返回。"""
        if not isinstance(node, dict):
            return {}
        ref = node.get("$ref")
        if isinstance(ref, str):
            name = ref.rsplit("/", 1)[-1]
            return self.schemas.get(name, {})
        return node

    def ref_name(self, node):
        if isinstance(node, dict) and isinstance(node.get("$ref"), str):
            return node["$ref"].rsplit("/", 1)[-1]
        return None

    def type_of(self, node):
        n = self.deref(node)
        if "type" in n:
            return n["type"]
        if "properties" in n:
            return "object"
        if "items" in n:
            return "array"
        if "allOf" in n:
            return "object"
        return "-"

    def example(self, node, depth=0, seen=None):
        """按 schema 生成示例值，供复制粘贴调试。"""
        seen = seen or set()
        name = self.ref_name(node)
        if name:
            if name in seen or depth > 4:
                return {}
            seen = seen | {name}
        n = self.deref(node)
        if "enum" in n and n["enum"]:
            return n["enum"][0]
        if "example" in n:
            return n["example"]
        if "allOf" in n:
            merged = {}
            for part in n["allOf"]:
                v = self.example(part, depth + 1, seen)
                if isinstance(v, dict):
                    merged.update(v)
            return merged
        t = self.type_of(n)
        if t == "string":
            fmt = n.get("format")
            if fmt == "date-time":
                return "2026-11-18 20:00:00"
            if fmt == "date":
                return "2026-11-18"
            return n.get("default", "string")
        if t == "integer":
            return n.get("default", 0)
        if t == "number":
            return n.get("default", 0)
        if t == "boolean":
            return n.get("default", False)
        if t == "array":
            return [self.example(n.get("items", {}), depth + 1, seen)]
        if t == "object" or "properties" in n:
            out = {}
            for k, v in (n.get("properties") or {}).items():
                out[k] = self.example(v, depth + 1, seen)
            return out
        return None

    def render(self, node, depth=0, seen=None, collapsed=False):
        """递归渲染 schema 为 HTML 表格行。"""
        seen = seen or set()
        name = self.ref_name(node)
        if name:
            if name in seen:
                return '<div class="s-row"><span class="s-name muted">… 循环引用 %s</span></div>' % esc(name)
            seen = seen | {name}
        n = self.deref(node)
        if "allOf" in n:
            merged = {"type": "object", "properties": {}}
            for part in n["allOf"]:
                p = self.deref(part)
                merged["properties"].update(p.get("properties") or {})
            n = merged
        t = self.type_of(n)
        if t == "object" and n.get("properties"):
            rows = []
            required = set(n.get("required") or [])
            for k, v in n["properties"].items():
                vn = self.deref(v)
                vt = self.type_of(v)
                ref = self.ref_name(v)
                label = ref or vt
                if vt == "array":
                    it = vn.get("items", {})
                    label = "array&lt;%s&gt;" % esc(self.ref_name(it) or self.type_of(it))
                desc = vn.get("description") or ""
                if vn.get("enum"):
                    desc = (desc + " 可选值: " + " / ".join(str(x) for x in vn["enum"])).strip()
                star = '<span class="req">*</span>' if k in required else ""
                rows.append(
                    '<div class="s-row"><span class="s-name">%s%s</span>'
                    '<span class="s-type">%s</span><span class="s-desc">%s</span></div>'
                    % (esc(k), star, label, esc(desc))
                )
                if vt in ("object", "array") and depth < 3:
                    child = vn.get("items", {}) if vt == "array" else vn
                    sub = self.render(child, depth + 1, seen)
                    if sub.strip():
                        rows.append('<div class="s-sub">%s</div>' % sub)
            if collapsed:
                return '<details class="s-box"><summary>展开字段结构</summary>%s</details>' % "".join(rows)
            return "".join(rows)
        if t == "array":
            return self.render(n.get("items", {}), depth, seen)
        return ""


def group_of(path):
    """按路径段匹配分组：/api/v1/tickets/{id}/accept -> 工单核心。

    不能直接用 startswith，因为网关前缀（/api/v1）会把所有路径归到同一组。
    """
    segs = [s for s in path.split("/") if s]
    for seg in segs:
        for prefix, label, key in GROUPS:
            # 允许 idempotent-token 这类"前缀-后缀"命名归到 idempotent
            if seg == prefix or seg.startswith(prefix + "-"):
                return prefix, label, key
    return None, DEFAULT_GROUP[0], DEFAULT_GROUP[1]


def describe_params(spec, op, path_item):
    """渲染参数表。

    springdoc 会把 @ModelAttribute 的查询对象渲染成单个 object 参数（名字如 req），
    这里展开成它的具体字段，否则文档上只有一个 req，看不出能传什么。
    """
    rows = []
    params = list(path_item.get("parameters") or []) + list(op.get("parameters") or [])

    def row(name, loc, req, t, desc):
        rows.append(
            '<div class="s-row"><span class="s-name">%s%s</span><span class="s-type">%s · %s</span>'
            '<span class="s-desc">%s</span></div>'
            % (esc(name), '<span class="req">*</span>' if req else "", esc(loc), esc(t), esc(desc))
        )

    for p in params:
        p = spec.deref(p)
        sch = p.get("schema") or {}
        props = spec.deref(sch).get("properties")
        if spec.type_of(sch) == "object" and props:
            required = set(spec.deref(sch).get("required") or [])
            for k, v in props.items():
                vn = spec.deref(v)
                t = spec.type_of(vn)
                if vn.get("enum"):
                    t += " (" + " / ".join(str(x) for x in vn["enum"]) + ")"
                desc = vn.get("description") or ""
                if vn.get("format"):
                    desc = (desc + " [" + vn["format"] + "]").strip()
                row(k, p.get("in", "query"), k in required, t, desc)
            continue
        t = spec.type_of(sch)
        if sch.get("enum"):
            t += " (" + " / ".join(str(x) for x in sch["enum"]) + ")"
        row(p.get("name", ""), p.get("in", ""), p.get("required", False), t, p.get("description") or "")
    return "".join(rows)


def build(src, dst):
    doc = json.load(open(src, encoding="utf-8"))
    spec = Spec(doc)
    paths = doc.get("paths", {})
    base = doc.get("servers", [{}])[0].get("url", "/")
    title = doc.get("info", {}).get("title") or "API"
    version = doc.get("info", {}).get("version") or ""

    # 分组收集
    buckets = {}
    total = 0
    for path, item in paths.items():
        for m in METHOD_ORDER:
            if m not in item:
                continue
            total += 1
            _, label, key = group_of(path)
            buckets.setdefault((key, label), []).append((path, m, item[m], item))

    sections = []
    nav = []
    order = {k: i for i, (_, _, k) in enumerate(GROUPS)}
    order[DEFAULT_GROUP[1]] = len(GROUPS)
    for (key, label), items in sorted(buckets.items(), key=lambda x: order.get(x[0][0], 99)):
        anchor = "g-" + key
        nav.append(
            '<a class="nav-item" href="#%s"><b>%s</b><span>%s</span><em>%d</em></a>'
            % (anchor, esc(label[:1]), esc(label), len(items))
        )
        cards = []
        for path, m, op, item in sorted(items, key=lambda x: x[0]):
            summary = op.get("summary") or op.get("operationId") or ""
            desc = op.get("description") or ""
            pid = "p-" + str(abs(hash(path + m)) % 100000)
            body_html = ""
            rb = op.get("requestBody")
            if rb:
                rb = spec.deref(rb)
                content = rb.get("content") or {}
                sch = (content.get("application/json") or content.get("*/*") or {}).get("schema")
                if sch:
                    ex = spec.example(sch)
                    body_html = (
                        '<div class="block"><div class="block-t">请求体 Request Body%s</div>%s'
                        '<pre class="json">%s</pre></div>'
                        % (
                            " · 必填" if rb.get("required") else "",
                            spec.render(sch, collapsed=True),
                            esc(json.dumps(ex, ensure_ascii=False, indent=2)),
                        )
                    )
            params_html = describe_params(spec, op, item)
            if params_html:
                params_html = '<div class="block"><div class="block-t">参数</div>%s</div>' % params_html
            resp_html = ""
            for code, r in (op.get("responses") or {}).items():
                content = (spec.deref(r).get("content") or {})
                sch = (content.get("application/json") or content.get("*/*") or {}).get("schema")
                if sch:
                    resp_html = (
                        '<div class="block"><div class="block-t">响应 %s</div>%s'
                        '<pre class="json">%s</pre></div>'
                        % (
                            esc(code),
                            spec.render(sch, collapsed=True),
                            esc(json.dumps(spec.example(sch), ensure_ascii=False, indent=2)),
                        )
                    )
                    break
            cards.append(
                """
<article class="card" id="%s" data-search="%s %s %s">
  <div class="card-h">
    <span class="m m-%s">%s</span>
    <code class="path">%s</code>
  </div>
  <div class="card-b">
    <p class="summary">%s</p>
    %s%s%s%s
  </div>
</article>"""
                % (
                    pid,
                    esc((path + " " + m + " " + summary).lower()),
                    esc(label),
                    "",
                    m,
                    m.upper(),
                    esc(path),
                    esc(summary),
                    '<p class="desc">%s</p>' % esc(desc) if desc else "",
                    params_html,
                    body_html,
                    resp_html,
                )
            )
        sections.append(
            '<section id="%s"><h2>%s <small>%d 个接口</small></h2>%s</section>'
            % (anchor, esc(label), len(items), "".join(cards))
        )

    page = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>%s · 接口文档</title>
<style>
:root{--bg:#fff;--fg:#1f2328;--mut:#6b7280;--line:#e5e7eb;--card:#fff;--soft:#f6f8fa;--accent:#2563eb;}
@media (prefers-color-scheme:dark){:root{--bg:#16181d;--fg:#e6e6e6;--mut:#9aa4b2;--line:#2c313a;--card:#1c1f26;--soft:#22262e;--accent:#60a5fa;}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:14px/1.65 -apple-system,"Segoe UI","Microsoft YaHei",sans-serif}
header{border-bottom:1px solid var(--line);padding:22px 28px;background:var(--soft)}
h1{margin:0 0 6px;font-size:20px;font-weight:650}
.meta{color:var(--mut);font-size:12.5px}
.meta code{background:rgba(127,127,127,.14);padding:1px 6px;border-radius:4px}
.wrap{display:flex;align-items:flex-start;gap:0}
nav{position:sticky;top:0;width:210px;flex:0 0 210px;height:100vh;overflow:auto;border-right:1px solid var(--line);padding:16px 0}
nav .search{margin:0 14px 12px;width:calc(100%% - 28px);padding:7px 10px;border:1px solid var(--line);border-radius:7px;background:var(--card);color:var(--fg);font-size:13px;outline:none}
nav .search:focus{border-color:var(--accent)}
.nav-item{display:flex;align-items:center;gap:8px;padding:8px 16px;color:var(--fg);text-decoration:none;font-size:13px}
.nav-item:hover{background:var(--soft)}
.nav-item b{width:20px;height:20px;flex:0 0 20px;display:flex;align-items:center;justify-content:center;background:var(--accent);color:#fff;border-radius:5px;font-size:11px;font-weight:600}
.nav-item em{margin-left:auto;color:var(--mut);font-style:normal;font-size:11.5px}
main{flex:1;min-width:0;padding:24px 28px 80px;max-width:1080px}
h2{font-size:16px;margin:32px 0 14px;padding-bottom:8px;border-bottom:1px solid var(--line)}
h2 small{color:var(--mut);font-weight:400;font-size:12px;margin-left:6px}
.card{border:1px solid var(--line);border-radius:10px;background:var(--card);margin-bottom:14px;overflow:hidden}
.card-h{display:flex;align-items:center;gap:10px;padding:12px 16px;background:var(--soft);border-bottom:1px solid var(--line)}
.m{font:600 11px/1 ui-monospace,SFMono-Regular,Consolas,monospace;padding:5px 8px;border-radius:5px;color:#fff;letter-spacing:.4px}
.m-get{background:#0d9488}.m-post{background:#2563eb}.m-put{background:#d97706}.m-delete{background:#dc2626}.m-patch{background:#7c3aed}
.path{font:13px ui-monospace,SFMono-Regular,Consolas,monospace;color:var(--fg);word-break:break-all}
.card-b{padding:14px 16px}
.summary{margin:0 0 4px;font-weight:600}
.desc{margin:0 0 10px;color:var(--mut);font-size:13px}
.block{margin-top:14px;border:1px solid var(--line);border-radius:8px;overflow:hidden}
.block-t{padding:7px 12px;background:var(--soft);border-bottom:1px solid var(--line);font-size:12px;color:var(--mut);font-weight:600}
.s-row{display:grid;grid-template-columns:180px 130px 1fr;gap:10px;padding:5px 12px;border-top:1px solid var(--line);font-size:12.5px}
.s-row:first-child{border-top:none}
.s-name{font-family:ui-monospace,Consolas,monospace;word-break:break-all}
.s-name .req{color:#dc2626;margin-left:2px}
.s-type{color:var(--accent);font-family:ui-monospace,Consolas,monospace;word-break:break-all}
.s-desc{color:var(--mut)}
.s-sub{padding-left:20px;border-left:2px solid var(--line);margin-left:12px}
.s-box{margin:6px 12px}
.s-box summary{cursor:pointer;color:var(--accent);font-size:12.5px;padding:4px 0}
.s-box .s-row{border-top:1px dashed var(--line)}
pre.json{margin:0;padding:12px;background:var(--soft);font:12px/1.6 ui-monospace,Consolas,monospace;overflow:auto;color:var(--fg);white-space:pre}
.muted{color:var(--mut)}
.hide{display:none}
footer{padding:20px 28px;color:var(--mut);font-size:12px;border-top:1px solid var(--line)}
@media (max-width:820px){nav{display:none}.s-row{grid-template-columns:1fr;gap:2px}main{padding:16px}}
</style>
</head>
<body>
<header>
  <h1>%s</h1>
  <div class="meta">OpenAPI %s &nbsp;·&nbsp; %d 个接口 &nbsp;·&nbsp; %d 个数据模型 &nbsp;·&nbsp; 服务地址 <code>%s</code></div>
</header>
<div class="wrap">
<nav>
  <input class="search" id="q" placeholder="搜索接口 / 路径…" autocomplete="off">
  %s
</nav>
<main id="main">
%s
<div id="empty" class="hide muted" style="padding:30px 0">没有匹配的接口</div>
</main>
</div>
<footer>由 ticket-system 自动生成 · 静态离线文档，无需联网 · 启动服务后可在 <code>/swagger-ui/index.html</code> 调试</footer>
<script>
(function(){
  var q=document.getElementById('q');
  var cards=[].slice.call(document.querySelectorAll('.card'));
  var secs=[].slice.call(document.querySelectorAll('section'));
  q.addEventListener('input',function(){
    var v=q.value.trim().toLowerCase();
    var hit=0;
    cards.forEach(function(c){
      var ok=!v||c.getAttribute('data-search').indexOf(v)>-1;
      c.classList.toggle('hide',!ok);
      if(ok)hit++;
    });
    secs.forEach(function(s){
      var any=[].slice.call(s.querySelectorAll('.card')).some(function(c){return !c.classList.contains('hide')});
      s.classList.toggle('hide',!any);
    });
    document.getElementById('empty').classList.toggle('hide',hit>0);
  });
})();
</script>
</body>
</html>""" % (
        esc(title),
        esc(title),
        esc(doc.get("openapi", "")),
        total,
        len(spec.schemas),
        esc(base),
        "".join(nav),
        "".join(sections),
    )
    open(dst, "w", encoding="utf-8").write(page)
    print("written:", dst, len(page), "chars,", total, "endpoints")


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2])
