package cn.hongchengq.proto_script_jnl;

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

        // todo 启动模式 四种
        // 0：分割(将一个大proto根据message分割为各个单独的文件)
        // 1：分割(将一个大proto根据先前定义的类型分割为几个文件)
        // 10：合并(根据message分割的文件合并为一个大proto)
        // 11：合并(根据类型分割的文件合并为一个大proto)
        int bootMode = 0;

        // 原始proto文件路径
        String inputFilePath = "proto/input/all_in_one.proto";
        // 映射文件路径
        String replaceMappingFilePath = "proto/input/mapping.tsv";
        // 替换字段后 proto 文件输出路径
        String replaceOutputDirectory = "proto/all_in_one_proto_output";
        // 输出proto文件路径
        String splitOutputDirectory = "proto/dispersible_proto_output";
        // 定义文件头部内容 注意转义
        String[] headerContent = {"option java_package = \"emu.grasscutter.net.proto\";"};
        // 输出文件前永远清理输出文件夹所有内容
        boolean clearOutputFolderForever = true;

        private PacketOpcodesOptional packetOpcodesOptional;
        @Data public static class PacketOpcodesOptional {
            // 是否生成 PacketOpcodes (gc用)
            private boolean createPacketOpcodes = true;
            private String packetHeader = "package emu.grasscutter.net.packet;";
            // PacketOpcodes.java 生成路径
            private String opsOutputDirectory = "proto/packet_opcodes_output";
        }

        private GenerateMessageBlacklistOptional generateMessageBlacklistOptional;
        @Data public static class GenerateMessageBlacklistOptional {
            private boolean enableBlacklist = true;
            private String blacklistFilePath = "proto/input/messageBlacklist.json";
        }

        private GenerateXorFieldConfig generateXorFieldConfig;
        @Data public static class GenerateXorFieldConfig {
            private boolean enableGenerateXorFieldConfig = true;
            private String xorOutputDirectory = "proto/xor_field_config_output";
        }
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

        // 配置忽略未知字段
//        jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        File file = new File("config.json");

        if (!file.isFile()) {
            log.error("{} The file does not exist", file);
            System.exit(0);
        }

        try {
            config = jsonMapper.readValue(file, ConfigBean.class);
        } catch (Exception e) {
            log.error("parse {} error", file, e);
        }
    }
}
