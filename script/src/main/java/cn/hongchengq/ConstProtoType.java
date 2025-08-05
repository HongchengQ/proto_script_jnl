package cn.hongchengq;

import java.util.Arrays;
import java.util.List;

public class ConstProtoType {
    static final String messageType = "message";
    static final String enumType = "enum";
    static final String oneOfType = "oneof";
    static final String repeatedType = "repeated";
    static final List<String> FieldType = Arrays.asList("bool", "string", "uint32", "uint64", "int32", "int64", "float", "double", "bytes", "fixed32", "fixed64", "sfixed32", "sfixed64");

    static final String dumpedCmdId = "// CmdId:";

    /**
     * 获取所有可以嵌套和被嵌套的类型
     */
    public static List<String> getConstNestingType() {
        return Arrays.asList(messageType, enumType, oneOfType);
    }

    public static List<String> getAllConstTypes() {
        List<String> tempList = getConstNestingType();
        tempList.add(repeatedType);
        tempList.addAll(FieldType);
        return tempList;
    }
}
