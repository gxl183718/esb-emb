package com.redpill;

import org.mule.api.MuleEventContext;
import org.mule.api.MuleMessage;
import org.mule.api.lifecycle.Callable;
import org.mule.api.transformer.TransformerException;
import org.mule.transformer.AbstractMessageTransformer;

import java.util.Map;

public class TransDemo implements Callable {

//    @Override
    public Object transformMessage(MuleMessage muleMessage, String s) throws TransformerException {
        System.out.println("...................." + s);
        System.out.println(muleMessage.getPayload().getClass()+"haha");

        try {
            System.out.println(new String(muleMessage.getPayloadAsBytes()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        Map map = null;
        String strXml = null;
        String requestMod = muleMessage.getInboundProperty("http.method");
        if ("GET".equals(requestMod)){
            map = muleMessage.getInboundProperty("http.query.params");
            strXml = TransUtil.parseMapToXML(map, "sayHello", "http://wbtest.com");
        }else if ("POST".equals(requestMod)){
            map = (Map)muleMessage.getPayload();

        }
        muleMessage.setOutboundProperty("Content-Type", "text/xml;charset=UTF-8");
        muleMessage.setOutboundProperty("Connection", "Keep-Alive");
        String ss =  "<soapenv:Envelope " +
                "xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "xmlns:ucp=\"http://ucp.xxx.com\">\n" +
                "   <soapenv:Header/>\n" +
                "   <soapenv:Body>\n" +
                "      <ucp:sayGodBye>\n" +
                "         <name>gg</name>\n" +
                "         <age>11</age>\n" +
                "      </ucp:sayGodBye>\n" +
                "   </soapenv:Body>\n" +
                "</soapenv:Envelope>";
        muleMessage.setPayload(ss);
        return muleMessage;
    }

    @Override
    public Object onCall(MuleEventContext muleEventContext) throws Exception {
        MuleMessage muleMessage = muleEventContext.getMessage();
        System.out.println("....................");
        System.out.println(muleMessage.getPayload().getClass()+"haha");
        System.out.println(new String(muleMessage.getPayloadAsBytes()));
        for (String outboundAttachmentName : muleMessage.getOutboundAttachmentNames()) {
            System.out.println( "name" + outboundAttachmentName);
        }
        muleMessage.setOutboundProperty("Content-Type", "text/xml;charset=UTF-8");
        muleMessage.setOutboundProperty("Connection", "Keep-Alive");
        muleMessage.setOutboundProperty("Accept-Encoding", "gzip, deflate, br");
        muleMessage.setOutboundProperty("Accept", "*/*");
        muleMessage.setOutboundProperty("Content-Length", "<calculated when request is sent>");

        String ss =  "<soapenv:Envelope " +
                "xmlns:ucp=\"http://ucp.xxx.com\">\n" +
                "   <soapenv:Header/>\n" +
                "   <soapenv:Body>\n" +
                "      <ucp:sayGodBye>\n" +
                "         <name>gg</name>\n" +
                "         <age>11</age>\n" +
                "      </ucp:sayGodBye>\n" +
                "   </soapenv:Body>\n" +
                "</soapenv:Envelope>";
        muleMessage.setPayload(ss);
        muleMessage.setOutboundProperty("http.query.method", "POST");
        System.out.println(muleMessage.getPayloadAsString());
        System.out.println("method" + (String)muleMessage.getOutboundProperty("http.query.method"));
        return muleMessage;
    }
}
