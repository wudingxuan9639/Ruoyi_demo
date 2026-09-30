package com.ruoyi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RuoYi 启动类。
 *
 * <p>原版 RuoYi 会排除 DataSourceAutoConfiguration 并自行装配 Druid 数据源；
 * 本示例为了保持"最小可运行"，直接使用 druid-spring-boot-3-starter 的自动配置，
 * 数据源参数见 resources/application-druid.yml。
 */
@SpringBootApplication
public class RuoYiApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuoYiApplication.class, args);
    }
}
