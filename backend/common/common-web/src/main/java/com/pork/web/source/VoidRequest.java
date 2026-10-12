package com.pork.web.source;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作废请求体：作废是最后一次改写结论的机会，因此理由必填。
 * <p>
 * 理由会随作废事件一并上链，事后追问「这条合格结论为什么被作废」时，链上答得出来。
 */
@Data
public class VoidRequest {

    /** 作废理由（必填，2-500 字），写入 void_reason 并随作废事件上链 */
    @NotBlank(message = "作废理由不能为空")
    @Size(min = 2, max = 500, message = "作废理由需 2-500 字")
    private String reason;
}
