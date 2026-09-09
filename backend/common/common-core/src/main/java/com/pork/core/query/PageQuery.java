package com.pork.core.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** Common, bounded pagination input for public list endpoints. */
@Data
public class PageQuery {

    @Min(value = 1, message = "pageNum must be greater than 0")
    private long pageNum = 1;

    @Min(value = 1, message = "pageSize must be greater than 0")
    @Max(value = 200, message = "pageSize must not exceed 200")
    private long pageSize = 20;

    private String sortField = "create_time";
    private String sortOrder = "desc";
}
