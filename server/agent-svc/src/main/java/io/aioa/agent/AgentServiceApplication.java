package io.aioa.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Agent 服务入口
 */
@SpringBootApplication
@ComponentScan(basePackages = {"io.aioa.agent", "io.aioa.common"})
@MapperScan("io.aioa.agent.repo.mapper")
public class AgentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentServiceApplication.class, args);
    }
}
