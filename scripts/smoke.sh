#!/usr/bin/env bash
# ============================================================================
# 端到端冒烟：登录 → 取令牌 → 提交 → 分派 → 接单 → 完成 → 验收 → 时间线
# 额外验证：同一个幂等令牌重复提交必须被拦下
#
# 用法：bash scripts/smoke.sh [http://localhost:8080]
# 前置：应用已启动（默认账号由 AdminInitializer 创建，密码统一 123456）
# ============================================================================
set -uo pipefail

BASE="${1:-http://localhost:8080}/api/v1"
PASS=0
FAIL=0

ok()   { PASS=$((PASS+1)); printf '  [OK]   %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '  [FAIL] %s\n' "$1"; }

# 极简 JSON 取值（避免依赖 jq）
sval() { printf '%s' "$2" | grep -o "\"$1\":\"[^\"]*\"" | head -1 | sed 's/.*:"//; s/"$//'; }
nval() { printf '%s' "$2" | grep -o "\"$1\":[0-9-]*" | head -1 | sed 's/.*://'; }

login() {
  curl -s -X POST "$BASE/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"123456\"}"
}

echo "=== 0. 服务连通性 ==="
me=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/auth/me")
if [ "$me" = "200" ]; then
  ok "服务已启动（$BASE）"
else
  bad "服务未响应（HTTP $me），请先执行 mvn spring-boot:run"
  exit 1
fi

echo "=== 1. 登录 ==="
r_emp=$(login employee2)
TOKEN_EMP=$(sval token "$r_emp")
EMP_ID=$(nval userId "$r_emp")
[ -n "$TOKEN_EMP" ] && ok "employee2(赵四, 信息技术部) 登录" || { bad "employee2 登录: $r_emp"; exit 1; }

r_dis=$(login dispatcher)
TOKEN_DIS=$(sval token "$r_dis")
[ -n "$TOKEN_DIS" ] && ok "dispatcher(王调度) 登录" || { bad "dispatcher 登录: $r_dis"; exit 1; }

r_hdl=$(login handler)
TOKEN_HDL=$(sval token "$r_hdl")
HDL_ID=$(nval userId "$r_hdl")
[ -n "$TOKEN_HDL" ] && ok "handler(李工) 登录，userId=$HDL_ID" || { bad "handler 登录: $r_hdl"; exit 1; }

r_adm=$(login admin)
TOKEN_ADM=$(sval token "$r_adm")
[ -n "$TOKEN_ADM" ] && ok "admin 登录" || { bad "admin 登录: $r_adm"; exit 1; }

echo "=== 2. 提交工单（幂等令牌） ==="
tok_resp=$(curl -s "$BASE/idempotent-token" -H "Authorization: Bearer $TOKEN_EMP")
IDEM=$(sval token "$tok_resp")
[ -n "$IDEM" ] && ok "取到幂等令牌 ${IDEM:0:8}..." || { bad "取幂等令牌: $tok_resp"; exit 1; }

BODY='{"title":"三楼打印机卡纸","content":"连续卡纸三次，无法正常打印，影响部门报表输出。","typeId":4,"priority":2}'
create_resp=$(curl -s -X POST "$BASE/tickets" \
  -H "Authorization: Bearer $TOKEN_EMP" \
  -H 'Content-Type: application/json' \
  -H "X-Idempotent-Token: $IDEM" \
  -d "$BODY")
TID=$(nval id "$create_resp")
TNO=$(sval ticketNo "$create_resp")
if [ -n "$TID" ]; then
  ok "创建成功 id=$TID no=$TNO"
else
  bad "创建失败: $create_resp"; exit 1
fi

echo "=== 3. 幂等校验（同一令牌重复提交） ==="
dup_resp=$(curl -s -X POST "$BASE/tickets" \
  -H "Authorization: Bearer $TOKEN_EMP" \
  -H 'Content-Type: application/json' \
  -H "X-Idempotent-Token: $IDEM" \
  -d "$BODY")
dup_code=$(nval code "$dup_resp")
if [ "$dup_code" = "30002" ]; then
  ok "重复提交被拦截（code=30002）"
else
  bad "重复提交未被拦截: $dup_resp"
fi

echo "=== 4. 缺少令牌应被拒绝 ==="
no_tok=$(curl -s -X POST "$BASE/tickets" \
  -H "Authorization: Bearer $TOKEN_EMP" \
  -H 'Content-Type: application/json' -d "$BODY")
no_tok_code=$(nval code "$no_tok")
[ "$no_tok_code" = "10001" ] && ok "缺令牌返回 10001" || bad "缺令牌返回 $no_tok_code（期望 10001）"

echo "=== 5. 分派（调度 → 李工） ==="
assign_resp=$(curl -s -X PUT "$BASE/tickets/$TID/assign" \
  -H "Authorization: Bearer $TOKEN_DIS" \
  -H 'Content-Type: application/json' \
  -d "{\"assigneeId\":$HDL_ID,\"remark\":\"按负载分派\"}")
[ "$(nval code "$assign_resp")" = "0" ] && ok "分派成功" || bad "分派失败: $assign_resp"

echo "=== 6. 员工无权分派（垂直越权防护） ==="
deny_resp=$(curl -s -X PUT "$BASE/tickets/$TID/accept" -H "Authorization: Bearer $TOKEN_EMP")
deny_code=$(nval code "$deny_resp")
if [ "$deny_code" = "20003" ] || [ "$deny_code" = "30001" ]; then
  ok "员工无法接单（code=$deny_code）"
else
  bad "越权未被拦截: $deny_resp"
fi

echo "=== 7. 接单 → 完成（处理人） ==="
acc_resp=$(curl -s -X PUT "$BASE/tickets/$TID/accept" -H "Authorization: Bearer $TOKEN_HDL")
[ "$(nval code "$acc_resp")" = "0" ] && ok "接单成功" || bad "接单失败: $acc_resp"

fin_resp=$(curl -s -X PUT "$BASE/tickets/$TID/finish" \
  -H "Authorization: Bearer $TOKEN_HDL" \
  -H 'Content-Type: application/json' \
  -d '{"remark":"已更换搓纸轮，测试连续打印 50 页正常"}')
[ "$(nval code "$fin_resp")" = "0" ] && ok "提交完成" || bad "提交完成失败: $fin_resp"

echo "=== 8. 验收（提交人） ==="
ver_resp=$(curl -s -X PUT "$BASE/tickets/$TID/verify" \
  -H "Authorization: Bearer $TOKEN_EMP" \
  -H 'Content-Type: application/json' \
  -d '{"pass":true,"remark":"确认恢复正常"}')
[ "$(nval code "$ver_resp")" = "0" ] && ok "验收通过" || bad "验收失败: $ver_resp"

echo "=== 9. 详情与时间线 ==="
detail=$(curl -s "$BASE/tickets/$TID" -H "Authorization: Bearer $TOKEN_EMP")
st=$(nval status "$detail")
[ "$st" = "50" ] && ok "最终状态 = 50 已关闭" || bad "最终状态 = $st（期望 50）"

flow_cnt=$(printf '%s' "$detail" | grep -o '"action":"[A-Z_]*"' | wc -l | tr -d ' ')
if [ "$flow_cnt" = "5" ]; then
  ok "流转日志 5 条（SUBMIT/ASSIGN/ACCEPT/FINISH/VERIFY_PASS）"
else
  bad "流转日志 $flow_cnt 条（期望 5）"
fi

echo "=== 10. 状态机防护（对已关闭工单再接单） ==="
bad_trans=$(curl -s -X PUT "$BASE/tickets/$TID/accept" -H "Authorization: Bearer $TOKEN_HDL")
bt_code=$(nval code "$bad_trans")
[ "$bt_code" = "30001" ] && ok "非法流转被拒绝（30001）" || bad "非法流转返回 $bt_code（期望 30001）"

echo "=== 11. 数据权限（李工看不到别人的单） ==="
other_resp=$(curl -s "$BASE/tickets?tab=dept&pageSize=5" -H "Authorization: Bearer $TOKEN_HDL")
other_total=$(nval total "$other_resp")
ok "李工 dept 视图 total=${other_total:-null}（数据权限为本人，仅含派给自己的）"

echo "=== 12. 统计（管理员） ==="
stat_resp=$(curl -s "$BASE/stats/overview" -H "Authorization: Bearer $TOKEN_ADM")
total=$(nval total "$stat_resp")
[ -n "$total" ] && ok "统计概览 total=$total" || bad "统计失败: $stat_resp"

echo
echo "----------------------------------------"
printf '通过 %d 项，失败 %d 项\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] && echo "冒烟通过" || echo "存在失败项，请检查上方输出"
exit $([ "$FAIL" -eq 0 ] && echo 0 || echo 1)
