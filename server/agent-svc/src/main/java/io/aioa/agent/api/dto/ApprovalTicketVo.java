package io.aioa.agent.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApprovalTicketVo {
    private Long id;
    private String idStr;
    private Long sessionId;
    private Long messageId;
    private Long toolId;
    private String toolCallId;
    private String inputPayload;
    private Integer status;  // 1=pending 2=approved 3=rejected 4=expired
    private String statusText;
    private Long approverId;
    private String opinion;
    private LocalDateTime approvedAt;
    private LocalDateTime expiredAt;
    private LocalDateTime createdAt;
}
