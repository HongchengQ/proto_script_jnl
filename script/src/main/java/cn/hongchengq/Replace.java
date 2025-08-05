package cn.hongchengq;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
public class Replace {
    public static void replace() {
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
                log.debug(String.valueOf(record));
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
                    log.error("读取到非正常 recordNumber: {}", record.getRecordNumber(), e);
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
                    log.debug("混淆字段 '{}' 重复出现但映射一致，将进行替换", obfuscated);
                } else {
                    // 重复且映射不一致的字段不使用
                    log.warn("警告: 混淆字段 '{}' 出现了 {} 次且映射不一致，将不进行替换", obfuscated, occurrence);
                }
            }

            // 对validMapping中的key进行处理，如果包含空格则删除
            Map<String, String> processedMapping = new HashMap<>();

            for (Map.Entry<String, String> entry : validMapping.entrySet()) {
                String obfuscated = entry.getKey();
                String deobfuscated = entry.getValue();

                if (obfuscated.contains(" ")) {
                    String cleanedObfuscated = obfuscated.replace(" ", "");
                    processedMapping.put(cleanedObfuscated, deobfuscated);
                    log.info("清理了混淆字段 '{}' 中的空格，清理后为 '{}'", obfuscated, cleanedObfuscated);
                } else {
                    processedMapping.put(obfuscated, deobfuscated);
                }
            }

            // 构建一个包含所有混淆字段的复合正则表达式
            StringBuilder patternBuilder = new StringBuilder();
            patternBuilder.append("\\b(");
            List<String> obfuscatedKeys = new ArrayList<>(processedMapping.keySet());
            for (int i = 0; i < obfuscatedKeys.size(); i++) {
                if (i > 0) {
                    patternBuilder.append("|");
                }
                patternBuilder.append(Pattern.quote(obfuscatedKeys.get(i)));
            }
            patternBuilder.append(")\\b");

            Pattern combinedPattern = Pattern.compile(patternBuilder.toString());

            // 获取proto文件路径
            String protoFilePath = Config.getConfig().inputProtoFilePath;
            String outputFilePath = protoFilePath.replace(".proto", "_deobfuscated.proto");

            try (BufferedReader bufferedReader = Files.newBufferedReader(Paths.get(protoFilePath));
                 BufferedWriter bufferedWriter = Files.newBufferedWriter(Paths.get(outputFilePath))) {

                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    // 用于记录该行替换的字段映射
                    Map<String, String> replacementsInLine = new HashMap<>();

                    String processedLine = combinedPattern.matcher(line).replaceAll(match -> {
                        String matchedKey = match.group(1);
                        String replacement = processedMapping.getOrDefault(matchedKey, matchedKey);
                        // 记录替换的字段
                        if (!matchedKey.equals(replacement)) {
                            replacementsInLine.put(matchedKey, replacement);
                        }
                        return replacement;
                    });

                    // 如果有替换发生，则在行尾添加注释
                    if (!replacementsInLine.isEmpty()) {
                        StringBuilder comment = new StringBuilder(" // ");
                        comment.append("[");
                        boolean first = true;
                        for (Map.Entry<String, String> replacement : replacementsInLine.entrySet()) {
                            if (!first) {
                                comment.append(", ");
                            }
                            comment.append(replacement.getKey())
                                    .append("->")
                                    .append(replacement.getValue());
                            first = false;
                        }
                        comment.append("]");
                        processedLine += comment.toString();
                    }

                    bufferedWriter.write(processedLine);
                    bufferedWriter.newLine();
                }

                log.info("mapping 应用完成，输出文件: {}", outputFilePath);
            } catch (IOException e) {
                log.error("生成文件时出错:", e);
            }

        } catch (IOException e) {
            log.error(String.valueOf(e));
        }
    }
}
