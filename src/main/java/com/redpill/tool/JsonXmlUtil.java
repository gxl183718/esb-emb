package com.redpill.tool;

import org.json.JSONObject;
import org.json.XML;

public class JsonXmlUtil {
    public static String json2Xml(String json) {
        JSONObject jsonObject = new JSONObject(json);
        return XML.toString(jsonObject, null);
    }

    public static void main(String[] args) {
        System.out.println(json2Xml("{\"a\":\"a\",\"b\":\"b\"}"));
    }

    /**
     * json转xml，并指定根节点
     *
     * @param json    json字符串
     * @param tagName 根节点名称
     * @return 带根节点的xml字符串
     */
    public static String json2Xml(String json, String tagName) {
        JSONObject jsonObject = new JSONObject(json);
        return XML.toString(jsonObject, tagName);
    }

    /**
     * xml转json
     *
     * @param xml xml字符串
     * @return json字符串
     */
    public static String xml2Json(String xml) {
        return XML.toJSONObject(xml).toString();
    }

    /**
     * xml转json，并进行格式化
     *
     * @param xml          xml字符串
     * @param indentFactor 换行缩进空格数
     * @return 格式化的json字符串
     */
    public static String xml2Json(String xml, int indentFactor) {
        return XML.toJSONObject(xml).toString(indentFactor);
    }
}
