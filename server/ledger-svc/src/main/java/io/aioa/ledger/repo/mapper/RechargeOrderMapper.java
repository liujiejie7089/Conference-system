package io.aioa.ledger.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aioa.ledger.repo.entity.RechargeOrderEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RechargeOrderMapper extends BaseMapper<RechargeOrderEntity> {
}
