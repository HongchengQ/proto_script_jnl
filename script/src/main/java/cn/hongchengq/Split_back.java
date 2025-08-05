package cn.hongchengq;

import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class Split_back {

    public static void split() {
        String inputProtoFilePath = Config.getConfig().inputProtoFilePath;
        String outputDirectory = Config.getConfig().outputProtoFilePath;

        try {
            // 确保输出目录存在
            Files.createDirectories(Paths.get(outputDirectory));

            List<String> lines = Files.readAllLines(Paths.get(inputProtoFilePath));

            // 存储文件头部信息（syntax、package、import等）
            List<String> headerLines = new ArrayList<>();

            // 存储各个消息类型
            List<ProtoMessage> messages = new ArrayList<>();

            // 存储枚举类型（包括嵌套在message中的enum）
            List<ProtoEnum> enums = new ArrayList<>();

            // 提取文件头部和各个消息体
            parseProtoFile(lines, headerLines, messages, enums);

            // 预编译所有message和enum名称的正则表达式
            Set<String> messageNames = messages.stream()
                    .map(msg -> msg.name)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Set<String> enumNames = enums.stream()
                    .map(enumObj -> extractEnumName(enumObj.lines.get(0)))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<String, Pattern> messagePatterns = new HashMap<>();
            for (String name : messageNames) {
                messagePatterns.put(name, Pattern.compile("\\b" + Pattern.quote(name) + "\\b"));
            }

            Map<String, Pattern> enumPatterns = new HashMap<>();
            for (String name : enumNames) {
                enumPatterns.put(name, Pattern.compile("\\b" + Pattern.quote(name) + "\\b"));
            }

            // 构建message依赖关系图
            Map<String, Set<String>> messageDependencies = buildMessageDependencies(messages, messagePatterns, messageNames);

            // 为每个message创建独立的proto文件
            for (ProtoMessage message : messages) {
                if (message.name != null) {
                    // 获取该message的所有依赖（包括直接和间接依赖）
                    Set<String> dependencies = getAllDependencies(message.name, messageDependencies);
                    createProtoFile(outputDirectory, message, headerLines, messages, enums, dependencies,
                            messagePatterns, enumPatterns);
                }
            }

            log.info("Proto文件分割完成，共生成 {} 个文件", messages.size());

        } catch (IOException e) {
            log.error("分割proto文件时出错: ", e);
        }
    }

    private static void parseProtoFile(List<String> lines, List<String> headerLines,
                                       List<ProtoMessage> messages, List<ProtoEnum> enums) {
        Stack<ProtoMessage> messageStack = new Stack<>();
        Stack<ProtoEnum> enumStack = new Stack<>(); // 用于处理嵌套在message中的enum

        for (String line : lines) {
            String trimmedLine = line.trim();

            // 收集文件头部信息
            if (messageStack.isEmpty() && enumStack.isEmpty() &&
                    (trimmedLine.startsWith("syntax") ||
                            trimmedLine.startsWith("package") ||
                            trimmedLine.startsWith("import"))) {
                headerLines.add(line);
                continue;
            }

            // 检测message开始（支持嵌套）
            if (trimmedLine.startsWith("message ")) {
                ProtoMessage newMessage = new ProtoMessage();
                newMessage.lines.add(line);
                // 提取message名称
                String messageName = extractMessageName(trimmedLine);
                if (messageName != null) {
                    newMessage.name = messageName;
                }

                if (!messageStack.isEmpty()) {
                    // 这是一个嵌套的message
                    messageStack.peek().nestedMessages.add(newMessage);
                }
                messageStack.push(newMessage);
                continue;
            }

            // 检测enum开始（支持嵌套在message中）
            if (trimmedLine.startsWith("enum ")) {
                ProtoEnum newEnum = new ProtoEnum();
                newEnum.lines.add(line);

                if (!messageStack.isEmpty()) {
                    // 这是一个嵌套在message中的enum
                    messageStack.peek().nestedEnums.add(newEnum);
                } else {
                    // 这是一个顶层enum
                    enumStack.push(newEnum);
                }
                continue;
            }

            // 处理message内容（支持嵌套）
            if (!messageStack.isEmpty()) {
                ProtoMessage currentMessage = messageStack.peek();
                currentMessage.lines.add(line);

                // 检测message结束
                if (trimmedLine.equals("}")) {
                    messageStack.pop();
                    if (messageStack.isEmpty()) {
                        // 顶层message结束
                        messages.add(currentMessage);
                    }
                }
                continue;
            }

            // 处理enum内容（支持嵌套在message中）
            if (!enumStack.isEmpty() ||
                    (!messageStack.isEmpty() && !messageStack.peek().nestedEnums.isEmpty())) {

                if (!enumStack.isEmpty()) {
                    // 处理顶层enum
                    ProtoEnum currentEnum = enumStack.peek();
                    currentEnum.lines.add(line);

                    // 检测enum结束
                    if (trimmedLine.equals("}")) {
                        enums.add(enumStack.pop());
                    }
                } else if (!messageStack.isEmpty() && !messageStack.peek().nestedEnums.isEmpty()) {
                    // 处理嵌套在message中的enum
                    ProtoMessage currentMessage = messageStack.peek();
                    ProtoEnum currentEnum = currentMessage.nestedEnums.get(currentMessage.nestedEnums.size() - 1);
                    currentEnum.lines.add(line);

                    // 检测enum结束
                    if (trimmedLine.equals("}")) {
                        // 嵌套enum结束，但仍在message中，不需要从nestedEnums中移除
                    }
                }
                continue;
            }
        }
    }

    private static Map<String, Set<String>> buildMessageDependencies(List<ProtoMessage> messages,
                                                                     Map<String, Pattern> messagePatterns,
                                                                     Set<String> messageNames) {
        Map<String, Set<String>> dependencies = new ConcurrentHashMap<>();

        messages.parallelStream().forEach(message -> {
            if (message.name != null) {
                Set<String> deps = analyzeMessageDependencies(message, messagePatterns, messageNames);
                dependencies.put(message.name, deps);
            }
        });

        return dependencies;
    }

    private static Set<String> analyzeMessageDependencies(ProtoMessage message,
                                                          Map<String, Pattern> messagePatterns,
                                                          Set<String> messageNames) {
        Set<String> dependencies = new HashSet<>();

        // 分析当前message及其所有嵌套message
        analyzeMessageDependenciesRecursive(message, messagePatterns, messageNames, dependencies);

        // 移除对自己的依赖
        dependencies.remove(message.name);

        return dependencies;
    }

    private static void analyzeMessageDependenciesRecursive(ProtoMessage message,
                                                            Map<String, Pattern> messagePatterns,
                                                            Set<String> messageNames,
                                                            Set<String> dependencies) {
        String messageContent = String.join("\n", message.lines);

        // 检查message内容中是否有对其他message的引用
        for (String messageName : messageNames) {
            Pattern pattern = messagePatterns.get(messageName);
            if (pattern != null && pattern.matcher(messageContent).find()) {
                dependencies.add(messageName);
            }
        }

        // 递归分析嵌套的message
        for (ProtoMessage nestedMessage : message.nestedMessages) {
            analyzeMessageDependenciesRecursive(nestedMessage, messagePatterns, messageNames, dependencies);
        }
    }

    private static Set<String> getAllDependencies(String messageName, Map<String, Set<String>> dependencies) {
        Set<String> allDeps = new HashSet<>();
        Set<String> directDeps = dependencies.getOrDefault(messageName, new HashSet<>());

        // 使用队列进行广度优先搜索
        Queue<String> queue = new LinkedList<>(directDeps);
        Set<String> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            String dep = queue.poll();
            if (!visited.contains(dep)) {
                visited.add(dep);
                allDeps.add(dep);

                // 添加该依赖的依赖
                Set<String> depsOfDep = dependencies.getOrDefault(dep, new HashSet<>());
                for (String d : depsOfDep) {
                    if (!visited.contains(d) && !d.equals(messageName)) {
                        queue.offer(d);
                    }
                }
            }
        }

        return allDeps;
    }

    private static void createProtoFile(String outputDirectory, ProtoMessage targetMessage,
                                        List<String> headerLines, List<ProtoMessage> messages,
                                        List<ProtoEnum> enums, Set<String> dependencies,
                                        Map<String, Pattern> messagePatterns,
                                        Map<String, Pattern> enumPatterns) throws IOException {
        String fileName = outputDirectory + File.separator + targetMessage.name + ".proto";

        // 分析message中实际使用的枚举（包括嵌套的enum）
        Set<String> usedEnums = analyzeUsedEnums(targetMessage, enumPatterns);

        // 收集依赖message使用的枚举
        Map<String, ProtoMessage> messageMap = new HashMap<>();
        buildMessageMap(messages, messageMap);

        for (String depName : dependencies) {
            ProtoMessage depMessage = messageMap.get(depName);
            if (depMessage != null) {
                Set<String> depEnums = analyzeUsedEnums(depMessage, enumPatterns);
                usedEnums.addAll(depEnums);
            }
        }

        // 创建依赖message的映射以提高查找效率
        Set<ProtoMessage> dependencyMessages = new HashSet<>();
        for (ProtoMessage msg : messages) {
            if (dependencies.contains(msg.name) && msg.name != null) {
                dependencyMessages.add(msg);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(fileName))) {
            // 写入头部信息
            for (String headerLine : headerLines) {
                writer.write(headerLine);
                writer.newLine();
            }

            // 添加空行分隔
            writer.newLine();

            // 写入依赖的message定义（包括其中嵌套的enum）
            for (ProtoMessage message : dependencyMessages) {
                writeMessage(writer, message);
                // 确保每个message定义后都有空行分隔
                writer.newLine();
            }

            // 写入顶层enum定义
            for (ProtoEnum protoEnum : enums) {
                String enumName = extractEnumName(protoEnum.lines.get(0));
                if (enumName != null && usedEnums.contains(enumName)) {
                    for (String enumLine : protoEnum.lines) {
                        writer.write(enumLine);
                        writer.newLine();
                    }
                    writer.newLine();
                }
            }

            // 写入目标消息定义
            writeMessage(writer, targetMessage);
        }
    }

    private static void writeMessage(BufferedWriter writer, ProtoMessage message) throws IOException {
        // 写入当前message
        for (String messageLine : message.lines) {
            writer.write(messageLine);
            writer.newLine();
        }
    }

    private static void buildMessageMap(List<ProtoMessage> messages, Map<String, ProtoMessage> messageMap) {
        for (ProtoMessage message : messages) {
            if (message.name != null) {
                messageMap.put(message.name, message);
            }
            // 递归处理嵌套message
            buildMessageMapRecursive(message, messageMap);
        }
    }

    private static void buildMessageMapRecursive(ProtoMessage message, Map<String, ProtoMessage> messageMap) {
        for (ProtoMessage nestedMessage : message.nestedMessages) {
            if (nestedMessage.name != null) {
                messageMap.put(nestedMessage.name, nestedMessage);
            }
            // 递归处理更深层的嵌套
            buildMessageMapRecursive(nestedMessage, messageMap);
        }
    }

    private static String extractMessageName(String firstLine) {
        Pattern pattern = Pattern.compile("message\\s+([\\w_]+)");
        Matcher matcher = pattern.matcher(firstLine.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static Set<String> analyzeUsedEnums(ProtoMessage message, Map<String, Pattern> enumPatterns) {
        Set<String> usedEnums = new HashSet<>();

        // 分析当前message及其所有嵌套message
        analyzeUsedEnumsRecursive(message, enumPatterns, usedEnums);

        return usedEnums;
    }

    private static void analyzeUsedEnumsRecursive(ProtoMessage message, Map<String, Pattern> enumPatterns, Set<String> usedEnums) {
        String messageContent = String.join("\n", message.lines);

        for (Map.Entry<String, Pattern> entry : enumPatterns.entrySet()) {
            String enumName = entry.getKey();
            Pattern pattern = entry.getValue();
            if (pattern.matcher(messageContent).find()) {
                usedEnums.add(enumName);
            }
        }

        // 递归分析嵌套message中使用的枚举
        for (ProtoMessage nestedMessage : message.nestedMessages) {
            analyzeUsedEnumsRecursive(nestedMessage, enumPatterns, usedEnums);
        }
    }

    private static String extractEnumName(String firstLine) {
        Pattern pattern = Pattern.compile("enum\\s+([\\w_]+)");
        Matcher matcher = pattern.matcher(firstLine.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // 内部类用于存储消息定义
    private static class ProtoMessage {
        String name;
        List<String> lines = new ArrayList<>();
        List<ProtoMessage> nestedMessages = new ArrayList<>();
        List<ProtoEnum> nestedEnums = new ArrayList<>(); // 嵌套在message中的enum
    }

    // 内部类用于存储枚举定义
    private static class ProtoEnum {
        List<String> lines = new ArrayList<>();
    }
}
