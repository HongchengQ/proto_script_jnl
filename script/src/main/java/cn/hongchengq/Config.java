package cn.hongchengq;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Slf4j
public class Config {
    @Getter private static ConfigBean config;

    @Data
    public static class ConfigBean {
        // proto对应游戏版本 标记用
        String gameVersion = "1.0.0";

        // todo
        // 启动模式 四种
        // 0：分割(将一个大proto根据message分割为各个单独的文件)
        // 1：分割(将一个大proto根据先前定义的类型分割为几个文件)
        // 10：合并(根据message分割的文件合并为一个大proto)
        // 11：合并(根据类型分割的文件合并为一个大proto)
        int bootMode = 0;

        // 原始proto文件路径
        String inputFilePath = "proto_test/input/test.proto";
        // 映射文件路径
        String replaceMappingFilePath = "proto_test/input/mapping.tsv";
        // 替换字段后 proto 文件输出路径
        String replaceOutputDirectory = "proto_test/all_in_one_proto_output";
        // 输出proto文件路径
        String splitOutputDirectory = "proto_test/dispersible_proto_output";
    }

    /**
     * 初始化方法 加载config
     */
    public static void JsonLoader() {
        JsonFactory jsonFactory = JsonFactory.builder()
                .configure(JsonReadFeature.ALLOW_JAVA_COMMENTS, true)// 配置特性
                .configure(JsonReadFeature.ALLOW_TRAILING_COMMA, true)// 配置特性
                .build();
        JsonMapper jsonMapper = new JsonMapper(jsonFactory);

        try {
            File file = new File("config.json");
            config = jsonMapper.readValue(file, ConfigBean.class);
        } catch (Exception e) {
            log.error("解析 config.json 失败", e);
        }
    }
}
