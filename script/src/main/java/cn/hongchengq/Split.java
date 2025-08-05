package cn.hongchengq;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Split {
    public static void split() {
        // test
        for (String s : ConstProtoType.getAllConstTypes()) {
            System.out.println(s);
        }
    }
}
