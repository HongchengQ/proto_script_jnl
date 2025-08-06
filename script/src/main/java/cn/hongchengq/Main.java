package cn.hongchengq;

public class Main {
    public static void main(String[] args) {
        Config.JsonLoader();
        String path = Replace.start();
        Split.start(path);
    }
}