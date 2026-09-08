package io.aioa.session.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息视图对象（含引用溯源）
 */
@Data
@Builder
public class MessageVo {
    private String id;          // idStr
    private Integer role;       // 1=user 2=assistant
    private String content;
    private Long tokenOutput;
    private LocalDateTime createdAt;
    private List<CitationVo> citations;
}
