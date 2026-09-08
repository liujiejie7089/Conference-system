package io.aioa.session.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SessionVo {
    private String id;
    private String title;
    private Long agentId;
    private Integer status;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
