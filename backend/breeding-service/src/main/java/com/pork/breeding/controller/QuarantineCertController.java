package com.pork.breeding.controller;

import com.pork.breeding.dto.QuarantineCertDTO;
import com.pork.breeding.service.QuarantineCertService;
import com.pork.breeding.vo.QuarantineCertVO;
import com.pork.core.result.Result;
import com.pork.web.source.VoidRequest;
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

    @PostMapping("/{id}/void")
    @Operation(summary = "作废产地检疫证明：需填写作废理由，记录保留不物理删除")
    public Result<Void> voidCert(@PathVariable Long pigId, @PathVariable Long id,
                                 @Valid @RequestBody VoidRequest request) {
        quarantineCertService.voidCert(pigId, id, request.getReason());
        return Result.success();
    }
}
