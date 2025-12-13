package com.caseexecute.util;

import com.caseexecute.dto.TestCaseExecutionRequest;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * phone_list.yaml文件处理工具类
 * 负责搜索、解析和更新phone_list.yaml文件
 */
@Slf4j
public class PhoneListYamlUtil {

    private static final String PHONE_LIST_FILE_NAME = "phone_list.yaml";

    /**
     * 在解压目录中搜索并更新phone_list.yaml文件
     *
     * @param extractPath 解压目录路径
     * @param ueList UE信息列表
     * @throws IOException IO异常
     */
    public static void updatePhoneListYaml(Path extractPath, List<TestCaseExecutionRequest.UeInfo> ueList) throws IOException {
        if (ueList == null || ueList.isEmpty()) {
            log.warn("UE列表为空，跳过phone_list.yaml更新");
            return;
        }

        // 搜索phone_list.yaml文件
        Path yamlFile = findPhoneListYaml(extractPath);
        
        // 读取现有数据或创建新数据
        Map<String, Object> phoneListData = new LinkedHashMap<>();
        if (yamlFile != null && Files.exists(yamlFile)) {
            log.info("找到phone_list.yaml文件: {}", yamlFile);
            phoneListData = loadYamlFile(yamlFile);
        } else {
            log.info("未找到phone_list.yaml文件，将创建新文件");
            // 如果文件不存在，在解压目录根目录创建
            yamlFile = extractPath.resolve(PHONE_LIST_FILE_NAME);
        }

        // 更新UE信息
        boolean updated = updateUeInfo(phoneListData, ueList);
        
        if (updated) {
            // 保存YAML文件
            saveYamlFile(yamlFile, phoneListData);
            log.info("phone_list.yaml文件更新成功: {}", yamlFile);
        } else {
            log.info("phone_list.yaml文件无需更新");
        }
    }

    /**
     * 递归搜索phone_list.yaml文件
     *
     * @param directory 搜索目录
     * @return phone_list.yaml文件路径，如果未找到返回null
     */
    private static Path findPhoneListYaml(Path directory) {
        try {
            return Files.walk(directory)
                    .filter(path -> path.getFileName() != null && 
                            path.getFileName().toString().equalsIgnoreCase(PHONE_LIST_FILE_NAME))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.error("搜索phone_list.yaml文件失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 加载YAML文件
     *
     * @param yamlFile YAML文件路径
     * @return 解析后的数据
     * @throws IOException IO异常
     */
    private static Map<String, Object> loadYamlFile(Path yamlFile) throws IOException {
        try (InputStream inputStream = Files.newInputStream(yamlFile)) {
            Yaml yaml = new Yaml();
            Object data = yaml.load(inputStream);
            
            if (data == null) {
                return new LinkedHashMap<>();
            }
            
            if (data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) data;
                return map;
            } else {
                log.warn("phone_list.yaml文件格式不正确，将创建新文件");
                return new LinkedHashMap<>();
            }
        } catch (Exception e) {
            log.error("解析phone_list.yaml文件失败: {}", e.getMessage(), e);
            throw new IOException("解析phone_list.yaml文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新UE信息到YAML数据中
     *
     * @param phoneListData YAML数据
     * @param ueList UE信息列表
     * @return 是否进行了更新
     */
    private static boolean updateUeInfo(Map<String, Object> phoneListData, List<TestCaseExecutionRequest.UeInfo> ueList) {
        boolean updated = false;

        for (TestCaseExecutionRequest.UeInfo ue : ueList) {
            if (ue == null || ue.getUeId() == null || ue.getUeId().trim().isEmpty()) {
                log.warn("UE信息不完整，跳过: {}", ue);
                continue;
            }

            String ueId = ue.getUeId().trim();

            // 检查UEID是否已存在
            if (phoneListData.containsKey(ueId)) {
                log.debug("UEID {} 已存在于phone_list.yaml中，跳过", ueId);
                continue;
            }

            // 创建UE信息Map
            Map<String, String> ueInfo = new LinkedHashMap<>();
            ueInfo.put("vendor", ue.getVendor() != null ? ue.getVendor() : "");
            ueInfo.put("model", ue.getModel() != null ? ue.getModel() : "");
            ueInfo.put("os", determineOs(ue.getVendor()));

            // 添加到数据中
            phoneListData.put(ueId, ueInfo);
            updated = true;
            log.info("添加UE信息到phone_list.yaml: UEID={}, vendor={}, model={}, os={}", 
                    ueId, ueInfo.get("vendor"), ueInfo.get("model"), ueInfo.get("os"));
        }

        return updated;
    }

    /**
     * 根据厂商判断操作系统
     * apple -> ios
     * hisilicon -> harmony
     * 其他 -> android
     *
     * @param vendor 厂商
     * @return 操作系统
     */
    private static String determineOs(String vendor) {
        if (vendor == null || vendor.trim().isEmpty()) {
            return "android";
        }

        String vendorLower = vendor.trim().toLowerCase();
        if (vendorLower.equals("apple")) {
            return "ios";
        } else if (vendorLower.equals("hisilicon") || vendorLower.equals("huawei-hisilicon")) {
            return "harmony";
        } else {
            return "android";
        }
    }

    /**
     * 保存YAML文件
     *
     * @param yamlFile YAML文件路径
     * @param data 要保存的数据
     * @throws IOException IO异常
     */
    private static void saveYamlFile(Path yamlFile, Map<String, Object> data) throws IOException {
        // 确保父目录存在
        if (yamlFile.getParent() != null) {
            Files.createDirectories(yamlFile.getParent());
        }

        // 配置YAML输出选项
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        Yaml yaml = new Yaml(options);

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(yamlFile.toFile()), 
                StandardCharsets.UTF_8)) {
            yaml.dump(data, writer);
            log.info("phone_list.yaml文件保存成功: {}", yamlFile);
        } catch (Exception e) {
            log.error("保存phone_list.yaml文件失败: {}", e.getMessage(), e);
            throw new IOException("保存phone_list.yaml文件失败: " + e.getMessage(), e);
        }
    }
}

