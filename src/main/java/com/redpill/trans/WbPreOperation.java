package com.redpill.trans;

import java.util.Map;

import com.redpill.tool.LogTool;
import org.mule.api.MuleEventContext;
import org.mule.api.lifecycle.Callable;
import org.mule.api.transport.PropertyScope;

import com.alibaba.fastjson.JSON;

public class WbPreOperation implements Callable {
    @Override
    public Object onCall(MuleEventContext muleEventContext) throws Exception {
    	String operationKey = "varOperation";
    	String paramKey = "one";
        //test
        String soapRequest = muleEventContext.getMessageAsString();
        LogTool.logInfo(2, "soap request:" + soapRequest);
        String ss[] = soapRequest.split(":");
        String operation = ss[1].split(" ")[0];
        LogTool.logInfo(2, "soap operation:" + operation);
        muleEventContext.getMessage().setInvocationProperty(operationKey, operation);
        String param = soapRequest.split("<"+paramKey+">")[1];
        param = param.split("</"+paramKey+">")[0];
        Map<String, Object> map = (Map)JSON.parse(param);
        for(Map.Entry<String, Object> en : map.entrySet()) {
        	System.out.println(en.getKey() + ":" + en.getValue());
        	muleEventContext.getMessage().setInvocationProperty(en.getKey(), en.getValue());
        	System.out.println(muleEventContext.getMessage().getInvocationProperty(en.getKey()).toString());
        }

        return muleEventContext;
    }
	
}
