package com.pork.ai.graph.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 图谱里的「模块」节点，对应一个 Swagger Tag（没有 Tag 时退回 Controller 类名）。
 *
 * <p>模块的用处不是展示，而是<b>缩小检索空间</b>：
 * 「导出退货单」这种问法里的「退货单」在 sales-service 和 trace-service 都可能出现，
 * 先按模块收敛，再在模块内排序，比全库平铺打分的准确率高一个档。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiModule(
        String id,
        String name,
        String service,
        String description,
        boolean internal,
        String controller
) {
}
