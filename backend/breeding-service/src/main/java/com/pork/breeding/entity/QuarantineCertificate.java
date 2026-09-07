package com.pork.breeding.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("quarantine_certificate")
public class QuarantineCertificate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pigId;
    private String certNo;
    private String issueOrg;
    private LocalDateTime issueTime;
    private LocalDate validUntil;
    private String inspector;
    private String certType;
    private String fileId;
    private String caSignature;
    private String contentHash;
    private LocalDateTime createTime;
}