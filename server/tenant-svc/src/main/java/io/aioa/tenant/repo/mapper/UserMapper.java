package io.aioa.tenant.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aioa.tenant.repo.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    UserEntity findByTenantAndUsername(@Param("tenantId") Long tenantId,
                                      @Param("username") String username);
}
