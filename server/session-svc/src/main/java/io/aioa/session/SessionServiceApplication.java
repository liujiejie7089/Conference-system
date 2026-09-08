package io.aioa.session;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 会话服务入口
 */
@SpringBootApplication
@ComponentScan(basePackages = {"io.aioa.session", "io.aioa.common"})
@MapperScan("io.aioa.session.repo.mapper")
public class SessionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SessionServiceApplication.class, args);
    }
}
