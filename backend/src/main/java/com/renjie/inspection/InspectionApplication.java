package com.renjie.inspection;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 工业设备巡检管理系统 —— 启动类。
 *
 * <p>接口前缀 {@code /api}（在 application.yml 里通过 context-path 配置），
 * 启动后接口文档见 http://localhost:8080/api/swagger-ui.html
 */
@SpringBootApplication
@MapperScan("com.renjie.inspection.mapper")
public class InspectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(InspectionApplication.class, args);
    }
}
