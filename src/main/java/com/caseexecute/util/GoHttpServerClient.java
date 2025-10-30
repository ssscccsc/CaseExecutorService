package com.caseexecute.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * GoHttpServer客户端工具类
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
public class GoHttpServerClient {

    private final CloseableHttpClient httpClient;

    public GoHttpServerClient() {
        this.httpClient = HttpClients.custom()
                .setDefaultRequestConfig(
                    org.apache.http.client.config.RequestConfig.custom()
                        .setConnectTimeout(30000)
                        .setSocketTimeout(60000)
                        .build()
                )
                .build();
    }

    /**
     * 上传本地文件到gohttpserver
     * @param localFilePath 本地文件路径
     * @param targetFileName 目标文件名
     * @param goHttpServerUrl gohttpserver地址
     * @param taskId 任务ID（可选）
     * @return 上传后的文件URL
     */
    public String uploadLocalFile(String localFilePath, String targetFileName, String goHttpServerUrl, String taskId) throws IOException {
        log.info("开始上传本地文件到gohttpserver: {} -> {}, 服务器地址: {}", localFilePath, targetFileName, goHttpServerUrl);
        
        try {
            // 验证输入参数
            if (localFilePath == null || localFilePath.trim().isEmpty()) {
                throw new IOException("本地文件路径不能为空");
            }
            if (targetFileName == null || targetFileName.trim().isEmpty()) {
                throw new IOException("目标文件名不能为空");
            }
            if (goHttpServerUrl == null || goHttpServerUrl.trim().isEmpty()) {
                throw new IOException("gohttpserver地址不能为空");
            }
            
            // 转换为绝对路径
            Path sourcePath = Paths.get(localFilePath).toAbsolutePath();
            log.info("解析后的绝对路径: {}", sourcePath.toString());
            
            if (!Files.exists(sourcePath)) {
                throw new IOException("源文件不存在: " + sourcePath.toString() + " (原始路径: " + localFilePath + ")");
            }
            
            // 验证文件是否可读
            if (!Files.isReadable(sourcePath)) {
                throw new IOException("源文件不可读: " + sourcePath.toString());
            }
            
            // 构建上传URL，使用gohttpserver的标准上传接口，拼上taskId目录
            String uploadUrl;
            if (taskId != null && !taskId.trim().isEmpty()) {
                uploadUrl = goHttpServerUrl + "/upload/" + taskId;
            } else {
                uploadUrl = goHttpServerUrl + "/upload";
            }
            
            // 读取文件内容
            byte[] fileBytes = Files.readAllBytes(sourcePath);
            log.info("文件读取成功 - 文件大小: {} bytes", fileBytes.length);
            
            // 构建multipart请求
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            byte[] multipartBody = buildMultipartBody(fileBytes, targetFileName, boundary);
            log.info("构建multipart请求成功 - 请求体大小: {} bytes, 上传URL: {}", multipartBody.length, uploadUrl);
            
            // 发送HTTP请求
            HttpPost request = new HttpPost(uploadUrl);
            request.setHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
            request.setEntity(new ByteArrayEntity(multipartBody));
            
            log.info("发送HTTP POST请求到: {}", uploadUrl);
            CloseableHttpResponse response = httpClient.execute(request);
            
            try {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                
                log.info("HTTP响应 - 状态码: {}, 响应体: {}", statusCode, responseBody);
                
                if (statusCode == 200 || statusCode == 201) {
                    String fileUrl;
                    if (taskId != null && !taskId.trim().isEmpty()) {
                        fileUrl = goHttpServerUrl + "/upload/" + taskId + "/" + targetFileName;
                    } else {
                        fileUrl = goHttpServerUrl + "/upload/" + targetFileName;
                    }
                    log.info("本地文件上传成功: {}", fileUrl);
                    return fileUrl;
                } else {
                    log.error("上传失败 - HTTP状态码: {}, 响应: {}, 上传URL: {}", statusCode, responseBody, uploadUrl);
                    throw new IOException("上传失败，HTTP状态码: " + statusCode + ", 响应: " + responseBody + ", 上传URL: " + uploadUrl);
                }
            } finally {
                response.close();
            }
            
        } catch (Exception e) {
            log.error("上传本地文件到gohttpserver失败: {}", e.getMessage());
            throw new IOException("上传本地文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 上传本地文件到gohttpserver（兼容旧版本）
     * @param localFilePath 本地文件路径
     * @param targetFileName 目标文件名
     * @param goHttpServerUrl gohttpserver地址
     * @return 上传后的文件URL
     */
    public String uploadLocalFile(String localFilePath, String targetFileName, String goHttpServerUrl) throws IOException {
        return uploadLocalFile(localFilePath, targetFileName, goHttpServerUrl, null);
    }

    /**
     * 构建multipart body
     */
    private byte[] buildMultipartBody(byte[] fileBytes, String fileName, String boundary) throws IOException {
        StringBuilder body = new StringBuilder();
        
        // 添加文件部分
        body.append("--").append(boundary).append("\r\n");
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName).append("\"\r\n");
        body.append("Content-Type: text/plain\r\n");
        body.append("\r\n");
        
        // 转换为字节数组
        byte[] headerBytes = body.toString().getBytes("UTF-8");
        byte[] footerBytes = ("\r\n--" + boundary + "--\r\n").getBytes("UTF-8");
        
        // 组合完整的multipart body
        byte[] multipartBody = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, multipartBody, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, multipartBody, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, multipartBody, headerBytes.length + fileBytes.length, footerBytes.length);
        
        return multipartBody;
    }
}
