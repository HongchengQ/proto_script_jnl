package cn.hongchengq.proto_script_jnl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
public class Tools {
    /**
     * 删除目录中的所有内容，保留目录本身
     *
     * @param directory 要清空的目录路径
     * @throws IOException IO异常
     */
    public static void deleteDirectoryContents(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .filter(path -> !path.equals(directory))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException("无法删除文件: " + path, e);
                        }
                    });
        }
    }

    /**
     * 从指定JSON文件中读取所有值为true的键名
     *
     * @param filePath JSON文件的路径
     * @return 包含所有值为true的键名的列表，如果解析失败则返回null
     */
    public static List<String> getJsonTrueKeys(String filePath) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode;

        // 读取并解析JSON文件
        try {
            rootNode = mapper.readTree(new File(filePath));
        } catch (IOException e) {
            log.error("parse {} error", filePath, e);
            return null;
        }

        List<String> trueKeys = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fieldIterator = rootNode.properties().iterator();

        // 遍历JSON对象的所有属性，筛选出值为true的键
        fieldIterator.forEachRemaining(entry -> {
            if (entry.getValue().asBoolean()) {
                trueKeys.add(entry.getKey());
            }
        });

        return trueKeys;
    }

    /**
     * 判断 list 是否有重复项
     */
    public static boolean hasDuplicates(List<Integer> list) {
        Set<Integer> set = new HashSet<>(list);
        return set.size() < list.size();
    }

}
