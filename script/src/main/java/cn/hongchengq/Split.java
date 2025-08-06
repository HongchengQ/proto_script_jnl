package cn.hongchengq;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class Split {
    static String inputProtoFilePath = Config.getConfig().replaceOutputDirectory;
    static String outputDirectory = Config.getConfig().splitOutputDirectory;

    static List<topFloorMessagesMetadata> topFloorMessages = new ArrayList<>();

    // 存储所有文件头部基本信息 - syntax、package、import等 (是根据大proto复制来的)
    static List<String> headerLines = new ArrayList<>();

    public static void start(String replaceFilePath) {
        if (replaceFilePath == null) return;
        inputProtoFilePath = replaceFilePath;

        try {
            // 确保输出目录存在
            Files.createDirectories(Paths.get(outputDirectory));

            parseProtoFileLines();

            for (topFloorMessagesMetadata topFloorMessage : topFloorMessages) {
                for (String s : topFloorMessage.extraNestedMessagesName) {
                    // 清除无用数据
                    topFloorMessage.needImportMessage.remove(s);
                }

                // needImportMessage 去重
                List<String> importMessage = topFloorMessage.needImportMessage;
                topFloorMessage.needImportMessage = new ArrayList<>(new LinkedHashSet<>(importMessage));

                // 创建 proto 文件
                createProtoFile(topFloorMessage);
            }

            log.info("Proto文件分割完成，共生成 {} 个文件", topFloorMessages.size());
        } catch (IOException e) {
            log.error("分割proto文件时出错: ", e);
        }
    }

    public static void parseProtoFileLines() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(inputProtoFilePath));

        // message嵌套深度: 当行内出现可以嵌套或被嵌套的类型时 +1, 匹配到 "}" 符号时 -1, 归零时生成一个新的message
        int messageNestingAllowance = 0;
        // 最后一次读取的 cmdId
        int lastCmdId = 0;

        for (String line : lines) {
            boolean isNestingTypeLine = false;  // 是可以嵌套类型行
            String lineImportMessage = null;      // 行导入自定义类型

            // 移除每行前导和尾随空格
            String trimmedLine = line.trim();

            // 收集文件头部信息
            if (topFloorMessages.isEmpty() &&
                    (trimmedLine.startsWith("syntax") ||
                            trimmedLine.startsWith("package") ||
                            trimmedLine.startsWith("import"))) {
                headerLines.add(line);
                continue;
            }

            if (trimmedLine.startsWith(ConstProtoType.getDumpedCmdId() + " ")) {
                Integer CmdId = extractCmdId(trimmedLine); // 提取 CmdId
                lastCmdId = Objects.requireNonNullElse(CmdId, 0);
            }

            // 构建 messages
            for (String NestingType : ConstProtoType.getConstNestingType()) {
                // 行内出现可以嵌套或被嵌套的类型
                if (trimmedLine.startsWith(NestingType + " ")) {
                    String messageName = extractTypeName(trimmedLine, NestingType); // 提取message名称
                    if (messageName != null) {
                        // 没有message嵌套余量时 代表已经进入下一个message了
                        if (messageNestingAllowance == 0) {
                            // 这时可以构建 newMessage
                            topFloorMessagesMetadata newMessage = new topFloorMessagesMetadata();
                            newMessage.name = messageName;
                            newMessage.cmdId = lastCmdId;
                            // 向 messages 添加 newMessage
                            topFloorMessages.add(newMessage);

                            lastCmdId = 0;
                        } else {
                            // 当前message在上一层嵌套中 需要导入
                            topFloorMessages.getLast().extraNestedMessagesName.add(messageName);
                        }
                        ++messageNestingAllowance;
                    }
                    isNestingTypeLine = true;
                    break;
                }
            }

            // 查找需要导入的自定义类型
            int fieldTypeMaxCount = ConstProtoType.getSimpleType().size();
            int fieldTypeCount = 0; // 已经查找过的固定类型, 等于max时代表这一行不是固定类型 极大可能是自定义类型 需要移交下一步操作, 等于0时后续也不需要操作
            for (String fieldType : ConstProtoType.getSimpleType()) {
                if (isNestingTypeLine) break;

                fieldTypeCount++;

                // 行开头时基本数据类型
                if (trimmedLine.startsWith(fieldType + " ")) {
                    fieldTypeCount = 0;
                    break;
                }
            }

            if ((fieldTypeCount == fieldTypeMaxCount)) {
                String name = extractFieldType(trimmedLine); // 提取字段类型名称
                if (name != null) {
                    lineImportMessage = name;
                }
            }

            // 根据符号判断已经进入方法末尾
            if (trimmedLine.equals("}")) {
                --messageNestingAllowance;
            }

            // messages已录入，并且录入不是cmdid那一行
            if (!topFloorMessages.isEmpty() && !trimmedLine.startsWith(ConstProtoType.getDumpedCmdId() + " ")) {
                // 向顶层message加入自身行
                topFloorMessages.getLast().lines.add(line);
                // 向顶层message加入需要import的message
                if (lineImportMessage != null && !isNestingTypeLine) {
                    topFloorMessages.getLast().needImportMessage.add(lineImportMessage);
                }
            }

        }
    }

    /**
     * 提取类型后面的名称
     * @param line 行
     * @param type 前往下面的方法查看 cn.hongchengq.ConstProtoType#getNestingType()
     * @return 类型后面的名称 如: GetPlayerTokenReq/Retcode
     */
    private static String extractTypeName(String line, String type) {
        Pattern pattern = Pattern.compile(type + "\\s+([\\w_]+)");
        Matcher matcher = pattern.matcher(line.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 提取需要导入的自定义类型
     * @param line 行
     * @return 需要导入的自定义类型
     */
    private static String extractFieldType(String line) {
        String customType = null;

        // 对于简单行(类型) 使用这样的方式
        Pattern pattern = Pattern.compile("^\\s*([\\w_]+)");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            customType = matcher.group(1);
        }
        if (customType == null) return null;

        // 跳过enum值定义行（如 ada = 0; 这样的行）
        Pattern enumValuePattern = Pattern.compile("^\\s*[\\w_]+\\s*=\\s*\\d+\\s*;");
        Matcher enumValueMatcher = enumValuePattern.matcher(line.trim());
        if (enumValueMatcher.find()) {
            return null; // 这是enum值定义行，不需要导入任何类型
        }

        if (customType.equals(ConstProtoType.getRepeatedType())) {
            // 处理 repeated 类型
            Pattern repeatedPattern = Pattern.compile("repeated\\s+([\\w_]+)");
            Matcher repeatedMatcher = repeatedPattern.matcher(line);
            if (repeatedMatcher.find()) {
                String repeatedType = repeatedMatcher.group(1);
                // 检查是否为自定义类型（非基本类型）
                if (!ConstProtoType.getSimpleType().contains(repeatedType)) {
                    customType = repeatedType;
                }
            }
        } else if (customType.equals(ConstProtoType.getMapType())) {
            // 处理 map 类型
            Pattern mapPattern = Pattern.compile("map<\\s*([\\w_]+)\\s*,\\s*([\\w_]+)\\s*>");
            Matcher mapMatcher = mapPattern.matcher(line);
            if (mapMatcher.find()) {
                String mapValueType = mapMatcher.group(2); // 只关心值类型，键类型通常是基本类型
                // 检查值类型是否为自定义类型（非基本类型）
                if (!ConstProtoType.getSimpleType().contains(mapValueType)) {
                    customType = mapValueType;
                }
            }
        }

        // 检查是否为基本类型，如果是则不返回
        if (ConstProtoType.getAllConstTypes().contains(customType)) {
            return null;
        }

        return customType;
    }

    /**
     * 提取 CmdId 后面的数字
     * 目标行示例: // CmdId: 46
     * @param line 行
     * @return CmdId
     */
    private static Integer extractCmdId(String line) {
        Pattern pattern = Pattern.compile("//\\s+CmdId:\\s+(\\d+)");
        Matcher matcher = pattern.matcher(line.trim());
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static void createProtoFile(topFloorMessagesMetadata proto) {
        String fileName = outputDirectory + File.separator + proto.name + ".proto";

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(fileName))) {
            // 写入头部信息
            for (String headerLine : headerLines) {
                writer.write(headerLine);
                writer.newLine();
            }
            // 添加空行分隔
            writer.newLine();

            // 写入需要 import 的 message 定义
            for (String importMessage : proto.needImportMessage) {
                writer.write("import \"" + importMessage + "\"" + ";");
                // 确保每个message定义后都有空行分隔
                writer.newLine();
            }
            // 添加空行分隔
            writer.newLine();

            // 写入目标消息定义
            for (String messageLine : proto.lines) {
                writer.write(messageLine);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * proto 输出信息元数据
     * 这里的数据将写入输出文件
     */
    private static class topFloorMessagesMetadata {
        String name;                                                                // 输出文件名
        int cmdId = 0;                                                              // CmdId
        List<String> lines = new ArrayList<>();                                     // 自身包含的行
        List<String> needImportMessage = new ArrayList<>();                         // 需要import的message
        List<String> extraNestedMessagesName = new ArrayList<>();                   // 自身额外嵌套类的name
        List<topFloorMessagesMetadata> extraNestedMessages = new ArrayList<>();     // 自身额外嵌套类 暂时不需要
    }

}
