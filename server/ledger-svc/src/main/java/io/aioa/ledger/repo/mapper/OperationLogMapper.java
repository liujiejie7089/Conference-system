package io.aioa.ledger.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aioa.ledger.repo.entity.OperationLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {
}
