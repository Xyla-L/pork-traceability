package com.pork.slaughter.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ractopamine_test")
public class RactopamineTest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pigId;
    private LocalDateTime testTime;
    private String testMethod;
    private String testTarget;
    private String samplePart;
    private Integer result; // 1阴性 0阳性
    private String detectionLimit;
    private String operator;
    private String fileIds;
    private LocalDateTime createTime;
}