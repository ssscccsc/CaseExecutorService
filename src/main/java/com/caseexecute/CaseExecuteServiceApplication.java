package com.caseexecute;

import com.caseexecute.config.FileStorageConfig;
import com.caseexecute.config.WebSocketClientConfig;
import com.caseexecute.service.WebSocketClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 用例执行服务启动类
 * 
 * @author system
 * @since 2024-01-01
 */
@SpringBootApplication
@EnableConfigurationProperties({FileStorageConfig.class, WebSocketClientConfig.class})
public class CaseExecuteServiceApplication implements CommandLineRunner {

    @Autowired
    private WebSocketClientService webSocketClientService;
    
    public static void main(String[] args) {
        SpringApplication.run(CaseExecuteServiceApplication.class, args);
        System.out.println("用例执行服务启动成功！");
    }
    
    @Override
    public void run(String... args) throws Exception {
        // 应用启动后，延迟3秒启动WebSocket客户端，确保所有Bean都已初始化
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                webSocketClientService.start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
