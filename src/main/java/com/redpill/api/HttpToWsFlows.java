package com.redpill.api;

import com.redpill.entity.HttpWsEntity;
import com.redpill.server.AnaTask;
import com.redpill.tool.LogTool;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import org.apache.commons.io.FileUtils;
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
import java.util.List;
import java.util.Map;

import static com.redpill.CFManager.saveDocument;

public class HttpToWsFlows implements MuleTask {
    private static final DefaultMuleContextFactory defaultMuleContextFactory = new DefaultMuleContextFactory();
    private SpringXmlConfigurationBuilder configBuilder;

    private static final String xmlTemplatePath = "httpToWsFlows.xml";
    private String xmlPre = "HTW-";
    private String xmlName;
    private String xmlPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "cf/";
    private String xsltPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "xslt/";
    private String xsltPre = "HTWXSLT-";
    public MuleContext muleContext;
    private String taskId;
    private String listenerConf;
    private String wsConf;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    private HttpWsEntity httpWsEntity;

    public HttpToWsFlows(HttpWsEntity httpWsEntity) {
        this.httpWsEntity = httpWsEntity;
        this.taskId = httpWsEntity.getTask_id();
    }

    @Override
    public void initTask() throws Exception {
        LogTool.logInfo(2, "init task " + httpWsEntity.getTask_id());

        List<HttpWsEntity.HwFlow> flows = httpWsEntity.getFlows();
        xmlName = xmlPre + httpWsEntity.getTask_id() + ".xml";
        try {
            initListenAndConsumer(flows.get(0));
        } catch (JDOMException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < flows.size(); i++) {
            HttpWsEntity.HwFlow hwFlow = flows.get(i);
            hwFlow.setFlowId(httpWsEntity.getTask_id() + "-flow-" + i);
            String method = hwFlow.getT_esb().getMethod();
            String param_type = hwFlow.getT_esb().getParam_type();
            String xsltName = xsltPath + xsltPre + hwFlow.getFlowId()+".xslt";
            if (method.equalsIgnoreCase("get")) {
                createGetXslt(xsltName, hwFlow);
                try {
                    getToWs(hwFlow, xsltName);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JDOMException e) {
                    e.printStackTrace();
                }
            }else if (method.equalsIgnoreCase("post")){
                if (param_type.equalsIgnoreCase("form")){
                    createPostXslt(xsltName, hwFlow);
                    try {
                        postFormToXml(hwFlow, xsltName);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JDOMException e) {
                        e.printStackTrace();
                    }
                }else if (param_type.equalsIgnoreCase("json")){
                    createPostXslt(xsltName, hwFlow);
                    try {
                        postJsonToXml(hwFlow, xsltName);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JDOMException e) {
                        e.printStackTrace();
                    }
                }else {
                    LogTool.logInfo(1, "http to ws init failed, no such param type.");
                    throw new Exception("http to ws init failed, no such param type.");
                }
            }else {
                LogTool.logInfo(1, "http to ws init failed.");
                return;
            }
        }
    }

    private void createGetXslt(String xsltName, HttpWsEntity.HwFlow hwFlow) {
        StringBuilder sbParams = new StringBuilder();
        StringBuilder sbParamXml = new StringBuilder();
        for (Map.Entry<String, String> entry : hwFlow.getMapping().entrySet()) {
            sbParams.append("    <xsl:param name=\""+entry.getKey()+"\" />\n");
            sbParamXml.append("            <"+entry.getValue()+">\n" +
                    "                <xsl:value-of select=\"$"+entry.getKey()+"\" />\n" +
                    "            </"+entry.getValue()+">\n");
        }
        String namespace = hwFlow.getS_esb().getWs_space().replaceAll("www\\.", "")
                .replaceAll("com\\.", "").replaceAll("http://", "");
        namespace = namespace.split("\\.").length>0? namespace.split("\\.")[0]:namespace;
        String xsltI = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"  \n" +
                "                xmlns:"+namespace+"=\""+hwFlow.getS_esb().getWs_space()+"\" \n" +
                "                version=\"1.0\">\n" +
                "    <xsl:output method=\"xml\" encoding=\"UTF-8\" indent=\"yes\" />\n" +
                sbParams.toString()+
                "    <xsl:template match=\"/\">\n" +
                "        <"+namespace+":"+ hwFlow.getS_esb().getWs_operation() +">\n" +
                sbParamXml.toString() +
                "        </"+namespace+":"+ hwFlow.getS_esb().getWs_operation() + ">\n" +
                "    </xsl:template>\n" +
                "</xsl:stylesheet>";
        File file = new File(xsltName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            FileUtils.writeStringToFile(file, xsltI);
        } catch (IOException e) {
            LogTool.logInfo(1, " create xslt error, when write to " + xsltName);
            e.printStackTrace();
        }
    }
    private void createPostXslt(String xsltName, HttpWsEntity.HwFlow hwFlow) {
        String sbParams = "<xsl:param name=\"params\" />\n";
        String sbParamXml = "<xsl:value-of select=\"$params\" />\n";
        String namespace = hwFlow.getS_esb().getWs_space().replaceAll("www\\.", "")
                .replaceAll("com\\.", "").replaceAll("http://", "");
        namespace = namespace.split("\\.").length>0? namespace.split("\\.")[0]:namespace;
        String xslt = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"  \n" +
                "                xmlns:"+namespace+"=\""+hwFlow.getS_esb().getWs_space()+"\" \n" +
                "                version=\"1.0\">\n" +
                "    <xsl:output method=\"xml\" encoding=\"UTF-8\" indent=\"yes\" />\n" +
                sbParams +
                "    <xsl:template match=\"/\">\n" +
                "        <"+namespace+":"+ hwFlow.getS_esb().getWs_operation() +">\n" +
                sbParamXml +
                "        </"+namespace+":"+ hwFlow.getS_esb().getWs_operation() + ">\n" +
                "    </xsl:template>\n" +
                "</xsl:stylesheet>";
        File file = new File(xsltName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            FileUtils.writeStringToFile(file, xslt);
        } catch (IOException e) {
            LogTool.logInfo(1, " create xslt error, when write to " + xsltName);
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
            RedisUtils.redisPool.jedis(jedis -> {
                jedis.hset(MuleConfig.muleMonitor + taskId, MuleConfig.hostIp, id);
                return null;
            });
            AnaTask.addToTaskMap(taskId, this);
            return true;
        } catch (Exception e) {
            LogTool.logInfo(1, taskId + " start error.");
            e.printStackTrace();
            removeTask();
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
//    public String toWs() throws IOException, JDOMException {
//        SAXBuilder saxBuilder = new SAXBuilder();
//        InputStream is = ClassLoader.getSystemResourceAsStream(xmlTemplatePath);
//        Document document = saxBuilder.build(is);
//        Element rootElement = document.getRootElement();
//        List<Element> children = rootElement.getChildren();
//        String listenerConf = taskId + "-lc-conf";
//        String wsConf = taskId + "-ws-conf";
//        for (Element child : children) {
//            if (child.getName().equals("listener-config")){
//                child.setAttribute("name", listenerConf);
//                child.getAttribute("host").setValue(listenerHost);
//                child.getAttribute("port").setValue(String.valueOf(listenerPort));
//                child.getAttribute("basePath").setValue(listenerPath);
//            }else if (child.getName().equals("consumer-config")){
//                child.setAttribute("name", wsConf);
//                child.getAttribute("service").setValue(wsService);
//                child.getAttribute("port").setValue(wsPort);
//                child.getAttribute("serviceAddress").setValue(wsServiceAddr);
//                child.getAttribute("wsdlLocation").setValue(wsWsdlLocation);
//            } else if (child.getName().equals("flow")){
//                child.setAttribute("name", taskId + "-flow");
//                Namespace mule = Namespace.getNamespace("mulexml", "http://www.mulesoft.org/schema/mule/xml");
//                Namespace http = Namespace.getNamespace("http", "http://www.mulesoft.org/schema/mule/http");
//                Namespace ws = Namespace.getNamespace("ws", "http://www.mulesoft.org/schema/mule/ws");
//                child.getChild("listener", http).setAttribute("config-ref", listenerConf);
//                child.getChild("consumer", ws).setAttribute("config-ref", wsConf).setAttribute("operation", operation);
//                if (!toJson){
//                    Namespace jsonNs = Namespace.getNamespace("json", "http://www.mulesoft.org/schema/mule/json");
//                    child.removeChild("xml-to-json-transformer", jsonNs);
//                }
//            }
//        }
//        File file = new File(xmlPath+xmlName);
//        if (!file.exists())
//            file.createNewFile();
//        saveDocument(document, file);
//        return xmlPath+xmlName;
//    }

    boolean initListenAndConsumer(HttpWsEntity.HwFlow hwFlow) throws JDOMException, IOException {
        SAXBuilder saxBuilder = new SAXBuilder();
        InputStream is = ClassLoader.getSystemResourceAsStream(xmlTemplatePath);
        Document document = saxBuilder.build(is);
        Element rootElement = document.getRootElement();
        List<Element> children = rootElement.getChildren();
        listenerConf = httpWsEntity.getTask_id() + "-http-conf";
        wsConf = httpWsEntity.getTask_id() + "-ws-conf";

        for (Element child : children) {
            if (child.getName().equals("listener-config")) {
                child.setAttribute("name", listenerConf);
                child.getAttribute("host").setValue(hwFlow.getT_esb().getIp_address());
                child.getAttribute("port").setValue(String.valueOf(hwFlow.getT_esb().getPort()));
            } else if (child.getName().equals("consumer-config")) {
                String serviceAddress = "http://" + hwFlow.getS_esb().getIp_address() + ":" + hwFlow.getS_esb().getPort() + hwFlow.getS_esb().getPath();
                child.setAttribute("name", wsConf);
                child.getAttribute("service").setValue(hwFlow.getS_esb().getWs_service());
                child.getAttribute("port").setValue(hwFlow.getS_esb().getWs_port());
                child.getAttribute("serviceAddress").setValue(serviceAddress);
                if (hwFlow.getS_esb().getWsdl().startsWith("http://")){
                }else {
                    hwFlow.getS_esb().setWsdl("http://" + hwFlow.getS_esb().getWsdl());
                }
                child.getAttribute("wsdlLocation").setValue(hwFlow.getS_esb().getWsdl());
            }
        }
        File file = new File(xmlPath+xmlName);
        if (!file.exists()){
            file.createNewFile();
        }
        saveDocument(document, file);
        return true;
    }
    public String getToWs(HttpWsEntity.HwFlow hwFlow, String xsltName) throws IOException, JDOMException {
        FileReader fileReader = new FileReader(new File(xmlPath + xmlName));
        SAXBuilder saxBuilder = new SAXBuilder();
        Document document = saxBuilder.build(fileReader);
        fileReader.close();

        Element rootElement = document.getRootElement();
        Namespace http = Namespace.getNamespace("http", "http://www.mulesoft.org/schema/mule/http");
        Namespace ws = Namespace.getNamespace("ws", "http://www.mulesoft.org/schema/mule/ws");
        Namespace doc = Namespace.getNamespace("doc", "http://www.mulesoft.org/schema/mule/documentation");
        Namespace json = Namespace.getNamespace("json", "http://www.mulesoft.org/schema/mule/json");
        Namespace mulexml = Namespace.getNamespace("mulexml", "http://www.mulesoft.org/schema/mule/xml");
        Namespace dft = Namespace.getNamespace("", "http://www.mulesoft.org/schema/mule/core");

        String httpConfName = listenerConf;
        String wsConfName = wsConf;

        Element flow = new Element("flow", dft);
        flow.setAttribute("name", hwFlow.getFlowId());
        Element httpLtn = new Element("listener", http)
                .setAttribute("config-ref", httpConfName)
                .setAttribute("path", hwFlow.getT_esb().getPath())
                .setAttribute("name", "http", doc);
        httpLtn.setAttribute("allowedMethods", hwFlow.getT_esb().getMethod());
        flow.addContent(httpLtn);
        flow.addContent(new Element("set-payload", dft)
                .setAttribute("value", "<gg></gg>")
                .setAttribute("name", "Set Payload", doc));
        Element xslt = new Element("xslt-transformer", mulexml)
                .setAttribute("returnClass", "java.lang.String")
                .setAttribute("xsl-file", xsltName)
                .setAttribute("maxIdleTransformers", "2")
                .setAttribute("maxActiveTransformers", "5")
                .setAttribute("name", "XSLT", doc);
        for (Map.Entry<String, String> entry : hwFlow.getMapping().entrySet()) {
            xslt.addContent(new Element("context-property", mulexml)
                    .setAttribute("key", entry.getKey())
                    .setAttribute("value", "#[message.inboundProperties.'http.query.params'."+entry.getKey()+"]"));
        }
        flow.addContent(xslt);
        flow.addContent(new Element("consumer", ws)
                .setAttribute("config-ref", wsConfName)
                .setAttribute("operation", hwFlow.getS_esb().getWs_operation())
                .setAttribute("name", "Web Service Consumer", doc));
        if (hwFlow.getT_esb().getResult_type().equalsIgnoreCase("json")){
            flow.addContent(new Element("xml-to-json-transformer", json).setAttribute("name", "XML to JSON", doc));
        }
        rootElement.addContent(flow);

        File file = new File(xmlPath+xmlName);
        if (!file.exists()){
            LogTool.logInfo(1, "no base xml");
            return null;
        }
        saveDocument(document, file);
        return xmlPath+xmlName;
    }
    private void postFormToXml(HttpWsEntity.HwFlow hwFlow, String xsltName) throws IOException, JDOMException {
        FileReader fileReader = new FileReader(new File(xmlPath + xmlName));
        SAXBuilder saxBuilder = new SAXBuilder();
        Document document = saxBuilder.build(fileReader);
        fileReader.close();

        Namespace http = Namespace.getNamespace("http", "http://www.mulesoft.org/schema/mule/http");
        Namespace ws = Namespace.getNamespace("ws", "http://www.mulesoft.org/schema/mule/ws");
        Namespace doc = Namespace.getNamespace("doc", "http://www.mulesoft.org/schema/mule/documentation");
        Namespace json = Namespace.getNamespace("json", "http://www.mulesoft.org/schema/mule/json");
        Namespace mulexml = Namespace.getNamespace("mulexml", "http://www.mulesoft.org/schema/mule/xml");
        Namespace dft = Namespace.getNamespace("", "http://www.mulesoft.org/schema/mule/core");

        String httpConfName = listenerConf;
        String wsConfName = wsConf;

        Element rootElement = document.getRootElement();
        Element flow = new Element("flow", dft);
        flow.setAttribute("name", hwFlow.getFlowId());
        Element httpLtn = new Element("listener", http)
                .setAttribute("config-ref", httpConfName)
                .setAttribute("path", hwFlow.getT_esb().getPath())
                .setAttribute("name", "http", doc);
        httpLtn.setAttribute("allowedMethods", hwFlow.getT_esb().getMethod());
        flow.addContent(httpLtn);
        flow.addContent(new Element("set-payload", dft)
                .setAttribute("value", "<gg></gg>")
                .setAttribute("name", "Set Payload", doc));
        Element xslt = new Element("xslt-transformer", mulexml)
                .setAttribute("returnClass", "java.lang.String")
                .setAttribute("xsl-file", xsltName)
                .setAttribute("maxIdleTransformers", "2")
                .setAttribute("maxActiveTransformers", "5")
                .setAttribute("name", "XSLT", doc);
        for (Map.Entry<String, String> entry : hwFlow.getMapping().entrySet()) {
            xslt.addContent(new Element("context-property", mulexml)
                    .setAttribute("key", entry.getKey())
                    .setAttribute("value", "#[message.inboundProperties.'http.query.params'."+entry.getKey()+"]"));
        }
        flow.addContent(xslt);
        flow.addContent(new Element("consumer", ws)
                .setAttribute("config-ref", wsConfName)
                .setAttribute("operation", hwFlow.getS_esb().getWs_operation())
                .setAttribute("name", "Web Service Consumer", doc));
        if (hwFlow.getT_esb().getResult_type().equalsIgnoreCase("json")){
            flow.addContent(new Element("xml-to-json-transformer", json).setAttribute("name", "XML to JSON", doc));
        }
        rootElement.addContent(flow);
        File file = new File(xmlPath+xmlName);
        if (!file.exists()){
            LogTool.logInfo(1, "no base xml");
            return;
        }
        saveDocument(document, file);
    }
    private void postJsonToXml(HttpWsEntity.HwFlow hwFlow, String xsltName) throws IOException, JDOMException {
        FileReader fileReader = new FileReader(new File(xmlPath + xmlName));
        SAXBuilder saxBuilder = new SAXBuilder();
        Document document = saxBuilder.build(fileReader);
        fileReader.close();

        Namespace http = Namespace.getNamespace("http", "http://www.mulesoft.org/schema/mule/http");
        Namespace ws = Namespace.getNamespace("ws", "http://www.mulesoft.org/schema/mule/ws");
        Namespace doc = Namespace.getNamespace("doc", "http://www.mulesoft.org/schema/mule/documentation");
        Namespace json = Namespace.getNamespace("json", "http://www.mulesoft.org/schema/mule/json");
        Namespace mulexml = Namespace.getNamespace("mulexml", "http://www.mulesoft.org/schema/mule/xml");
        Namespace dft = Namespace.getNamespace("", "http://www.mulesoft.org/schema/mule/core");

        String httpConfName = listenerConf;
        String wsConfName = wsConf;

        Element rootElement = document.getRootElement();
        Element flow = new Element("flow", dft);
        flow.setAttribute("name", hwFlow.getFlowId());
        Element httpLtn = new Element("listener", http)
                .setAttribute("config-ref", httpConfName)
                .setAttribute("path", hwFlow.getT_esb().getPath())
                .setAttribute("name", "http", doc);
        httpLtn.setAttribute("allowedMethods", hwFlow.getT_esb().getMethod());
        flow.addContent(httpLtn);
        flow.addContent(new Element("component", dft)
                .setAttribute("class", "com.redpill.trans.JsonToXml")
                .setAttribute("name" , "Java", doc));
        flow.addContent(new Element("set-payload", dft)
                .setAttribute("value", "&lt;TemRoot&gt;#[payload]&lt;/TemRoot&gt;")
                .setAttribute("name", "Set Payload", doc));
        Element xslt = new Element("xslt-transformer", mulexml)
                .setAttribute("returnClass", "java.lang.String")
                .setAttribute("xsl-file", xsltName)
                .setAttribute("maxIdleTransformers", "2")
                .setAttribute("maxActiveTransformers", "5")
                .setAttribute("name", "XSLT", doc);
        for (Map.Entry<String, String> entry : hwFlow.getMapping().entrySet()) {
            xslt.addContent(new Element("context-property", mulexml)
                    .setAttribute("key", entry.getKey())
                    .setAttribute("value", "#[message.payload]"));
        }
        flow.addContent(xslt);
        flow.addContent(new Element("component", dft)
                .setAttribute("class", "com.redpill.trans.XmlToSoa")
                .setAttribute("name" , "Java", doc));

        flow.addContent(new Element("consumer", ws)
                .setAttribute("config-ref", wsConfName)
                .setAttribute("operation", hwFlow.getS_esb().getWs_operation())
                .setAttribute("name", "Web Service Consumer", doc));
        if (hwFlow.getT_esb().getResult_type().equalsIgnoreCase("json")){
            flow.addContent(new Element("xml-to-json-transformer", json).setAttribute("name", "XML to JSON", doc));
        }
        rootElement.addContent(flow);
        File file = new File(xmlPath+xmlName);
        if (!file.exists()){
            LogTool.logInfo(1, "no base xml");
            return;
        }
        saveDocument(document, file);
    }
}
