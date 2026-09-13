package com.myagent.enterprise.dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Public enterprise contracts. Identity is supplied by the server, never by an LLM. */
public final class EnterpriseTypes {
    private EnterpriseTypes() { }
    public record Employee(String userId, String displayName, String department, String businessRole,
                           String managerId, String hrId, boolean active) { }
    public record Account(String userId, int year, int total, int frozen, int used) {
        public int getAvailable() { return total - frozen - used; }
    }
    public record LeaveRequest(String id, String userId, String leaveType, @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate startDate, @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate endDate,
                               int days, String reason, String status, String managerId, String hrId,
                               @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDateTime createdAt, @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDateTime updatedAt) { }
    public record ApprovalTask(String id, String requestId, String assigneeId, String stage, String status) { }
    public record Audit(String id, String requestId, String actorId, String actionId, String event,
                        String comment, @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDateTime createdAt) { }
    public record Detail(LeaveRequest request, List<ApprovalTask> tasks, List<Audit> timeline) { }
    public record Action(String id, String userId, String conversationId, String kind, String requestId,
                         String payload, String status, String result, @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDateTime expiresAt,
                         @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDateTime createdAt, String dedupKey) { }
    public record LeaveInput(@NotBlank String conversationId, @NotBlank String requestKey,
                             @Pattern(regexp="ANNUAL|PERSONAL") @NotNull String leaveType,
                             @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                             @NotBlank @Size(max=1000) String reason) { }
    public record DecisionInput(@NotBlank String conversationId, @NotBlank String requestKey,
                                @Pattern(regexp="APPROVE|REJECT") @NotNull String decision,
                                @Size(max=1000) String comment) { }
    public record WithdrawInput(@NotBlank String conversationId, @NotBlank String requestKey) { }
    public record Grant(@Pattern(regexp="ALL|DEPARTMENT|ROLE") @NotNull String audience,
                        @NotBlank @Size(max=100) String subject) { }
    public record Policy(@NotBlank @Size(max=64) String version, @NotNull LocalDate effectiveDate) { }
    public record Workday(@JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate day, boolean working, String description) { }
    public record ErpConnectionStatus(String mode, boolean configured, String summary, String nextStep) { }
}
