#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
离线构建「系统架构知识图谱」种子文件。

为什么需要离线构建：
    ai-service 运行时本来可以直接拉各服务的 /v3/api-docs（springdoc 已经开着），
    但那样要求 9 个服务全部启动才能建图，本地开发和 CI 里不现实。
    所以这里静态扫一遍 Controller 源码，先出一份可用的图；
    ai-service 启动时如果拉到线上 OpenAPI，会用真实 schema 覆盖同名节点。

输出：
    backend/ai-service/src/main/resources/arch-graph-seed.json

用法：
    python scripts/build-arch-graph.py
"""

import io
import json
import os
import re
import sys
from datetime import datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BACKEND = os.path.join(ROOT, "backend")
OUT = os.path.join(BACKEND, "ai-service", "src", "main", "resources", "arch-graph-seed.json")

# 服务 → 网关前缀。
# 网关 StripPrefix=2 表示请求去掉 /api/v1 后转发，所以服务内路径自带一级（如 /farms 之外还带 /breeding）；
# breeding-service 是 StripPrefix=3，服务内路径不带 /breeding，所以前缀要带全。
SERVICE_PREFIX = {
    "auth-service": "/api/v1",
    "breeding-service": "/api/v1/breeding",
    "slaughter-service": "/api/v1",
    "distribution-service": "/api/v1",
    "sales-service": "/api/v1",
    "trace-service": "/api/v1",
    "blockchain-service": "/api/v1",
    "file-service": "/api/v1",
    "ingest-service": "/api/v1",
    "ai-service": "/api/v1/ai",
}

# 这些服务的 controller 是给内部服务/设备调的，不该让 AI 去执行
INTERNAL_KEYWORDS = ("internal", "deviceingest", "device-ingest", "ingest")


def is_internal_path(path):
    """按路径段判定服务间接口。

    只看类名会漏：不少 Controller 把 /internal 写在方法级注解上，
    例如 sales-service 的 GET /sales/internal/products —— 类名里没有 internal，
    但它同样是服务间接口，不该让 AI 去调。
    """
    if not path:
        return False
    for seg in path.split("/"):
        s = seg.lower()
        if any(k in s for k in INTERNAL_KEYWORDS):
            return True
    return False

SIMPLE_TYPES = {
    "String", "Integer", "Long", "int", "long", "Boolean", "boolean",
    "Double", "double", "Float", "float", "BigDecimal", "Short", "short",
    "Byte", "byte", "Character", "char", "Object", "Date", "LocalDate",
    "LocalDateTime", "LocalTime", "BigInteger",
}

MAPPING_ANNOS = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "DeleteMapping": "DELETE",
    "PatchMapping": "PATCH",
}

# 这些是框架注入的形参，不是业务槽位
SKIP_PARAM_TYPES = {
    "HttpServletRequest", "HttpServletResponse", "HttpSession", "Model",
    "ModelMap", "BindingResult", "Principal", "Authentication", "Locale",
    "Errors", "ServletRequest", "ServletResponse",
}


# --------------------------------------------------------------------------
# 源码预处理：把注释抹成空格，保持 offset 不变，这样后续定位不会错位
# --------------------------------------------------------------------------
def strip_comments(src):
    """返回 (clean, javadocs)；javadocs 是 [(end_offset, text)]"""
    out = []
    javadocs = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == '"':
            # 字符串原样保留
            j = i + 1
            while j < n:
                if src[j] == "\\":
                    j += 2
                    continue
                if src[j] == '"':
                    j += 1
                    break
                j += 1
            out.append(src[i:j])
            i = j
        elif c == "'":
            j = i + 1
            while j < n:
                if src[j] == "\\":
                    j += 2
                    continue
                if src[j] == "'":
                    j += 1
                    break
                j += 1
            out.append(src[i:j])
            i = j
        elif src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i))
            i = j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            if src.startswith("/**", i):
                javadocs.append((j, src[i + 3:j - 2]))
            out.append(" " * (j - i))
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out), javadocs


def match_paren(src, start):
    """从 src[start] 之后的第一个 '(' 开始，返回配对 ')' 的下标"""
    i = src.find("(", start)
    if i < 0:
        return -1, -1
    depth = 0
    j = i
    n = len(src)
    in_str = False
    while j < n:
        c = src[j]
        if in_str:
            if c == "\\":
                j += 2
                continue
            if c == '"':
                in_str = False
        elif c == '"':
            in_str = True
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i, j
        j += 1
    return i, -1


def split_top(s):
    """按顶层逗号切分（忽略 <> () {} 内部的逗号）"""
    parts, buf = [], []
    depth = 0
    in_str = False
    for c in s:
        if in_str:
            buf.append(c)
            if c == '"':
                in_str = False
            continue
        if c == '"':
            in_str = True
            buf.append(c)
        elif c in "(<[{":
            depth += 1
            buf.append(c)
        elif c in ")>]}":
            depth -= 1
            buf.append(c)
        elif c == "," and depth == 0:
            parts.append("".join(buf).strip())
            buf = []
        else:
            buf.append(c)
    tail = "".join(buf).strip()
    if tail:
        parts.append(tail)
    return parts


def ann_string(body, key):
    """从注解参数里取 "key" 的字符串值"""
    m = re.search(r'\b' + key + r'\s*=\s*"((?:[^"\\]|\\.)*)"', body)
    return m.group(1).replace('\\"', '"') if m else None


def ann_bool(body, key):
    m = re.search(r'\b' + key + r'\s*=\s*(true|false)', body)
    return None if not m else m.group(1) == "true"


def extract_paths(body):
    """@GetMapping({"", "/page"}) / @GetMapping("/x") / @GetMapping(path="/x") → list"""
    if body is None:
        return [""]
    # 数组形式
    m = re.search(r'\{\s*((?:"[^"]*"\s*,?\s*)+)\}', body)
    if m:
        return re.findall(r'"([^"]*)"', m.group(1)) or [""]
    for key in ("value", "path"):
        v = ann_string(body, key)
        if v is not None:
            return [v]
    # 只有位置参数
    m = re.match(r'\s*"([^"]*)"', body)
    if m:
        return [m.group(1)]
    return [""]


def join_path(*parts):
    segs = []
    for p in parts:
        if not p:
            continue
        segs.extend([s for s in p.split("/") if s])
    return "/" + "/".join(segs)


# --------------------------------------------------------------------------
# 类：@RestController + @RequestMapping + @Tag
# --------------------------------------------------------------------------
CLASS_RE = re.compile(r'\b(?:public\s+)?(?:final\s+)?class\s+(\w+)')


def class_level(clean):
    """返回 (class_name, class_start, base_path, tag_name, tag_desc)"""
    m = CLASS_RE.search(clean)
    if not m:
        return None
    class_name, class_pos = m.group(1), m.start()

    head = clean[:class_pos]
    base_paths = [""]
    for am in re.finditer(r'@RequestMapping\s*', head):
        i, j = match_paren(head, am.end())
        if i < 0 or j < 0:
            continue
        base_paths = extract_paths(head[i + 1:j])
    tag_name, tag_desc = class_name.replace("Controller", ""), ""
    for am in re.finditer(r'@Tag\s*', head):
        i, j = match_paren(head, am.end())
        if i < 0 or j < 0:
            continue
        body = head[i + 1:j]
        tag_name = ann_string(body, "name") or tag_name
        tag_desc = ann_string(body, "description") or ""
    return class_name, class_pos, base_paths[0] if base_paths else "", tag_name, tag_desc


# --------------------------------------------------------------------------
# 方法：Mapping 注解 + 方法签名
# --------------------------------------------------------------------------
def parse_params(param_src):
    """解析方法参数串 → [ApiParam dict]"""
    params = []
    for raw in split_top(param_src):
        raw = raw.strip()
        if not raw:
            continue
        # 依次剥掉注解，并保留 (注解名, 注解体) —— 不能分两个列表错位配对，
        # 否则 @Valid @RequestBody FarmDTO dto 会被读成「Valid 的 body 是 FarmDTO dto」，
        # RequestBody 分支永远进不去，body 参数就退化成 query（踩过）
        annos = []
        rest = raw.strip()
        while rest.startswith("@"):
            m = re.match(r'@[\w.]+', rest)
            if not m:
                break
            anno_name = m.group(0)[1:]
            rest = rest[m.end():]
            anno_body = ""
            if rest.lstrip().startswith("("):
                lead = len(rest) - len(rest.lstrip())
                i, j = match_paren(rest, lead)
                if i >= 0 and j >= 0:
                    anno_body = rest[i + 1:j]
                    rest = rest[j + 1:]
            rest = rest.lstrip()
            annos.append((anno_name, anno_body))

        # rest 形如 "Long id" / "FarmDTO dto"
        m = re.match(r'^(?:final\s+)?([\w.<>\[\], ?]+?)\s+(\w+)$', rest)
        if not m:
            continue
        ptype = re.sub(r'\s+', '', m.group(1))
        pname = m.group(2)

        location, required, default = "query", None, None
        body_type = None

        for anno_name, anno_body in annos:
            short = anno_name.split(".")[-1]
            if short == "RequestParam":
                location = "query"
            elif short == "PathVariable":
                location = "path"
                required = True
            elif short == "RequestBody":
                location = "body"
                body_type = ptype
                required = True
            elif short == "RequestHeader":
                location = "header"
                required = False
            elif short == "RequestPart":
                location = "part"
            else:
                continue
            rb = ann_bool(anno_body, "required")
            if rb is not None:
                required = rb
            default = ann_string(anno_body, "defaultValue") or default
            alias = ann_string(anno_body, "name") or ann_string(anno_body, "value")
            if alias:
                pname = alias

        # 没写注解的参数：Spring 对简单类型当 query，且默认非必填
        if required is None:
            required = False

        # 带默认值的一定非必填（@RequestParam(defaultValue="1") 默认 required=false）
        if default is not None:
            required = False

        base_type = ptype.split("<")[0].replace("[]", "")

        # Servlet 容器注入的形参（HttpServletRequest 之类）不是业务参数，
        # 让它们进图谱只会让 Qwen 以为需要填。直接丢弃。
        if base_type in SKIP_PARAM_TYPES:
            continue

        is_simple = base_type in SIMPLE_TYPES or base_type.startswith(("List", "Set", "Collection"))

        params.append({
            "name": pname,
            "in": location,
            "type": ptype,
            "required": bool(required),
            "defaultValue": default,
            "description": "",
            "simple": bool(is_simple),
            "bodyType": body_type,
        })
    return params


def parse_methods(clean, class_start, base_path):
    """在 class 体内找 Mapping 注解，返回操作列表"""
    ops = []
    for anno, method in MAPPING_ANNOS.items():
        for m in re.finditer(r'@' + anno + r'\b', clean):
            pos = m.start()
            if pos < class_start:
                continue
            i, j = match_paren(clean, m.end())
            body = clean[i + 1:j] if (i >= 0 and j >= 0) else ""
            paths = extract_paths(body)

            # 注解之后到方法体之间：找方法名和参数
            after = clean[j + 1:] if j >= 0 else clean[m.end():]
            sig = re.search(
                r'(?:public|protected|private)\s+(?:<[^>]+>\s+)?([\w.<>\[\], ?]+?)\s+(\w+)\s*\(',
                after)
            if not sig:
                continue
            ret_type, mname = sig.group(1).strip(), sig.group(2)
            mpos = j + 1 + sig.start() if j >= 0 else m.end() + sig.start()

            pi, pj = match_paren(clean, mpos + sig.end() - sig.start() - 1)
            param_src = clean[pi + 1:pj] if (pi >= 0 and pj >= 0) else ""

            # 摘要：@Operation(summary) → javadoc 首行 → 方法名
            head = clean[pos:mpos]
            summary = ""
            om = re.search(r'@Operation\s*', head)
            if om:
                oi, oj = match_paren(head, om.end())
                if oi >= 0 and oj >= 0:
                    summary = ann_string(head[oi + 1:oj], "summary") or ""
            if not summary:
                summary = first_javadoc_line(clean, pos) or humanize(mname)

            for p in paths:
                ops.append({
                    "method": method,
                    "path": join_path(base_path, p),
                    "summary": summary.strip(),
                    "returnType": ret_type.strip(),
                    "javaMethod": mname,
                    "params": parse_params(param_src),
                    "_annoPos": pos,
                })
    return ops


def first_javadoc_line(clean, pos):
    """注解前最近的 /** */ 内容第一句（注释已被抹成空格，这里回不到原文，
    所以由外部传入 javadoc 列表匹配；保留接口以简化调用）"""
    return ""


def humanize(name):
    return name


# --------------------------------------------------------------------------
# DTO 字段：让 body 参数能被槽位填充
# --------------------------------------------------------------------------
CLASS_FIELD_RE = re.compile(
    r'private\s+([\w.<>\[\], ?]+?)\s+(\w+)\s*;')


def collect_dtos(service_dir):
    """扫 dto/vo 包，建 类名 → 字段列表。支持 'Outer.Inner' 形式"""
    result = {}
    for sub in ("dto", "vo", "entity"):
        d = os.path.join(service_dir, "src", "main", "java")
        if not os.path.isdir(d):
            continue
        for root, _, files in os.walk(d):
            if f"{os.sep}{sub}" not in root + os.sep and not root.endswith(sub):
                continue
            for fn in files:
                if not fn.endswith(".java"):
                    continue
                path = os.path.join(root, fn)
                try:
                    src = io.open(path, encoding="utf-8").read()
                except Exception:
                    continue
                clean, _ = strip_comments(src)
                # 顶层类 + 内部类
                classes = list(re.finditer(r'\bclass\s+(\w+)', clean))
                records = list(re.finditer(r'\brecord\s+(\w+)\s*\(', clean))
                if not classes and not records:
                    continue
                outer = classes[0].group(1) if classes else records[0].group(1)

                # record：字段就是组件列表。SalesRequests.QrBatch 这种就靠它，
                # 否则 POST 类接口的 body 全是空的
                for rm in records:
                    cname = rm.group(1)
                    i, j = match_paren(clean, rm.end() - 1)
                    if i < 0 or j < 0:
                        continue
                    fields = []
                    for comp in split_top(clean[i + 1:j]):
                        comp = comp.strip()
                        while comp.startswith("@"):
                            cm = re.match(r'@[\w.]+', comp)
                            if not cm:
                                break
                            comp = comp[cm.end():].lstrip()
                            if comp.startswith("("):
                                ci, cj = match_paren(comp, 0)
                                if ci < 0 or cj < 0:
                                    break
                                comp = comp[cj + 1:].lstrip()
                        fm = re.match(r'^(?:final\s+)?([\w.<>\[\], ?]+?)\s+(\w+)$', comp)
                        if fm:
                            fields.append({"name": fm.group(2),
                                           "type": re.sub(r"\s+", "", fm.group(1))})
                    if fields:
                        key = cname if cname == outer else f"{outer}.{cname}"
                        result[key] = fields

                for idx, cm in enumerate(classes):
                    cname = cm.group(1)
                    start = cm.end()
                    end = classes[idx + 1].start() if idx + 1 < len(classes) else len(clean)
                    body = clean[start:end]
                    fields = []
                    for fm in CLASS_FIELD_RE.finditer(body):
                        ftype, fname = fm.group(1).strip(), fm.group(2)
                        fields.append({"name": fname, "type": re.sub(r"\s+", "", ftype)})
                    if not fields:
                        continue
                    key = cname if idx == 0 else f"{outer}.{cname}"
                    result[key] = fields
    return result


# --------------------------------------------------------------------------
# 主流程
# --------------------------------------------------------------------------
def scan_service(service):
    svc_dir = os.path.join(BACKEND, service)
    java_root = os.path.join(svc_dir, "src", "main", "java")
    if not os.path.isdir(java_root):
        return [], {}

    dtos = collect_dtos(svc_dir)
    modules, operations = {}, []

    for root, _, files in os.walk(java_root):
        if not os.path.basename(root) == "controller":
            continue
        for fn in files:
            if not fn.endswith(".java"):
                continue
            if not fn.endswith("Controller.java"):
                continue
            path = os.path.join(root, fn)
            src = io.open(path, encoding="utf-8").read()
            clean, javadocs = strip_comments(src)

            info = class_level(clean)
            if not info:
                continue
            class_name, class_start, base_path, tag_name, tag_desc = info

            internal = any(k in class_name.lower() for k in INTERNAL_KEYWORDS)
            mod_id = f"mod:{service}:{tag_name}"
            modules[mod_id] = {
                "id": mod_id,
                "name": tag_name,
                "service": service,
                "description": tag_desc,
                "internal": internal,
                "controller": class_name,
            }

            # 用 javadoc 补摘要：按注解位置找最近的、结束位置在其前的 javadoc
            jd = sorted(javadocs, key=lambda x: x[0])

            for op in parse_methods_with_doc(clean, class_start, base_path, jd):
                gateway_path = SERVICE_PREFIX.get(service, "/api/v1") + op["path"]
                op_id = f"op:{service}:{op['method']}:{op['path']}"
                params = []
                for p in op["params"]:
                    if p["in"] == "body" and p.get("bodyType"):
                        # bodyType 可能是 "SalesRequests.QrBatch"（嵌套 record），
                        # 先按全名查，再退回最后一段
                        bt = p["bodyType"].split("<")[0]
                        fields = dtos.get(bt) or dtos.get(bt.split(".")[-1]) or []
                        p["bodyFields"] = fields
                    params.append(p)
                operations.append({
                    "id": op_id,
                    "service": service,
                    "moduleId": mod_id,
                    "method": op["method"],
                    "path": op["path"],
                    "gatewayPath": gateway_path,
                    "summary": op["summary"],
                    "javaMethod": op["javaMethod"],
                    "returnType": op["returnType"],
                    "internal": internal or is_internal_path(op["path"]),
                    "params": params,
                })

    return list(modules.values()), operations


def parse_methods_with_doc(clean, class_start, base_path, javadocs):
    """parse_methods 的增强版：用 javadoc 首行补摘要。

    定位 javadoc 用注解的字符偏移（_annoPos），不要用方法名去 find ——
    同名片段可能出现在注释或调用处，一错就会把别人的注释挂到当前接口上
    （踩过：distribution 全部接口都被挂成「封批」那一句）。
    """
    ops = parse_methods(clean, class_start, base_path)
    for op in ops:
        if op["summary"] != op["javaMethod"]:
            continue
        pos = op.get("_annoPos", -1)
        if pos < 0:
            continue
        cand = None
        for end, text in javadocs:
            if end <= pos:
                cand = (end, text)
            else:
                break
        if not cand:
            continue
        # 关键校验：注释结束到注解开始之间，除了空白和注解，不能出现 ; { }
        # 否则说明中间隔了别的成员（上一个方法的 javadoc 会被错误地挂到当前方法上）
        between = clean[cand[0]:pos]
        if re.search(r'[;{}]', between):
            continue
        lines = [ln.strip().lstrip("*").strip() for ln in cand[1].splitlines()]
        # 跳过 <p> / @param / 空行，取第一句自然语言
        lines = [ln for ln in lines
                 if ln and not ln.startswith("<") and not ln.startswith("@")]
        for ln in lines:
            first = re.split(r"[。.，,]", ln)[0].strip()
            # 太短的多半是「1.3 性别」这类小标题，继续往下找
            if len(first) >= 3 and not re.match(r"^\d+(\.\d+)*\s", first):
                op["summary"] = first[:40]
                break
    for op in ops:
        op.pop("_annoPos", None)
    return ops


def main():
    all_modules, all_ops = {}, []
    for service in sorted(os.listdir(BACKEND)):
        if not service.endswith("-service"):
            continue
        if service in ("gateway-service",):
            continue
        mods, ops = scan_service(service)
        for m in mods:
            all_modules[m["id"]] = m
        all_ops.extend(ops)
        print(f"{service:24s} 模块 {len(mods):3d}  接口 {len(ops):3d}")

    # 去重（同一 method+path 可能被 @GetMapping({"", "/page"}) 展开成两条，属正常；
    # 真重复说明同一个 path 被两个方法映射，保留第一条并告警）
    seen, uniq = set(), []
    for op in all_ops:
        if op["id"] in seen:
            print(f"  [warn] duplicate {op['id']} @ {op['javaMethod']}", file=sys.stderr)
            continue
        seen.add(op["id"])
        uniq.append(op)

    graph = {
        "version": 1,
        "source": "static-scan",
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "modules": sorted(all_modules.values(), key=lambda x: (x["service"], x["name"])),
        "operations": sorted(uniq, key=lambda x: (x["service"], x["path"], x["method"])),
    }

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with io.open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(graph, f, ensure_ascii=False, indent=1)

    print(f"\n模块 {len(graph['modules'])}  接口 {len(graph['operations'])}")
    print(f"写出 {OUT}  ({os.path.getsize(OUT)/1024:.1f} KB)")


if __name__ == "__main__":
    main()
