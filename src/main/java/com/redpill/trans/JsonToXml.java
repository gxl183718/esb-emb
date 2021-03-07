package com.redpill.trans;

import com.redpill.tool.JsonXmlUtil;
import org.mule.api.MuleEventContext;
import org.mule.api.lifecycle.Callable;

public class JsonToXml implements Callable {
    @Override
    public Object onCall(MuleEventContext muleEventContext) throws Exception {
        String payloadAsString = muleEventContext.getMessage().getPayloadAsString();
        String s = JsonXmlUtil.json2Xml(payloadAsString);
        return s;
    }
}
