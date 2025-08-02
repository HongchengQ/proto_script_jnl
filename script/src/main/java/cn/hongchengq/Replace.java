package cn.hongchengq;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

// todo 替换后在行尾添加一条注释 内容为混淆字段->解混淆
public class Replace {
    public static void main(String[] args) {
        String tsvFilePath = Config.getConfig().mappingFilePath;

        // 用于存储混淆字段到解混淆字段的映射
        Map<String, String> mapping = new HashMap<>();
        // 用于统计每个混淆字段出现次数
        Map<String, Integer> count = new HashMap<>();
        // 用于存储重复但映射一致的字段
        Set<String> consistentDuplicates = new HashSet<>();

        try (
                Reader reader = new FileReader(tsvFilePath);
                CSVParser csvParser = CSVFormat.TDF.withFirstRecordAsHeader().parse(reader)
        ) {
            // 遍历TSV文件中的每一行
            for (CSVRecord record : csvParser) {
//                System.out.println("debug:\n" + record);
                try {
                    String obfuscated = record.get(0);
                    String deobfuscated = record.get(1);

                    // 检查是否已存在该混淆字段
                    if (mapping.containsKey(obfuscated)) {
                        // 如果已存在，检查映射是否一致
                        if (mapping.get(obfuscated).equals(deobfuscated)) {
                            // 映射一致，添加到一致重复集合中
                            consistentDuplicates.add(obfuscated);
                        }
                    } else {
                        // 存储映射关系
                        mapping.put(obfuscated, deobfuscated);
                    }

                    // 统计混淆字段出现次数
                    count.put(obfuscated, count.getOrDefault(obfuscated, 0) + 1);

                } catch (Exception e) {
                    System.out.println("读取到非正常recordNumber" + record.getRecordNumber());
                }
            }

            // 处理映射关系
            Map<String, String> validMapping = new HashMap<>();

            for (Map.Entry<String, Integer> entry : count.entrySet()) {
                String obfuscated = entry.getKey();
                int occurrence = entry.getValue();

                if (occurrence == 1) {
                    // 只出现一次的字段直接使用
                    validMapping.put(obfuscated, mapping.get(obfuscated));
                } else if (consistentDuplicates.contains(obfuscated)) {
                    // 重复但映射一致的字段也使用
                    validMapping.put(obfuscated, mapping.get(obfuscated));
                    System.out.println("信息: 混淆字段 '" + obfuscated + "' 重复出现但映射一致，将进行替换");
                } else {
                    // 重复且映射不一致的字段不使用
                    System.out.println("警告: 混淆字段 '" + obfuscated + "' 出现了 " + occurrence + " 次且映射不一致，将不进行替换");
                }
            }

            // 对validMapping中的key进行处理，如果包含空格则删除
            Map<String, String> processedMapping = new HashMap<>();
            for (Map.Entry<String, String> entry : validMapping.entrySet()) {
                String obfuscated = entry.getKey();
                String deobfuscated = entry.getValue();

                // 如果key包含空格，则删除空格
                if (obfuscated.contains(" ")) {
                    String cleanedObfuscated = obfuscated.replace(" ", "");
                    processedMapping.put(cleanedObfuscated, deobfuscated);
                    System.out.println("信息: 清理了混淆字段 '" + obfuscated + "' 中的空格，清理后为 '" + cleanedObfuscated + "'");
                } else {
                    processedMapping.put(obfuscated, deobfuscated);
                }
            }

            // 获取proto文件路径
            String protoFilePath = Config.getConfig().inputProtoFilePath;
            String outputFilePath = protoFilePath.replace(".proto", "_deobfuscated.proto");

            try {
                // 读取proto文件
                List<String> lines = Files.readAllLines(Paths.get(protoFilePath));

                // 创建输出文件
                List<String> outputLines = new ArrayList<>();

                // 逐行处理proto文件
                for (String line : lines) {
                    String processedLine = line;

                    // 对每一行检查是否包含混淆字段并进行替换
                    for (Map.Entry<String, String> entry : processedMapping.entrySet()) {
                        String obfuscated = entry.getKey();
                        String deobfuscated = entry.getValue();

                        // 使用正则表达式匹配整个单词，避免部分匹配
                        processedLine = processedLine.replaceAll("\\b" + Pattern.quote(obfuscated) + "\\b", deobfuscated);
                    }

                    outputLines.add(processedLine);
                }

                // 写入输出文件
                Files.write(Paths.get(outputFilePath), outputLines);
                System.out.println("解混淆完成，输出文件: " + outputFilePath);

            } catch (IOException e) {
                System.err.println("处理proto文件时出错: " + e.getMessage());
                e.printStackTrace();
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
