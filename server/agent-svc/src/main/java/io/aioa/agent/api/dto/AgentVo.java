package io.aioa.agent.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentVo {
    private String id;
    private String slug;
    private String name;
    private String avatar;
    private String description;
}
