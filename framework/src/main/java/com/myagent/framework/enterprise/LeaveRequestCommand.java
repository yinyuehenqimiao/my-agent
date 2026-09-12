package com.myagent.framework.enterprise;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;

/**
 * 请假创建命令（企业系统端口 DTO）。
 * <p>
 * ERP 写接口强制携带幂等键与 traceId，防止重复扣减。
 */
@Data
@Builder
public class LeaveRequestCommand {

    @NotBlank private String leaveType;       // ANNUAL / PERSONAL
    @NotBlank private String startDate;        // YYYY-MM-DD
    @NotBlank private String endDate;          // YYYY-MM-DD
    @NotBlank private String reason;
    /** 幂等键（由调用端唯一生成，重试复用） */
    @NotBlank private String idempotencyKey;
    /** 追踪号 */
    @NotBlank private String traceId;
    /** 会话 ID（用于关联确认卡片） */
    @Pattern(regexp = "[A-Za-z0-9_\\-]+") private String conversationId;
}
