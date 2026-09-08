package io.aioa.session.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 引用溯源视图对象
 * source_type: 1=知识库 2=Tool结果 3=外部链接
 */
@Data
@Builder
public class CitationVo {
    private String id;
    private Integer sourceType;
    private String title;
    private String url;
    private String snippet;
}
