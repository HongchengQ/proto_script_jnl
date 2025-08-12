package cn.hongchengq.proto_script_jnl.revert;

import cn.hongchengq.proto_script_jnl.Main;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// todo
@Slf4j
public class RevertMapping {

    /**
     * 比较两个目录中的proto文件，生成字段映射关系
     *
     * @param originalProtoDir 原始proto文件目录
     * @param processedProtoDir 处理后的proto文件目录
     * @param outputTsvPath 输出TSV文件路径
     */
    public static void generateMapping(String originalProtoDir, String processedProtoDir, String outputTsvPath) {
        try {
            // 获取两个目录中的所有proto文件
            Map<String, List<String>> originalMessages = parseProtoDirectory(originalProtoDir);
            Map<String, List<String>> processedMessages = parseProtoDirectory(processedProtoDir);

            // 创建输出目录
            Path outputPath = Paths.get(outputTsvPath).getParent();
            if (outputPath != null) {
                Files.createDirectories(outputPath);
            }

            // 写入TSV文件
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputTsvPath))) {
                // 写入头部
                writer.write("Obfuscated\tDeobfuscated\tMessage\tType");
                writer.newLine();

                // 比较并生成映射关系
                compareAndWriteMappings(writer, originalMessages, processedMessages);
            }

            log.info("映射文件已生成: {}", outputTsvPath);
        } catch (IOException e) {
            log.error("生成映射文件时出错", e);
        }
    }

    /**
     * 解析目录中的所有proto文件
     *
     * @param protoDir proto文件目录路径
     * @return 消息名称到字段列表的映射
     * @throws IOException IO异常
     */
    private static Map<String, List<String>> parseProtoDirectory(String protoDir) throws IOException {
        Map<String, List<String>> messages = new HashMap<>();

        try (Stream<Path> paths = Files.walk(Paths.get(protoDir))) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".proto"))
                    .forEach(path -> {
                        try {
                            Map<String, List<String>> fileMessages = parseProtoFile(path.toString());
                            messages.putAll(fileMessages);
                        } catch (IOException e) {
                            log.error("解析文件 {} 时出错", path, e);
                        }
                    });
        }

        return messages;
    }

    /**
     * 解析单个proto文件
     *
     * @param filePath proto文件路径
     * @return 消息名称到字段列表的映射
     * @throws IOException IO异常
     */
    private static Map<String, List<String>> parseProtoFile(String filePath) throws IOException {
        Map<String, List<String>> messages = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(Paths.get(filePath));

        String currentMessage = null;
        List<String> currentFields = new ArrayList<>();
        int nestingLevel = 0;

        for (String line : lines) {
            String trimmedLine = line.trim();

            // 处理message开始
            if (trimmedLine.startsWith("message ")) {
                if (nestingLevel == 0) {
                    // 顶层message
                    Matcher matcher = Pattern.compile("message\\s+([\\w_]+)").matcher(trimmedLine);
                    if (matcher.find()) {
                        currentMessage = matcher.group(1);
                        currentFields = new ArrayList<>();
                    }
                }
                nestingLevel++;
            }
            // 处理message结束
            else if (trimmedLine.equals("}")) {
                nestingLevel--;
                if (nestingLevel == 0 && currentMessage != null) {
                    messages.put(currentMessage, currentFields);
                    currentMessage = null;
                }
            }
            // 处理字段定义
            else if (currentMessage != null && nestingLevel == 1) {
                // 匹配字段定义行 (类型 名称 = 编号;)
                Matcher fieldMatcher = Pattern.compile("^\\s*[\\w.]+\\s+([\\w_]+)\\s*=").matcher(trimmedLine);
                if (fieldMatcher.find()) {
                    currentFields.add(fieldMatcher.group(1));
                }
            }
        }

        return messages;
    }

    /**
     * 比较原始和处理后的消息，写入映射关系
     *
     * @param writer TSV文件写入器
     * @param originalMessages 原始消息映射
     * @param processedMessages 处理后的消息映射
     * @throws IOException IO异常
     */
    private static void compareAndWriteMappings(BufferedWriter writer,
                                                Map<String, List<String>> originalMessages,
                                                Map<String, List<String>> processedMessages) throws IOException {

        for (Map.Entry<String, List<String>> entry : originalMessages.entrySet()) {
            String messageName = entry.getKey();
            List<String> originalFields = entry.getValue();

            // 查找对应的处理后消息
            List<String> processedFields = processedMessages.get(messageName);
            if (processedFields == null) {
                continue;
            }

            // 比较字段
            for (int i = 0; i < originalFields.size() && i < processedFields.size(); i++) {
                String originalField = originalFields.get(i);
                String processedField = processedFields.get(i);

                // 如果字段名不同，说明发生了映射
                if (!originalField.equals(processedField)) {
                    // 判断是否是混淆字段（全大写）
                    if (isObfuscatedField(originalField)) {
                        writer.write(originalField + "\t" + processedField + "\t" + messageName + "\tfield");
                        writer.newLine();
                    }
                }
            }

            // 检查消息名称本身是否被解混淆
            String processedMessageName = messageName; // 默认使用原名称
            if (processedMessages.containsKey(messageName)) {
                // 如果处理后的消息存在，但名称不同，则可能是消息名被解混淆
                // 这里假设消息名的映射关系需要通过其他方式确定
                // 目前仅检查是否符合混淆特征
                if (isObfuscatedField(messageName)) {
                    // 在这种情况下，我们无法直接从processedMessages获取解混淆后的消息名
                    // 因为key还是混淆的名称。实际应用中可能需要更复杂的映射逻辑
                    writer.write(messageName + "\t" + messageName + "\t\tmessage");
                    writer.newLine();
                }
            }
        }
    }

    /**
     * 判断是否是混淆字段（纯大写字母组成）
     *
     * @param fieldName 字段名称
     * @return 是否是混淆字段
     */
    private static boolean isObfuscatedField(String fieldName) {
        return fieldName != null &&
                !fieldName.isEmpty() &&
                fieldName.matches("^[A-Z]+$");
    }


    /**
     * 主方法，用于测试和运行
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 示例用法
        String originalProtoDir = "proto/original";
        String processedProtoDir = "proto/processed";
        String outputTsvPath = "proto/output/mapping.tsv";

        generateMapping(originalProtoDir, processedProtoDir, outputTsvPath);
    }
}
