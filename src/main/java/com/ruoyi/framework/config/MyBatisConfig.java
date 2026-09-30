package com.ruoyi.framework.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置（对应 RuoYi 的 framework/config/MyBatisConfig）。
 */
@Configuration
@MapperScan("com.ruoyi.project.**.mapper")
public class MyBatisConfig {
}
