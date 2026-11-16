package com.pork.breeding.controller;

import com.pork.breeding.dto.QuarantineCertDTO;
import com.pork.breeding.service.QuarantineCertService;
import com.pork.breeding.support.FarmScope;
import com.pork.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 产地检疫「一证多头」出证入口。
 * <p>
 * 与 {@code /pigs/{pigId}/quarantine-cert} 并存：单头出证是旧入口（设备覆盖不到时的补录，
 * 一条一头），这里是「一张证覆盖一车猪」的主路径。两者写的是同一张单据表，
 * 唯一的差别是覆盖头数——校验口径完全一致（每头都要过已屠宰 + 免疫档案两关）。
 */
@RestController
@RequestMapping("/quarantine-certs")
@Tag(name = "产地检疫出证（一证多头）", description = "一张检疫证覆盖多头生猪")
@RequiredArgsConstructor
public class QuarantineCertBatchController {

    private final QuarantineCertService quarantineCertService;
    private final FarmScope farmScope;

    @PostMapping
    @Operation(summary = "出证：一张证覆盖多头（pigIds）")
    public Result<Void> upload(@Valid @RequestBody QuarantineCertDTO dto) {
        // 机构隔离：逐头核对归属，不能给别家的猪出证
        if (dto.getPigIds() != null) {
            for (Long pigId : dto.getPigIds()) {
                if (pigId != null) farmScope.requirePigOfFarm(pigId, "该产地检疫证");
            }
        }
        quarantineCertService.uploadCert(dto);
        return Result.success();
    }
}
