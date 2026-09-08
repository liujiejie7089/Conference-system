package io.aioa.ledger.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aioa.ledger.repo.entity.TokenUsageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TokenUsageMapper extends BaseMapper<TokenUsageEntity> {
}
