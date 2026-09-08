package com.pork.breeding.controller;

import com.pork.breeding.dto.QuarantineCertDTO;
import com.pork.breeding.service.QuarantineCertService;
import com.pork.breeding.vo.QuarantineCertVO;
import com.pork.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pigs/{pigId}/quarantine-cert")
@Tag(name = "产地检疫证明管理", description = "提供产地检疫证明的上传和查询功能")
@RequiredArgsConstructor
public class QuarantineCertController {

    private final QuarantineCertService quarantineCertService;

    @PostMapping
    @Operation(summary = "上传产地检疫证明")
    public Result<Void> upload(@PathVariable Long pigId, @Valid @RequestBody QuarantineCertDTO dto) {
        dto.setPigId(pigId);
        quarantineCertService.uploadCert(dto);
        return Result.success();
    }

    @GetMapping
    @Operation(summary = "查看产地检疫证明")
    public Result<QuarantineCertVO> getCert(@PathVariable Long pigId) {
        QuarantineCertVO vo = quarantineCertService.getCertByPigId(pigId);
        return Result.success(vo);
    }
}
