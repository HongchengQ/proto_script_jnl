# Current features

1. 对 Proto 文件根据规则文件批量替换指定字符, 并在替换行后添加记录注释
2. 把单体式 Proto 切割为模块化 Proto

# Building

**Requirements :**

- [Java SE Development Kits - 21](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
- [Maven](https://maven.apache.org/download.cgi)


```mvn package```

# Run

1. `.\config_example.json`复制并重命名为`config.json`
2. 检查`config.json`
3. Proto 文件和规则(mapping.tsv) 放入 `.\proto\input`目录
4. `java -jar .\target\proto_script_jnl-pm-jar-with-dependencies.jar`

