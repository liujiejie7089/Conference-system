package io.aioa.ledger.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aioa.ledger.repo.entity.QuotaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QuotaMapper extends BaseMapper<QuotaEntity> {

    /**
     * 原子扣减额度
     * @return 1 成功，0 余额不足
     */
    @Update("UPDATE quotas SET used_quota = used_quota + #{amount} " +
            "WHERE tenant_id = #{tenantId} AND user_id = #{userId} " +
            "AND total_quota - used_quota >= #{amount}")
    int consume(@Param("tenantId") Long tenantId,
                @Param("userId") Long userId,
                @Param("amount") Long amount);
}
