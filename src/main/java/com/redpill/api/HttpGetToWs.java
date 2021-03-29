package com.redpill.api;

import com.redpill.tool.LogTool;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.config.ConfigurationException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.config.spring.SpringXmlConfigurationBuilder;
import org.mule.context.DefaultMuleContextFactory;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.redpill.CFManager.saveDocument;

public class HttpGetToWs implements MuleTask {

    private static final String xmlTemplatePath = "httpGetToWsTem.xml";
    private String xmlPre = "hgtw-";
    private String xmlName;
    private String xsltPre = "hgtw-xslt-";
    private String xmlPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "flow/";
    private String xsltPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "xslt/";
    private String xsltName;
    private static final DefaultMuleContextFactory defaultMuleContextFactory = new DefaultMuleContextFactory();
    private SpringXmlConfigurationBuilder configBuilder;
    public String taskId;
    public MuleContext muleContext;

    private boolean toJson;

    private String listenerHost;
    private Integer listenerPort;
    private String listenerPath;

    private String wsService;
    private String wsPort;
    private String wsServiceAddr;
    private String wsWsdlLocation;
    private String operation;
    private String namespace;
    private String namespaceUrl;
    private String xsltTem;

    public HttpGetToWs(String taskId, String listenerHost, Integer listenerPort, String listenerPath,
                       String wsService, String wsPort, String wsServiceAddr, String wsWsdlLocation,
                       String operation, String namespace, String namespaceUrl, Map<String, String> dataMapper, boolean toJson) {
        this.toJson = toJson;
        this.taskId = taskId;
        this.xmlName = xmlPre + taskId + ".xml";
        this.listenerHost = listenerHost;
        this.listenerPort = listenerPort;
        this.listenerPath = listenerPath;
        this.wsService = wsService;
        this.wsPort = wsPort;
        this.wsServiceAddr = wsServiceAddr;
        this.wsWsdlLocation = wsWsdlLocation;
        this.operation = operation;
        this.namespace = namespace;
        this.namespaceUrl = namespaceUrl;
        this.dataMapper = dataMapper;
        this.xsltName = xsltPre + taskId + ".xslt";
    }



    private Map<String, String> dataMapper = new HashMap<>();



    @Override
    public void initTask() {
        LogTool.logInfo(2, "init task " + taskId);
//        StringBuilder sbParams = new StringBuilder();
//        StringBuilder sbParamXml = new StringBuilder();
//
//        for (String value : dataMapper.values()) {
//            sbParams.append("    <xsl:param name=\""+value+"\" />\n");
//            sbParamXml.append("            <"+value+">\n" +
//                    "                <xsl:value-of select=\"$"+value+"\" />\n" +
//                    "            </"+value+">\n");
//        }
//        this.xsltTem = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
//                "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"  \n" +
//                "                xmlns:"+namespace+"=\""+namespaceUrl+"\" \n" +
//                "                version=\"1.0\">\n" +
//                "    <xsl:output method=\"xml\" encoding=\"UTF-8\" indent=\"yes\" />\n" +
//                    sbParams.toString()+
//                "    <xsl:template match=\"/\">\n" +
//                "        <"+namespace+":"+ operation +">\n" +
//                    sbParamXml.toString() +
//                "        </"+namespace+":"+ operation + ">\n" +
//                "    </xsl:template>\n" +
//                "</xsl:stylesheet>";
//        File file = new File(xsltPath + xsltName);
//        if (!file.exists()) {
//            try {
//                file.createNewFile();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//        try {
//            FileUtils.writeStringToFile(file, xsltTem);
//        } catch (IOException e) {
//            LogTool.logInfo(1, " create xslt error, when write to " + xsltPath + xsltName);
//            e.printStackTrace();
//        }

        try {
            getToWs();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JDOMException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean executeTask() {
        LogTool.logInfo(2, "execute task " + taskId);
        try {
            configBuilder = new SpringXmlConfigurationBuilder(xmlPath + xmlName);
            muleContext = defaultMuleContextFactory.createMuleContext(configBuilder);
            muleContext.start();
            String id = muleContext.getConfiguration().getId();
            String taskInfo = RedisUtils.redisPool.jedis(jedis -> {
                jedis.hset(MuleConfig.muleMonitor + taskId, MuleConfig.hostIp, id);
                return null;
            });
            return true;
        } catch (ConfigurationException e) {
            LogTool.logInfo(1, taskId + " start error.");
            e.printStackTrace();
        } catch (InitialisationException e) {
            LogTool.logInfo(1, taskId + " start error.");
            e.printStackTrace();
        } catch (MuleException e) {
            LogTool.logInfo(1, taskId + " start error.");
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void closeTask() {
        if (null != muleContext){
            try {
                muleContext.stop();
                RedisUtils.redisPool.jedis(jedis -> {
                    jedis.del(MuleConfig.muleMonitor + taskId);
                    return null;
                });
            } catch (MuleException e) {
                LogTool.logInfo(1, taskId + " stop error.");
                e.printStackTrace();
            }
            muleContext.dispose();
        }
        LogTool.logInfo(2, "stop task " + taskId);
    }

    @Override
    public void removeTask() {
        if (muleContext != null){
            this.closeTask();
        }
        File file = new File(xmlPath + xmlName);
        file.delete();
        LogTool.logInfo(2, "rm task " + taskId);
    }

    public String getToWs() throws IOException, JDOMException {
        SAXBuilder saxBuilder = new SAXBuilder();
        InputStream is = ClassLoader.getSystemResourceAsStream(xmlTemplatePath);
        Document document = saxBuilder.build(is);
        Element rootElement = document.getRootElement();
        List<Element> children = rootElement.getChildren();
        String listenerConf = taskId + "-lc-conf";
        String wsConf = taskId + "-ws-conf";
        for (Element child : children) {
            if (child.getName().equals("listener-config")){
                child.setAttribute("name", listenerConf);
                child.getAttribute("host").setValue(listenerHost);
                child.getAttribute("port").setValue(String.valueOf(listenerPort));
                child.getAttribute("basePath").setValue(listenerPath);
            }else if (child.getName().equals("consumer-config")){
                child.setAttribute("name", wsConf);
                child.getAttribute("service").setValue(wsService);
                child.getAttribute("port").setValue(wsPort);
                child.getAttribute("serviceAddress").setValue(wsServiceAddr);
                child.getAttribute("wsdlLocation").setValue(wsWsdlLocation);
            } else if (child.getName().endsWith("flow")){
                child.setAttribute("name", taskId + "-flow");
                Namespace mule = Namespace.getNamespace("mulexml", "http://www.mulesoft.org/schema/mule/xml");
                Namespace http = Namespace.getNamespace("http", "http://www.mulesoft.org/schema/mule/http");
                Namespace ws = Namespace.getNamespace("ws", "http://www.mulesoft.org/schema/mule/ws");

                child.getChild("listener", http).setAttribute("config-ref", listenerConf);
                child.getChild("xslt-transformer", mule).setAttribute("xsl-file", xsltPath + xsltName);
                dataMapper.forEach((k,v)->{
                    Element element = new Element("context-property", mule);
                    element.setAttribute("key", k).setAttribute("value", "#[message.inboundProperties.'http.query.params'."+v + "]");
                    child.getChild("xslt-transformer", mule).addContent(element);
                });
                child.getChild("consumer", ws).setAttribute("config-ref", wsConf).setAttribute("operation", operation);
                if (!toJson){
                    Namespace jsonNs = Namespace.getNamespace("json", "http://www.mulesoft.org/schema/mule/json");
                    child.removeChild("xml-to-json-transformer", jsonNs);
                }
            }
        }
        File file = new File(xmlPath+xmlName);
        if (!file.exists())
            file.createNewFile();
        saveDocument(document, file);
        return xmlPath+xmlName;
    }
}
