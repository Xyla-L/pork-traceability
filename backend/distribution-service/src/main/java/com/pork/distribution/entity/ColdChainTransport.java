package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cold_chain_transport")
public class ColdChainTransport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String transportNo;
    private Long splitBatchId;
    private String vehicleNo;
    private String vehicleType;
    private String refrigeration;
    private String driverName;
    private String driverPhone;
    private String origin;
    private String destination;
    private LocalDateTime plannedDepart;
    private LocalDateTime plannedArrive;
    private LocalDateTime departTime;
    private LocalDateTime arriveTime;
    private Integer status;
    private LocalDateTime createTime;
}
