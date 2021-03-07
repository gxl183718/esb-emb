package com.redpill;

import java.util.*;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.xml.XMLSerializer;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

public class TransUtil {
	public static Map<String,Object> parseJSONToMap(String jsonStr){
		Map<String, Object> map = new HashMap<>();
		JSONObject jsonObject = JSONObject.fromObject(jsonStr);
		for (Object o : jsonObject.keySet()) {
			Object v = jsonObject.get(o);
			if (v instanceof JSONArray){
				List<Map<String, Object>> list = new ArrayList<>();
				Iterator<JSONObject> iterator = ((JSONArray) v).iterator();
				while (iterator.hasNext()) {
					JSONObject jsonObject1 = iterator.next();
					list.add(parseJSONToMap(jsonObject1.toString()));
				}
				map.put(o.toString(), list);
			}else {
				map.put(o.toString(), v);
			}
		}
		return map;
	}
	public static String parseMapToXML(Map<String,Object> map,String method,String nameSpace) {
		if (map == null)
			return null;
		JSONObject json = JSONObject.fromObject(map);
		XMLSerializer ser = new XMLSerializer();
		ser.setRootName("tem:" + method);
		ser.write(json);
		ser.setNamespace("tem", nameSpace);
		ser.setTypeHintsEnabled(false);
		String result = ser.write(json, "UTF-8");
		return result;
	}

		public static Map<String, Object> multilayerXmlToMap(String xml) {
		    Document doc = null;
		    try {
		        doc = DocumentHelper.parseText(xml);
		    } catch (DocumentException e) {
		        e.printStackTrace();
		    }
		    Map<String, Object> map = new HashMap<>();
		    if (null == doc) {
		        return map;
		    }
		    // 获取根元素
		    Element rootElement = doc.getRootElement();
		    recursionXmlToMap(rootElement,map);
		    return map;
		}

		/**
		 * multilayerXmlToMap核心方法，递归调用
		 * 
		 * @param element 节点元素
		 * @param outmap 用于存储xml数据的map
		 */
		private static void recursionXmlToMap(Element element, Map<String, Object> outmap) {
		    // 得到根元素下的子元素列表
		    List<Element> list = element.elements();
		    int size = list.size();
		    if (size == 0) {
		        // 如果没有子元素,则将其存储进map中
		        outmap.put(element.getName(), element.getTextTrim());
		    } else {
		        // innermap用于存储子元素的属性名和属性值
		        Map<String, Object> innermap = new HashMap<>();
		        // 遍历子元素
		        list.forEach(childElement -> recursionXmlToMap(childElement, innermap));
		        outmap.put(element.getName(), innermap);
		    }
		}
}
