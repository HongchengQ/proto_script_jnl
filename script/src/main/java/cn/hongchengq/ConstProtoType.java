package cn.hongchengq;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConstProtoType {
    private static final String messageType = "message";
    private static final String enumType = "enum";
    private static final String oneOfType = "oneof";
    private static final String repeatedType = "repeated";
    @Getter
    private static final List<String> FieldType = Arrays.asList("bool", "string", "uint32", "uint64", "int32", "int64", "float", "double", "bytes", "fixed32", "fixed64", "sfixed32", "sfixed64");
    @Getter
    private static final String mapType = "map<.*, .*>";
    @Getter
    private static final String dumpedCmdId = "// CmdId:";

    /**
     * 获取所有可以嵌套和被嵌套的类型
     */
    public static List<String> getConstNestingType() {
        return Arrays.asList(messageType, enumType, oneOfType);
    }

    /**
     * 获取系统定义的字段类型
     * @return
     */
    public static List<String> getConstFieldType() {
        List<String> tempList = new ArrayList<>();

        tempList.add(repeatedType);
        tempList.add(mapType);
        tempList.addAll(FieldType);

        return tempList;
    }

    /**
     * 获取系统定义的所有类型
     * @return
     */
    public static List<String> getAllConstTypes() {
        List<String> tempList = new ArrayList<>(getConstNestingType());
        tempList.addAll(getConstFieldType());

        return tempList;
    }
}
