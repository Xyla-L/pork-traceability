#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把各业务服务的 voidXxx 从「只校验理由」升级到「与新增同级受控」。

替换规则（每个文件内）：
  1. `String cleanReason = sourceGuard.requireVoidReason(reason);`
     -> `ManualOperator voidBy = sourceGuard.requireVoidAllowed(TraceStage.<X>, reason);`
        `String cleanReason = reason.trim();`
  2. 该行之后第一次出现的 `String operator = sourceGuard.currentOperator().display();`
     -> `String operator = voidBy.display();`
"""
import pathlib
import re
import sys

ROOT = pathlib.Path("E:/pork-traceability/backend")

TARGETS = {
    "slaughter-service/src/main/java/com/pork/slaughter/service/impl/EntryInspectionServiceImpl.java": "ENTRY",
    "slaughter-service/src/main/java/com/pork/slaughter/service/impl/RactopamineTestServiceImpl.java": "RACTOPAMINE",
    "slaughter-service/src/main/java/com/pork/slaughter/service/impl/SlaughterInspectionServiceImpl.java": "INSPECTION",
    "slaughter-service/src/main/java/com/pork/slaughter/service/impl/CarcassStampServiceImpl.java": "STAMP",
    "breeding-service/src/main/java/com/pork/breeding/service/impl/PigIndividualServiceImpl.java": "TAG",
    "breeding-service/src/main/java/com/pork/breeding/service/impl/VaccineRecordServiceImpl.java": "VACCINE",
    "breeding-service/src/main/java/com/pork/breeding/service/impl/QuarantineCertServiceImpl.java": "QUARANTINE",
    "distribution-service/src/main/java/com/pork/distribution/service/impl/DistributionServiceImpl.java": "SPLIT",
}

OLD_REASON = "        String cleanReason = sourceGuard.requireVoidReason(reason);\n"
OLD_OPERATOR = "        String operator = sourceGuard.currentOperator().display();\n"
NEW_OPERATOR = "        String operator = voidBy.display();\n"

failed = False
for rel, stage in TARGETS.items():
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    if OLD_REASON not in text:
        print(f"[SKIP] {rel}: 未找到待替换的理由校验行")
        failed = True
        continue
    new_reason = (
        f"        // 作废与新增同级受控：实名 + 理由 + 票据（设备优先环节）+ 上链，防止「用作废绕开管控」\n"
        f"        ManualOperator voidBy = sourceGuard.requireVoidAllowed(TraceStage.{stage}, reason);\n"
        f"        String cleanReason = reason.trim();\n"
    )
    idx = text.index(OLD_REASON)
    text = text[:idx] + new_reason + text[idx + len(OLD_REASON):]
    # 在替换点之后找 operator 赋值（即同一个作废方法内的那一处）
    op_idx = text.find(OLD_OPERATOR, idx)
    if op_idx < 0:
        print(f"[SKIP] {rel}: 作废方法内未找到 operator 赋值行")
        failed = True
        continue
    text = text[:op_idx] + NEW_OPERATOR + text[op_idx + len(OLD_OPERATOR):]
    path.write_text(text, encoding="utf-8")
    print(f"[OK]   {rel} -> TraceStage.{stage}")

sys.exit(1 if failed else 0)
