package io.aioa.ledger;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 词元账本服务入口
 */
@SpringBootApplication
@ComponentScan(basePackages = {"io.aioa.ledger", "io.aioa.common"})
@MapperScan("io.aioa.ledger.repo.mapper")
public class LedgerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }
}
