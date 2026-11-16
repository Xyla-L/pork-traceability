package com.pork.breeding.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.entity.QuarantineCertPig;
import com.pork.breeding.entity.QuarantineCertificate;
import com.pork.breeding.mapper.QuarantineCertMapper;
import com.pork.breeding.mapper.QuarantineCertPigMapper;
import com.pork.breeding.service.PigIndividualService;
import com.pork.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产地检疫证核验（内部接口，仅服务间调用）
 * <p>
 * 供入场查验通道（门禁 / 检疫证核验终端）自动核验使用：
 * 设备只拿到一个检疫证号，需要确认「证是真的、且证上这头猪就是闸口这头猪、且没过期」。
 * 返回 valid=false 并带 reason，由接入层决定是拦下还是转人工，而不是在这里抛异常。
 */
@RestController
@RequestMapping("/breeding/internal/quarantine-certs")
@RequiredArgsConstructor
public class QuarantineCertInternalController {

    private final QuarantineCertMapper certMapper;
    private final QuarantineCertPigMapper certPigMapper;
    private final PigIndividualService pigIndividualService;

    @GetMapping("/verify")
    public Result<Map<String, Object>> verify(@RequestParam(required = false) String certNo,
                                              @RequestParam(required = false) String earTagNo) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("certNo", certNo);
        data.put("earTagNo", earTagNo);
        data.put("valid", false);

        if (certNo == null || certNo.isBlank()) {
            data.put("reason", "未提供检疫证号");
            return Result.success(data);
        }

        QuarantineCertificate cert = certMapper.selectOne(Wrappers.<QuarantineCertificate>lambdaQuery()
                .eq(QuarantineCertificate::getCertNo, certNo));
        if (cert == null) {
            data.put("reason", "检疫证号在产地检疫证明库中不存在");
            return Result.success(data);
        }

        data.put("certId", cert.getId());
        data.put("certPigId", cert.getPigId());
        data.put("issueOrg", cert.getIssueOrg());
        data.put("issueTime", cert.getIssueTime());
        data.put("validUntil", cert.getValidUntil());

        // 证覆盖的猪：一张证可覆盖多头（一证多头），成员在 quarantine_cert_pig。
        // 闸口扫到的耳标只要在这张证的名单里就算一致——这才是「证与猪对得上」的真实含义。
        List<QuarantineCertPig> members = certPigMapper.selectList(
                Wrappers.<QuarantineCertPig>lambdaQuery().eq(QuarantineCertPig::getCertId, cert.getId()));
        List<String> certEarTags = members.stream().map(QuarantineCertPig::getEarTagNo).toList();
        data.put("certEarTagNos", certEarTags);
        data.put("pigCount", members.size());
        data.put("certEarTagNo", certEarTags.isEmpty() ? null : certEarTags.get(0));

        if (cert.getValidUntil() != null && cert.getValidUntil().isBefore(LocalDate.now())) {
            data.put("reason", "检疫证已过有效期 " + cert.getValidUntil());
            return Result.success(data);
        }

        if (earTagNo != null && !earTagNo.isBlank()) {
            if (!certEarTags.contains(earTagNo)) {
                data.put("reason", "检疫证覆盖的生猪名单里没有闸口扫描的耳标 " + earTagNo);
                return Result.success(data);
            }
        }

        data.put("valid", true);
        data.put("reason", "核验通过");
        return Result.success(data);
    }
}
