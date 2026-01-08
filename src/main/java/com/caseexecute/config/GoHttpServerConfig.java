package com.caseexecute.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GoHttpServer配置类
 * 用于读取application.yml中的gohttpserver配置
 * 
 * @author system
 * @since 2024-01-01
 */
@Data
@Component
@ConfigurationProperties(prefix = "gohttpserver")
public class GoHttpServerConfig {
    
    /**
     * GoHttpServer服务地址
     */
    private String url;
    
    /**
     * 打印配置信息（用于调试）
     */
    public void printConfig() {
        System.out.println("=== GoHttpServerConfig 配置信息 ===");
        System.out.println("url: " + url);
        System.out.println("==================================");
    }
}

