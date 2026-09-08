package io.aioa.session.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateSessionRequest {
    private Long agentId;
    private String title;
}
