#!/usr/bin/env bash
# 胴体盖章「填充示例值」回归的落库核验与清理（自带 cjs 驱动）。
#
# 用法：bash logs/verify-stamp-demo-db.sh
#   1) 跑 logs/verify-stamp-demo.cjs（浏览器 + 接口：横幅已清空 / 示例值挑未盖章白条 /
#      胴体编号带时刻 / 真上报确实多写一条 / 同猪换号重报转人工）
#   2) 在「数据还在库里」的时候核验落库事实：新盖章记录确实存在且绑定到挑中的耳标、
#      冲突上报**没有**多写任何记录、接入流水里两条上报都在
#   3) 清理本次产生的那一条盖章记录（含上链事件与两条接入流水），并核对数目回到基线
#
# 注：`db_common.blockchain_ledger`（链本身）**不动** —— 与既有清理脚本同一口径：
#   能删的是「业务侧的上链记录」`blockchain_record`，链上账本是追加型的，删一条等于毁链。
#
# ⚠️ 一次性脚本造的记录必须物理删掉，不能用「作废」：
#   `carcass_stamp` 上 `pig_id` 是唯一键，作废只是把 `void_flag` 置 1、行还在 ——
#   用「作废」清理等于把这头白条永久钉成「已有一枚作废的章」，以后再也盖不了。
#   （回归要留痕的场合才用作废；这里清的是自己刚造的测试数据。）
#
# 断言口径：只认真实落库的事实（条数、外键存在性、编号），不去断言接口文案。
set -u

ROOT=/e/pork-traceability
NODE="C:/Users/17875/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }
DUMP() { docker exec pork-traceability-mysql-1 mysqldump -uroot -pchange-this-root-password --default-character-set=utf8mb4 "$@" 2>&1 | grep -v "Using a password"; }
STAMP_DEV=STAMPER-001

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
no() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1 —— $2"; }
eq() { if [ "$2" = "$3" ]; then ok "$1（$2）"; else no "$1" "期望 $3，实际 $2"; fi }

echo "=== 胴体盖章示例值回归：落库核验与清理 ==="

START_TS=$(MYSQL "SELECT NOW();" | head -1)
BEFORE_STAMP=$(MYSQL "SELECT COUNT(*) FROM db_slaughter.carcass_stamp;")
BEFORE_CHAIN=$(MYSQL "SELECT IFNULL(MAX(id),0) FROM db_common.blockchain_record;")
BEFORE_STG=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE channel='STAMP' AND device_no='${STAMP_DEV}';")
echo "  水位：盖章记录 ${BEFORE_STAMP} / 上链最大 id ${BEFORE_CHAIN} / 盖章机暂存行 ${BEFORE_STG}"
echo "  测试起点 ${START_TS}"

# ── 1) 驱动浏览器 + 接口核验，并抓出清理用 ID ──
OUT=${ROOT}/logs/.verify-stamp-demo.out
"$NODE" "${ROOT}/logs/verify-stamp-demo.cjs" 2>&1 | tee "$OUT"
CJS_RC=${PIPESTATUS[0]}
if [ "${CJS_RC}" != "0" ]; then no "浏览器 + 接口核验" "退出码 ${CJS_RC}（见上方 FAIL）"; else ok "浏览器 + 接口核验全通过"; fi

grab() { grep -m1 "^#$1=" "$OUT" | cut -d= -f2- | tr -d '\r' ; }
STAMPID=$(grab STAMPID)
PIGID=$(grab PIGID)
CARCASSNO=$(grab CARCASSNO)
SAFENO=$(grab SAFENO)
BIZKEY_SUCCESS=$(grab BIZKEY_SUCCESS)
BIZKEY_CONFLICT=$(grab BIZKEY_CONFLICT)
SINCE=$(grab SINCE)
SINCE=${SINCE:-${START_TS}}
echo "  本次造物：盖章记录 id=${STAMPID:-（无）} / 猪 ${PIGID:-（无）} / 编号 ${CARCASSNO:-（无）}"
echo "           冲突编号 ${SAFENO:-（无）} / 起点 ${SINCE}"

# ── 2) 落库核验（趁数据还在）──
echo
echo "── 2) 落库事实核验 ──"
if [ -n "${STAMPID}" ] && [ -n "${PIGID}" ]; then
  ROW=$(MYSQL "SELECT CONCAT(pig_id,'|',carcass_no,'|',IFNULL(batch_no,''),'|',IFNULL(veterinary,''),'|',source,'|',void_flag) FROM db_slaughter.carcass_stamp WHERE id=${STAMPID};")
  echo "  新记录 = ${ROW}"
  eq "新盖章记录绑定到挑中的生猪" "$(echo "$ROW" | cut -d'|' -f1)" "${PIGID}"
  eq "新盖章记录的胴体编号就是示例值给的那一个" "$(echo "$ROW" | cut -d'|' -f2)" "${CARCASSNO}"
  eq "新盖章记录来源是设备（DEVICE）" "$(echo "$ROW" | cut -d'|' -f5)" "DEVICE"
  eq "新盖章记录未被作废" "$(echo "$ROW" | cut -d'|' -f6)" "0"
else
  no "新盖章记录存在" "脚本没打出 #STAMPID（上方应已 FAIL）"
fi
eq "盖章表只多了 1 条（冲突上报没有多写）" "$(MYSQL "SELECT COUNT(*) FROM db_slaughter.carcass_stamp;")" "$((BEFORE_STAMP + 1))"
eq "冲突上报用的编号没有落成记录" "$(MYSQL "SELECT COUNT(*) FROM db_slaughter.carcass_stamp WHERE carcass_no='${SAFENO:-__none__}';")" "0"
eq "同一头白条仍然只有一枚章（pig_id 唯一键没被绕开）" "$(MYSQL "SELECT COUNT(*) FROM db_slaughter.carcass_stamp WHERE pig_id=${PIGID:-0};")" "1"

CHAIN_ROW=$(MYSQL "SELECT COUNT(*) FROM db_common.blockchain_record WHERE biz_type='SLAUGHTER_INSPECT' AND biz_id=${STAMPID:-0} AND id>${BEFORE_CHAIN};")
eq "新盖章记录上链了（SLAUGHTER_INSPECT）" "${CHAIN_ROW}" "1"
STG_NEW=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE channel='STAMP' AND device_no='${STAMP_DEV}' AND receive_time>='${SINCE}';")
eq "接入流水留下两条上报（成功 + 冲突）" "${STG_NEW}" "2"

# ── 3) 清理（先备份再删，只删本次自己造的）──
echo
echo "── 3) 清理本次造物 ──"
mkdir -p "${ROOT}/logs/backup"
TS=$(date +%Y%m%d-%H%M%S)
BK="${ROOT}/logs/backup/stamp-demo-${TS}.sql"
if [ -n "${STAMPID}" ]; then
  { echo "-- 盖章示例值回归自造记录 ${TS}"; \
    DUMP db_slaughter carcass_stamp --where="id=${STAMPID}"; \
    DUMP db_ingest ingest_staging --where="channel='STAMP' AND device_no='${STAMP_DEV}' AND receive_time>='${SINCE}'"; \
    DUMP db_common blockchain_record --where="biz_type='SLAUGHTER_INSPECT' AND biz_id=${STAMPID}"; \
  } > "$BK" || true
  echo "  已备份到 ${BK}（$(grep -c '^INSERT' "$BK" || true) 条 INSERT）"

  MYSQL "DELETE FROM db_common.blockchain_record WHERE biz_type='SLAUGHTER_INSPECT' AND biz_id=${STAMPID};" >/dev/null
  MYSQL "DELETE FROM db_slaughter.carcass_stamp WHERE id=${STAMPID};" >/dev/null
  [ -n "${CARCASSNO}" ] && MYSQL "DELETE FROM db_slaughter.carcass_stamp WHERE carcass_no IN ('${CARCASSNO}','${SAFENO}');" >/dev/null
fi
MYSQL "DELETE FROM db_ingest.ingest_staging WHERE channel='STAMP' AND device_no='${STAMP_DEV}' AND receive_time>='${SINCE}';" >/dev/null
if [ -n "${BIZKEY_SUCCESS}" ] && [ -n "${BIZKEY_CONFLICT}" ]; then
  MYSQL "DELETE FROM db_ingest.ingest_staging WHERE channel='STAMP' AND biz_key IN ('${BIZKEY_SUCCESS}','${BIZKEY_CONFLICT}');" >/dev/null
fi

eq "盖章记录数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_slaughter.carcass_stamp;")" "${BEFORE_STAMP}"
eq "自造的那条盖章记录已不存在" "$(MYSQL "SELECT COUNT(*) FROM db_slaughter.carcass_stamp WHERE id=${STAMPID:-0};")" "0"
eq "示例值给的新编号在库里已无痕" "$(MYSQL "SELECT COUNT(*) FROM db_slaughter.carcass_stamp WHERE carcass_no IN ('${CARCASSNO:-__none__}','${SAFENO:-__none__}');")" "0"
eq "本次上链事件已清" "$(MYSQL "SELECT COUNT(*) FROM db_common.blockchain_record WHERE biz_type='SLAUGHTER_INSPECT' AND biz_id=${STAMPID:-0};")" "0"
eq "本次接入流水已清" "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE channel='STAMP' AND device_no='${STAMP_DEV}' AND receive_time>='${SINCE}';")" "0"

echo
echo "结果：通过 ${PASS} 项，失败 ${FAIL} 项"
exit $([ "${FAIL}" = "0" ] && echo 0 || echo 1)
