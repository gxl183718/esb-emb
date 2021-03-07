package com.redpill.trans;

import com.alibaba.fastjson.JSON;
import com.redpill.tool.JsonXmlUtil;
import org.mule.api.MuleEventContext;
import org.mule.api.lifecycle.Callable;

import java.util.Map;

public class formToXml implements Callable {
    @Override
    public Object onCall(MuleEventContext muleEventContext) throws Exception {
        System.out.println(muleEventContext.getMessage().getPayloadAsString());
        Map payload = muleEventContext.getMessage().getPayload(Map.class);
        String s = JSON.toJSONString(payload);
        String s1 = JsonXmlUtil.json2Xml(s);
        return s1;
    }
}
