package cn.hongchengq;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {
    public static final String PROJECT_ADDRESS = "https://gitcode.com/HongchengQ/proto_script_jnl";
    public static String usedTime;

    public static void main(String[] args) {
        // 加载 config
        Config.JsonLoader();

        // 获取当前时间
        LocalDateTime dateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-dd-MM HH:mm:ss");
        usedTime = dateTime.format(formatter);

        // 字段替换
        String path = Replace.start();

        // 分割文件
        Split.start(path);
    }
}