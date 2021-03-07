package com.redpill;

import org.mule.api.MuleMessage;
import org.mule.api.transformer.TransformerException;
import org.mule.transformer.AbstractMessageTransformer;

public class HttpTrans extends AbstractMessageTransformer {
    @Override
    public Object transformMessage(MuleMessage muleMessage, String s) throws TransformerException {
        muleMessage.getInboundProperty("http.query.params");
        return null;
    }
}
