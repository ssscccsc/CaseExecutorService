package com.caseexecute.service.impl;

import com.alibaba.fastjson.JSON;
import com.caseexecute.config.WebSocketClientConfig;
import com.caseexecute.dto.WebSocketMessage;
import com.caseexecute.dto.ExecutorRegisterMessage;
import com.caseexecute.service.WebSocketClientService;
import com.caseexecute.service.TestCaseExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.URI;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket客户端服务实现
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
@Service
public class WebSocketClientServiceImpl implements WebSocketClientService {
    
    @Autowired
    private WebSocketClientConfig webSocketClientConfig;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    /**
     * 本地IP地址
     */
    private String localIp;
    
    /**
     * 本地MAC地址
     */
    private String localMac;
    
    /**
     * WebSocket客户端实例
     */
    private WebSocketClient webSocketClient;
    
    /**
     * 定时任务执行器（用于心跳和重连）
     */
    private ScheduledExecutorService scheduler;
    
    
    /**
     * 连接状态
     */
    private volatile boolean connected = false;
    
    @PostConstruct
    public void init() {
        if (webSocketClientConfig.getEnabled()) {
            scheduler = Executors.newScheduledThreadPool(2);
            try {
                localIp = getLocalIpAddress();
                log.info("执行机本地IP地址: {}", localIp);
                
                localMac = getLocalMacAddress();
                log.info("执行机本地MAC地址: {}", localMac);
            } catch (Exception e) {
                log.error("获取本地IP地址失败: {}", e.getMessage(), e);
            }
        }
    }
    
    @PreDestroy
    public void destroy() {
        stop();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
    
    @Override
    public void start() {
        if (!webSocketClientConfig.getEnabled()) {
            log.info("WebSocket客户端未启用，跳过连接");
            return;
        }
        
        if (connected) {
            log.info("WebSocket客户端已连接，无需重复启动");
            return;
        }
        
        try {
            String serverUrl = webSocketClientConfig.getServerUrl();
            log.info("启动WebSocket客户端，连接地址: {}", serverUrl);
            
            URI serverUri = new URI(serverUrl);
            webSocketClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    connected = true;
                    log.info("WebSocket连接已建立 - HTTP状态码: {}, 消息: {}", 
                            handshake.getHttpStatus(), handshake.getHttpStatusMessage());
                    
                    // 连接成功后立即注册执行机信息
                    registerExecutor();
                    
                    // 启动心跳定时任务
                    startHeartbeat();
                }
                
                @Override
                public void onMessage(String message) {
                    log.debug("收到后台WebSocket消息: {}", message);
                    handleMessage(message);
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connected = false;
                    log.warn("WebSocket连接已关闭 - 代码: {}, 原因: {}, 远程关闭: {}", 
                            code, reason, remote);
                    
                    // 如果连接被远程关闭或异常关闭，尝试重连
                    if (remote || code != 1000) {
                        scheduleReconnect();
                    }
                }
                
                @Override
                public void onError(Exception ex) {
                    log.error("WebSocket连接错误: {}", ex.getMessage(), ex);
                    connected = false;
                }
            };
            
            // 设置连接超时
            webSocketClient.setConnectionLostTimeout(
                    webSocketClientConfig.getConnectionTimeout().intValue());
            
            // 尝试连接
            boolean connected = webSocketClient.connectBlocking(
                    webSocketClientConfig.getConnectionTimeout().intValue(), 
                    TimeUnit.SECONDS);
            
            if (!connected) {
                log.error("WebSocket连接失败，将在{}秒后重试", webSocketClientConfig.getReconnectInterval());
                scheduleReconnect();
            }
            
        } catch (Exception e) {
            log.error("启动WebSocket客户端失败: {}", e.getMessage(), e);
            scheduleReconnect();
        }
    }
    
    @Override
    public void stop() {
        if (webSocketClient != null) {
            try {
                webSocketClient.close();
            } catch (Exception e) {
                log.error("关闭WebSocket连接失败: {}", e.getMessage(), e);
            }
            webSocketClient = null;
        }
        connected = false;
    }
    
    @Override
    public boolean isConnected() {
        return connected && webSocketClient != null && webSocketClient.isOpen();
    }
    
    @Override
    public void sendMessage(String message) {
        if (isConnected()) {
            try {
                webSocketClient.send(message);
                log.debug("发送WebSocket消息: {}", message);
            } catch (Exception e) {
                log.error("发送WebSocket消息失败: {}", e.getMessage(), e);
            }
        } else {
            log.warn("WebSocket未连接，无法发送消息: {}", message);
        }
    }
    
    @Override
    public void registerExecutor() {
        if (localIp == null || localIp.isEmpty()) {
            log.error("本地IP地址为空，无法注册执行机");
            return;
        }
        
        try {
            ExecutorRegisterMessage registerMsg = new ExecutorRegisterMessage();
            registerMsg.setExecutorIp(localIp);
            registerMsg.setExecutorMac(localMac);
            registerMsg.setExecutorName("Executor-" + localIp);
            registerMsg.setStatus(1); // 在线
            registerMsg.setTimestamp(System.currentTimeMillis());
            
            WebSocketMessage wsMessage = new WebSocketMessage();
            wsMessage.setType("REGISTER");
            wsMessage.setData(registerMsg);
            wsMessage.setTimestamp(System.currentTimeMillis());
            wsMessage.setExecutorIp(localIp);
            wsMessage.setMessageId(generateMessageId());
            
            String messageJson = JSON.toJSONString(wsMessage);
            sendMessage(messageJson);
            
            log.info("执行机注册消息已发送 - 执行机IP: {}", localIp);
            
        } catch (Exception e) {
            log.error("发送执行机注册消息失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 处理收到的消息
     */
    private void handleMessage(String message) {
        try {
            WebSocketMessage wsMessage = JSON.parseObject(message, WebSocketMessage.class);
            if (wsMessage == null || wsMessage.getType() == null) {
                log.warn("收到无效的WebSocket消息: {}", message);
                return;
            }
            
            String messageType = wsMessage.getType();
            
            switch (messageType) {
                case "REGISTER_RESPONSE":
                    handleRegisterResponse(wsMessage);
                    break;
                case "TASK":
                    handleTaskMessage(wsMessage);
                    break;
                case "CANCEL":
                    handleCancelMessage(wsMessage);
                    break;
                case "HEARTBEAT_RESPONSE":
                    log.debug("收到心跳响应");
                    break;
                case "ERROR":
                    log.error("收到错误消息: {}", wsMessage.getData());
                    break;
                default:
                    log.warn("未知的消息类型: {}", messageType);
            }
            
        } catch (Exception e) {
            log.error("处理WebSocket消息失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 处理注册响应
     */
    private void handleRegisterResponse(WebSocketMessage wsMessage) {
        log.info("执行机注册成功 - 响应: {}", wsMessage.getData());
    }
    
    /**
     * 处理任务消息
     */
    private void handleTaskMessage(WebSocketMessage wsMessage) {
        try {
            com.caseexecute.dto.TestCaseExecutionRequest request = 
                    JSON.parseObject(JSON.toJSONString(wsMessage.getData()), 
                            com.caseexecute.dto.TestCaseExecutionRequest.class);
            
            if (request == null) {
                log.error("任务消息解析失败");
                return;
            }
            
            log.info("收到后台下发的任务 - 任务ID: {}", request.getTaskId());
            
            // 调用任务执行服务处理任务（延迟获取Bean，避免循环依赖）
            TestCaseExecutionService executionService = applicationContext.getBean(TestCaseExecutionService.class);
            if (executionService != null) {
                executionService.processTestCaseExecution(request);
            } else {
                log.error("无法获取TestCaseExecutionService，任务无法执行 - 任务ID: {}", request.getTaskId());
            }
            
            // 发送任务接收确认
            WebSocketMessage response = new WebSocketMessage();
            response.setType("TASK_RESPONSE");
            response.setTimestamp(System.currentTimeMillis());
            response.setMessageId(wsMessage.getMessageId());
            response.setExecutorIp(localIp);
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("taskId", request.getTaskId());
            data.put("status", "RECEIVED");
            data.put("message", "任务已接收");
            response.setData(data);
            
            sendMessage(JSON.toJSONString(response));
            
        } catch (Exception e) {
            log.error("处理任务消息失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 处理停止命令消息
     */
    private void handleCancelMessage(WebSocketMessage wsMessage) {
        try {
            // 解析停止命令数据
            java.util.Map<String, Object> cancelData = (java.util.Map<String, Object>) wsMessage.getData();
            if (cancelData == null) {
                log.error("停止命令消息数据为空");
                return;
            }
            
            String taskId = (String) cancelData.get("taskId");
            if (taskId == null || taskId.trim().isEmpty()) {
                log.error("停止命令消息中任务ID为空");
                return;
            }
            
            log.info("收到后台下发的停止命令 - 任务ID: {}", taskId);
            
            // 调用任务执行服务取消任务（延迟获取Bean，避免循环依赖）
            TestCaseExecutionService executionService = applicationContext.getBean(TestCaseExecutionService.class);
            if (executionService != null) {
                boolean cancelled = executionService.cancelTaskExecution(taskId);
                if (cancelled) {
                    log.info("任务已成功取消 - 任务ID: {}", taskId);
                } else {
                    log.warn("任务取消失败，可能任务不存在或已完成 - 任务ID: {}", taskId);
                }
            } else {
                log.error("无法获取TestCaseExecutionService，停止命令无法执行 - 任务ID: {}", taskId);
            }
            
            // 发送停止命令响应
            WebSocketMessage response = new WebSocketMessage();
            response.setType("CANCEL_RESPONSE");
            response.setTimestamp(System.currentTimeMillis());
            response.setMessageId(wsMessage.getMessageId());
            response.setExecutorIp(localIp);
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("taskId", taskId);
            data.put("status", "CANCELLED");
            data.put("message", "停止命令已处理");
            response.setData(data);
            
            sendMessage(JSON.toJSONString(response));
            
        } catch (Exception e) {
            log.error("处理停止命令消息失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 启动心跳定时任务
     */
    private void startHeartbeat() {
        if (scheduler != null) {
            scheduler.scheduleAtFixedRate(() -> {
                if (isConnected()) {
                    try {
                        WebSocketMessage heartbeat = new WebSocketMessage();
                        heartbeat.setType("HEARTBEAT");
                        heartbeat.setTimestamp(System.currentTimeMillis());
                        heartbeat.setMessageId(generateMessageId());
                        heartbeat.setExecutorIp(localIp);
                        java.util.Map<String, Object> data = new java.util.HashMap<>();
                        data.put("status", "OK");
                        heartbeat.setData(data);
                        
                        sendMessage(JSON.toJSONString(heartbeat));
                    } catch (Exception e) {
                        log.error("发送心跳失败: {}", e.getMessage(), e);
                    }
                }
            }, webSocketClientConfig.getHeartbeatInterval(), 
                    webSocketClientConfig.getHeartbeatInterval(), TimeUnit.SECONDS);
        }
    }
    
    /**
     * 安排重连
     */
    private void scheduleReconnect() {
        if (scheduler != null) {
            scheduler.schedule(() -> {
                log.info("尝试重新连接WebSocket服务器...");
                stop();
                start();
            }, webSocketClientConfig.getReconnectInterval(), TimeUnit.SECONDS);
        }
    }
    
    /**
     * 获取本地IP地址
     */
    private String getLocalIpAddress() throws Exception {
        // 优先尝试获取非回环地址
        java.util.Enumeration<java.net.NetworkInterface> interfaces = 
                java.net.NetworkInterface.getNetworkInterfaces();
        
        while (interfaces.hasMoreElements()) {
            java.net.NetworkInterface networkInterface = interfaces.nextElement();
            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                continue;
            }
            
            java.util.Enumeration<java.net.InetAddress> addresses = 
                    networkInterface.getInetAddresses();
            
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof java.net.Inet4Address && !address.isLoopbackAddress()) {
                    return address.getHostAddress();
                }
            }
        }
        
        // 如果没找到，返回本地地址
        return InetAddress.getLocalHost().getHostAddress();
    }
    
    /**
     * 获取本地MAC地址
     */
    private String getLocalMacAddress() {
        try {
            // 优先尝试获取与本地IP对应的网络接口的MAC地址
            if (localIp != null && !localIp.isEmpty()) {
                java.util.Enumeration<java.net.NetworkInterface> interfaces = 
                        java.net.NetworkInterface.getNetworkInterfaces();
                
                while (interfaces.hasMoreElements()) {
                    java.net.NetworkInterface networkInterface = interfaces.nextElement();
                    if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                        continue;
                    }
                    
                    java.util.Enumeration<java.net.InetAddress> addresses = 
                            networkInterface.getInetAddresses();
                    
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (address instanceof java.net.Inet4Address && 
                            !address.isLoopbackAddress() && 
                            address.getHostAddress().equals(localIp)) {
                            byte[] macBytes = networkInterface.getHardwareAddress();
                            if (macBytes != null && macBytes.length > 0) {
                                return formatMacAddress(macBytes);
                            }
                        }
                    }
                }
            }
            
            // 如果没找到对应的，尝试获取第一个非回环网络接口的MAC地址
            java.util.Enumeration<java.net.NetworkInterface> interfaces = 
                    java.net.NetworkInterface.getNetworkInterfaces();
            
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                byte[] macBytes = networkInterface.getHardwareAddress();
                if (macBytes != null && macBytes.length > 0) {
                    return formatMacAddress(macBytes);
                }
            }
            
            log.warn("无法获取MAC地址，返回空字符串");
            return "";
        } catch (Exception e) {
            log.error("获取本地MAC地址失败: {}", e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 格式化MAC地址为字符串（格式：XX:XX:XX:XX:XX:XX）
     */
    private String formatMacAddress(byte[] macBytes) {
        if (macBytes == null || macBytes.length == 0) {
            return "";
        }
        
        StringBuilder macAddress = new StringBuilder();
        for (int i = 0; i < macBytes.length; i++) {
            if (i > 0) {
                macAddress.append(":");
            }
            String hex = Integer.toHexString(0xFF & macBytes[i]);
            if (hex.length() == 1) {
                macAddress.append("0");
            }
            macAddress.append(hex.toUpperCase());
        }
        return macAddress.toString();
    }
    
    /**
     * 生成消息ID
     */
    private String generateMessageId() {
        return "MSG_" + System.currentTimeMillis() + "_" + 
                (int)(Math.random() * 10000);
    }
}

