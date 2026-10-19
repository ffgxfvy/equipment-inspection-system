package com.renjie.inspection.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（springdoc-openapi）。
 *
 * <p>访问地址：http://localhost:8080/api/swagger-ui.html
 * 除了页面，还能导出 OpenAPI JSON（/v3/api-docs），可直接给前端生成请求代码。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inspectionOpenApi() {
        return new OpenAPI().info(new Info()
                .title("工业设备巡检管理系统 API")
                .version("1.0.0")
                .description("""
                        设备台账、巡检任务、巡检记录与异常告警管理。

                        认证方式：先调用 /auth/login 获取 token，
                        再在后续请求的 Header 里带上 `Authorization: Bearer <token>`。
                        """)
                .contact(new Contact().name("金仁杰")));
    }
}
