package com.pork.breeding.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class PigCreateDTO {

    @NotBlank(message = "耳标号不能为空")
    private String earTagNo;

    @NotNull(message = "养殖场ID不能为空")
    private Long farmId;

    private String breed;

    private LocalDate birthDate;

    private String penNo;

    private String source;
}