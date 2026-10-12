package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 司机-卡绑定：装车口「刷卡自动录」的落点。
 * <p>
 * 为什么用卡而不是人脸：司机贴一下卡即可，不用下车、不用停留、不受逆光/夜间/口罩影响，
 * 终端也最便宜；卡在人手上自带最低限度的责任。人脸降级为可选的强化档。
 * <p>
 * 卡挂失（{@code status=0}）不影响历史运单的追溯 —— 运单里存的是**人名**，不是卡号。
 */
@Data
@TableName("driver_card")
public class DriverCard {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** IC 卡号（唯一） */
    private String cardNo;
    /** 绑定的司机 id */
    private Long driverId;
    /** 1有效 0挂失/作废 */
    private Integer status;
    private String remark;
}
