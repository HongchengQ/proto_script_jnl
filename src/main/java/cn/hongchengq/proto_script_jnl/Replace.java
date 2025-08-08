package cn.hongchengq.proto_script_jnl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
public class Replace {
    public static String start() {
        String tsvFilePath = Config.getConfig().replaceMappingFilePath;

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
                    String obfuscated;
                    String deobfuscated;

                    if (record.size() < 2 || record.get(0) == null || record.get(1) == null) {
                        log.warn("tsv 文件中有一处元素小于 2，不进行记录");
                        continue;
                    }

                    obfuscated = record.get(0);
                    deobfuscated = record.get(1);

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
                    log.error(String.valueOf(e));
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
                    log.warn("混淆字段 '{}' 出现了 {} 次且映射不一致，将不进行替换", obfuscated, occurrence);
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

            // 构建Trie树用于匹配
            TrieNode root = new TrieNode();
            for (Map.Entry<String, String> entry : processedMapping.entrySet()) {
                addToTrie(root, entry.getKey(), entry.getValue());
            }

            // 获取proto文件路径
            String inputFilePath = Config.getConfig().inputFilePath;
            String outputPath = Config.getConfig().replaceOutputDirectory;
            Path outputPathDir = Paths.get(outputPath);
            String outputFilePath = outputPath + "/replace_output.proto";

            // 确保输出目录存在
            Files.createDirectories(outputPathDir);

            if (Config.getConfig().isClearOutputFolderForever()) {
                // 删除输出目录下的所有内容
                Tools.deleteDirectoryContents(outputPathDir);
            }

            try (BufferedReader bufferedReader = Files.newBufferedReader(Paths.get(inputFilePath));
                 BufferedWriter bufferedWriter = Files.newBufferedWriter(Paths.get(outputFilePath))) {

                bufferedWriter.write("// " + Main.PROJECT_ADDRESS + "\n");
                bufferedWriter.write("// usedTime: " + Main.usedTime + "\n");
                bufferedWriter.newLine();

                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    String processedLine = replaceUsingTrie(line, root);
                    bufferedWriter.write(processedLine);
                    bufferedWriter.newLine();
                }

                log.info("mapping 应用完成，输出文件: {}", outputFilePath);
                return outputFilePath;
            } catch (IOException e) {
                log.error("生成文件时出错:", e);
            }

        } catch (IOException e) {
            log.error(String.valueOf(e));
        }
        return null;
    }

    /**
     * 使用Trie树进行字符串替换
     */
    private static String replaceUsingTrie(String line, TrieNode root) {
        if (line.isEmpty()) {
            return line;
        }

        StringBuilder result = new StringBuilder(line.length() * 2);
        Map<String, String> replacements = new HashMap<>();

        int i = 0;
        while (i < line.length()) {
            TrieNode current = root;
            int matchEnd = -1;
            String replacementValue = null;

            // 从当前位置开始查找最长匹配
            for (int j = i; j < line.length(); j++) {
                char c = line.charAt(j);
                TrieNode next = current.children.get(c);

                if (next == null) {
                    break;
                }

                current = next;
                if (current.isEndOfWord) {
                    matchEnd = j;
                    replacementValue = current.replacement;
                }
            }

            // 如果找到匹配项
            if (matchEnd != -1) {
                String matchedKey = line.substring(i, matchEnd + 1);
                result.append(replacementValue);
                if (!matchedKey.equals(replacementValue)) {
                    replacements.put(matchedKey, replacementValue);
                }
                i = matchEnd + 1;
            } else {
                // 没有匹配，复制当前字符
                result.append(line.charAt(i));
                i++;
            }
        }

        // 添加注释（如果有的话）
        if (!replacements.isEmpty()) {
            result.append(" /*[");
            boolean first = true;
            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                if (!first) {
                    result.append(", ");
                }
                result.append(replacement.getKey())
                        .append("->")
                        .append(replacement.getValue());
                first = false;
            }
            result.append("]*/");
        }

        return result.toString();
    }

    /**
     * 将键值对添加到Trie树中
     */
    private static void addToTrie(TrieNode root, String key, String value) {
        TrieNode current = root;
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            current.children.putIfAbsent(ch, new TrieNode());
            current = current.children.get(ch);
        }
        current.isEndOfWord = true;
        current.replacement = value;
    }

    /**
     * Trie树节点
     */
    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEndOfWord = false;
        String replacement;
    }
}
