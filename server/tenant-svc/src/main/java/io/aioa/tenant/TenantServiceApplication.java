package io.aioa.tenant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 用户与租户服务入口
 */
@SpringBootApplication
@ComponentScan(basePackages = {"io.aioa.tenant", "io.aioa.common"})
@MapperScan("io.aioa.tenant.repo.mapper")
public class TenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenantServiceApplication.class, args);
    }
}
