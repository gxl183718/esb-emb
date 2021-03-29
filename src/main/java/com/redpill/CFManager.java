package com.redpill;

import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.mule.api.MuleContext;
import org.mule.transport.http.construct.HttpProxy;

import javax.validation.Payload;
import java.io.*;
import java.util.List;
import java.util.UUID;

public class CFManager {

    public static void main(String[] args) throws IOException, JDOMException, InterruptedException {
//        String xml = wsProxy("http://0.0.0.0:8082/cxf/webServices", "http://localhost:8080/cxf/webServices",
//                "http://localhost:8080/cxf/webServices?wsdl");
//        Thread.sleep(5000);
//        System.out.println("execute ");
//        MuleContext muleContext = MuleUtil.exeTask(new String[]{xml});
//
//        Thread.sleep(10000);
//        MuleUtil.close(muleContext);
//
//        MuleUtil.loadAllTasks("conf/cf/");

//        httpProxy("a", "" , "");

    }
    static void anaXml() throws IOException, JDOMException {
        SAXBuilder saxBuilder = new SAXBuilder();
        InputStream is = new FileInputStream(new File("conf/cfTem/wsProxyTem.xml"));
        Document document = saxBuilder.build(is);
        Element rootElement = document.getRootElement();
        List<Element> children = rootElement.getChildren();
        for (Element child : children) {
            if (child.getName().equals("web-service-proxy")){
            }
        }
        saveDocument(document, new File("conf/cf/httpProxy.xml"));
    }

    public static void saveDocument(Document document, File xmlFile) throws IOException {
        XMLOutputter xmlopt = new XMLOutputter();
        FileWriter writer = new FileWriter(xmlFile);
        Format fm = Format.getPrettyFormat();
        xmlopt.setFormat(fm);
        xmlopt.output(document, writer);
        writer.close();
    }

    public static String wsProxy(String inboundAddress, String outboundAddress, String wsdl) throws IOException, JDOMException {
        String xmlName = UUID.randomUUID().toString().replaceAll("-", "");
        SAXBuilder saxBuilder = new SAXBuilder();
        InputStream is = new FileInputStream(new File("conf/cfTem/wsProxyTem.xml"));
        Document document = saxBuilder.build(is);
        Element rootElement = document.getRootElement();
        List<Element> children = rootElement.getChildren();
        for (Element child : children) {
            if (child.getName().equals("web-service-proxy")){
                child.getAttribute("inboundAddress").setValue(inboundAddress);
                child.getAttribute("outboundAddress").setValue(outboundAddress);
                child.getAttribute("wsdlLocation").setValue(wsdl);
                child.getAttribute("name").setValue("ws-proxy"+xmlName);
            }
        }
        String xml =  "wsToWs" + xmlName + ".xml";
        File file = new File("conf/cf/"+xml);
        if (!file.exists())
            file.createNewFile();
        System.out.println(file.exists());
        saveDocument(document, file);
        return "conf/cf/"+xml;
    }
}
