package cn.hongchengq;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class Split {
    static String inputProtoFilePath = Config.getConfig().inputProtoFilePath;
    static String outputDirectory = Config.getConfig().outputProtoFilePath;

    static List<ProtoOutputMetadata> messages = new ArrayList<>();

    public static void split() {
        try {
            // 确保输出目录存在
            Files.createDirectories(Paths.get(outputDirectory));

            // 存储文件头部信息（syntax、package、import等）
            List<String> headerLines = new ArrayList<>();

            parseProtoFileLines();

            log.info("Proto文件分割完成，共生成 {} 个文件", 0);
        } catch (IOException e) {
            log.error("分割proto文件时出错: ", e);
        }
    }

    public static void parseProtoFileLines() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(inputProtoFilePath));

        // message嵌套余量: 当行内出现可以嵌套或被嵌套的类型时 +1, 匹配到 "}" 符号时 -1, 归零时生成一个新的message
        int messageNestingAllowance = 0;

        int lastCmdId = 0;

        for (String line : lines) {
            // 移除每行前导和尾随空格
            String trimmedLine = line.trim();

            if (trimmedLine.startsWith(ConstProtoType.dumpedCmdId + " ")) {
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
                            ProtoOutputMetadata newMessage = new ProtoOutputMetadata();
                            newMessage.name = messageName;
                            newMessage.cmdId = lastCmdId;
                            // 向 messages 添加 newMessage
                            messages.add(newMessage);

                            lastCmdId = 0;
                        }
                        ++messageNestingAllowance;
                    }
                    break;
                }
            }

            // 根据符号判断已经进入方法末尾
            if (trimmedLine.equals("}")) {
                --messageNestingAllowance;
            }

            // messages已录入，并且录入不是cmdid那一行
            if (!messages.isEmpty() && !trimmedLine.startsWith(ConstProtoType.dumpedCmdId + " ")) {
                messages.getLast().lines.add(line);
            }

        }

        // debug
        StringBuilder s = new StringBuilder();
        for (ProtoOutputMetadata p : messages) {
            s.append(p.name);
            s.append(" ").append(p.cmdId);
            s.append("\n");
            p.lines.forEach(ss -> s.append(ss).append("\n"));
            s.append("\n\n");
        }
        System.out.println(s);
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


    /**
     * proto 输出信息元数据
     * 这里的数据将写入输出文件
     */
    private static class ProtoOutputMetadata {
        String name;                                                    // 输出文件名
        int cmdId = 0;                                                  // CmdId
        List<String> lines = new ArrayList<>();                         // 自身包含的行
        List<String> needImportMessage = new ArrayList<>();             // 需要import的message
        List<ProtoOutputMetadata> nestedMessages = new ArrayList<>();   // 自身嵌套类
    }

}

