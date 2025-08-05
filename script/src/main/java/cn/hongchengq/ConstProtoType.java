package cn.hongchengq;

import java.util.Arrays;
import java.util.List;

public class ConstProtoType {
    static final String messageType = "message";
    static final String enumType = "enum";
    static final String repeatedType = "repeated";
    static final List<String> FieldType = Arrays.asList("bool", "string", "uint32", "uint64", "int32", "int64", "float", "double", "bytes", "fixed32", "fixed64", "sfixed32", "sfixed64");

    public static List<String> getAllConstTypes() {
        List<String> tempList = Arrays.asList(messageType, enumType, repeatedType);
        tempList.addAll(FieldType);
        return tempList;
    }
}
