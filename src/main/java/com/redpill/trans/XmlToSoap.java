package com.redpill.trans;

import org.mule.api.MuleEventContext;
import org.mule.api.lifecycle.Callable;

public class XmlToSoap implements Callable {
    @Override
    public Object onCall(MuleEventContext muleEventContext) throws Exception {

        //test
        System.out.println(muleEventContext.getMessageAsString());

        String payloadAsString = muleEventContext.getMessage().getPayloadAsString();
        String s = payloadAsString.replaceAll("&lt;TemRoot&gt", "")
                .replaceAll("&lt;/TemRoot&gt", "")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">");

        return s;
    }
}
