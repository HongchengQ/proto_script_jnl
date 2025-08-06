package cn.hongchengq;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

}
