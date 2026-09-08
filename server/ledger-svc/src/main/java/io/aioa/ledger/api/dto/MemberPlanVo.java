package io.aioa.ledger.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class MemberPlanVo {
    private Integer currentLevel;
    private String currentLevelName;
    /** 词元包列表 */
    private List<Map<String, Object>> tokenPacks;
    /** 会员套餐列表 */
    private List<Map<String, Object>> memberPlans;
}
