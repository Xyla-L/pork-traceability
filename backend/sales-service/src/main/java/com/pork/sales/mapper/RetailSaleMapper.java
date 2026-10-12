package com.pork.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.sales.entity.RetailSale;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RetailSaleMapper extends BaseMapper<RetailSale> {

    /**
     * 查已作废的商品（仅供追溯侧标注使用）。
     * <p>
     * {@code @TableLogic} 会把作废商品从常规查询里滤掉——这是对的，但消费者扫码时
     * 「这个码查不到」和「这件商品已作废」是两回事：前者像是系统坏了，
     * 后者才说得清上游生猪/批次出了什么问题。所以留一个显式绕过逻辑删除的只读入口。
     * 返回 null 表示该二维码既没有有效商品、也没有作废商品。
     */
    @Select("SELECT id, product_qr_code AS productQrCode, split_batch_id AS splitBatchId, "
            + "store_id AS storeId, store_name AS storeName, status, "
            + "void_reason AS voidReason, void_operator AS voidOperator, void_time AS voidTime "
            + "FROM retail_sale WHERE product_qr_code = #{qrCode} AND void_flag = 1 LIMIT 1")
    RetailSale selectVoidedByQr(@Param("qrCode") String qrCode);
}
