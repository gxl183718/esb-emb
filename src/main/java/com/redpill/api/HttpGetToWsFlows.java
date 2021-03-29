package com.redpill.api;

import com.redpill.tool.LogTool;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import lombok.Data;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.redpill.CFManager.saveDocument;

public class HttpGetToWsFlows implements MuleTask {

    private static final String xmlTemplatePath = "httpGetToWsFlows.xml";
    private String xmlPre = "HGTW-";
    private String xmlName;
    private String xmlPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "flow/";
    private String xsltPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "xslt/";

    private static final DefaultMuleContextFactory defaultMuleContextFactory = new DefaultMuleContextFactory();
    private SpringXmlConfigurationBuilder configBuilder;

    public MuleContext muleContext;

    public String taskId;

    private String listenerHost;
    private Integer listenerPort;

    private String wsService;
    private String wsPort;
    private String wsServiceAddr;
    private String wsWsdlLocation;
    private String namespace;
    private String namespaceUrl;

    private List<Flow> flows;

    public static class Flow {
        private String listenerPath;
        private String method;
        private String operation;
        private boolean toJson;
        private Map<String, String> map;

        public String getListenerPath() {
            return listenerPath;
        }

        public void setListenerPath(String listenerPath) {
            this.listenerPath = listenerPath;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public boolean isToJson() {
            return toJson;
        }

        public void setToJson(boolean toJson) {
            this.toJson = toJson;
        }

        public Map<String, String> getMap() {
            return map;
        }

        public void setMap(Map<String, String> map) {
            this.map = map;
        }
    }

    public HttpGetToWsFlows(String taskId, String listenerHost, Integer listenerPort, String wsService, String wsPort,
                            String wsServiceAddr, String wsWsdlLocation,String namespace, String namespaceUrl, List<Flow> flows) {
        this.taskId = taskId;
        this.xmlName = xmlPre + taskId + ".xml";
        this.listenerHost = listenerHost;
        this.listenerPort = listenerPort;
        this.wsService = wsService;
        this.wsPort = wsPort;
        this.wsServiceAddr = wsServiceAddr;
        this.wsWsdlLocation = wsWsdlLocation;
        this.namespaceUrl = namespaceUrl;
        this.namespace = namespace;
        this.flows = flows;
    }


    @Override
    public void initTask() {
        LogTool.logInfo(2, "init task " + taskId);

        for (int i = 0; i < flows.size(); i++) {
            StringBuilder sbParams = new StringBuilder();
            StringBuilder sbParamXml = new StringBuilder();
            for (Map.Entry<String, String> entry : flows.get(i).getMap().entrySet()) {
                sbParams.append("    <xsl:param name=\""+entry.getKey()+"\" />\n");
                sbParamXml.append("            <"+entry.getValue()+">\n" +
                        "                <xsl:value-of select=\"$"+entry.getKey()+"\" />\n" +
                        "            </"+entry.getValue()+">\n");
            }
            String xsltI = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"  \n" +
                    "                xmlns:"+namespace+"=\""+namespaceUrl+"\" \n" +
                    "                version=\"1.0\">\n" +
                    "    <xsl:output method=\"xml\" encoding=\"UTF-8\" indent=\"yes\" />\n" +
                    sbParams.toString()+
                    "    <xsl:template match=\"/\">\n" +
                    "        <"+namespace+":"+ flows.get(i).operation +">\n" +
                    sbParamXml.toString() +
                    "        </"+namespace+":"+ flows.get(i).operation + ">\n" +
                    "    </xsl:template>\n" +
                    "</xsl:stylesheet>";
            String pathF = xsltPath + taskId + "-flow-" + i + ".xslt";
            File file = new File(pathF);
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
                LogTool.logInfo(1, " create xslt error, when write to " + pathF);
                e.printStackTrace();
            }
        }

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
            RedisUtils.redisPool.jedis(jedis -> {
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
        Namespace http = Namespace.getNamespace("http", "http://www.mulesoft.org/schema/mule/http");
        Namespace ws = Namespace.getNamespace("ws", "http://www.mulesoft.org/schema/mule/ws");
        Namespace doc = Namespace.getNamespace("doc", "http://www.mulesoft.org/schema/mule/documentation");
        Namespace json = Namespace.getNamespace("json", "http://www.mulesoft.org/schema/mule/json");
        Namespace mulexml = Namespace.getNamespace("mulexml", "http://www.mulesoft.org/schema/mule/xml");
        Namespace dft = Namespace.getNamespace("", "http://www.mulesoft.org/schema/mule/core");

        String httpConfName = "httpListenerConfig";
        String wsConfName = "wsConf";
        rootElement.getChild("listener-config", http)
            .setAttribute("name", httpConfName)
            .setAttribute("host", listenerHost)
            .setAttribute("port", String.valueOf(listenerPort));
        rootElement.getChild("consumer-config", ws)
                .setAttribute("name", wsConfName)
                .setAttribute("port", wsPort)
                .setAttribute("service", wsService)
                .setAttribute("serviceAddress", wsServiceAddr)
                .setAttribute("wsdlLocation", wsWsdlLocation)
                .setAttribute("name", "Web Service Consumer", doc);

        for (int i = 0; i < flows.size(); i++) {

            Element flow = new Element("flow", dft);
            flow.setAttribute("name", "flow" + i);
            Element httpLtn = new Element("listener", http)
                    .setAttribute("config-ref", httpConfName)
                    .setAttribute("path", flows.get(i).listenerPath)
                    .setAttribute("name", "http", doc);
            if (!flows.get(i).method.equals("*")) {
                httpLtn.setAttribute("allowedMethods", flows.get(i).method);
            }
            flow.addContent(httpLtn);
            flow.addContent(new Element("set-payload", dft)
                    .setAttribute("value", "<gg></gg>")
                    .setAttribute("name", "Set Payload", doc));
            Element xslt = new Element("xslt-transformer", mulexml)
                    .setAttribute("returnClass", "java.lang.String")
                    .setAttribute("xsl-file", xsltPath + taskId + "-flow-" + i + ".xslt")
                    .setAttribute("maxIdleTransformers", "2")
                    .setAttribute("maxActiveTransformers", "5")
                    .setAttribute("name", "XSLT", doc);
            for (Map.Entry<String, String> entry : flows.get(i).map.entrySet()) {
                xslt.addContent(new Element("context-property", mulexml)
                .setAttribute("key", entry.getKey())
                .setAttribute("value", "#[message.inboundProperties.'http.query.params'."+entry.getKey()+"]"));
            }
            flow.addContent(xslt);
            flow.addContent(new Element("consumer", ws)
                                .setAttribute("config-ref", wsConfName)
                                .setAttribute("operation", flows.get(i).getOperation())
                                .setAttribute("name", "Web Service Consumer", doc));
            if (flows.get(i).toJson){
                flow.addContent(new Element("xml-to-json-transformer", json).setAttribute("name", "XML to JSON", doc));
            }
            rootElement.addContent(flow);
        }
        File file = new File(xmlPath+xmlName);
        if (!file.exists()){
            file.createNewFile();
        }
        saveDocument(document, file);
        return xmlPath+xmlName;
    }

    public static void main(String[] args) {
        List<Flow> flows = new ArrayList<>();
        Flow flow = new Flow();
        flow.setListenerPath("/hehe");
        Map<String, String> map = new HashMap<>();
        map.put("a", "name");
        map.put("b", "age");
        flow.setMap(map);
        flow.setMethod("get");
        flow.setOperation("sayGodBye");
        flow.setToJson(true);
        Flow flow2 = new Flow();
        Map<String, String> map1 = new HashMap();
        flow2.setMap(map1);
        flow2.setListenerPath("/haha");
        flow2.setMethod("get");
        flow2.setOperation("fuck");
        flow2.setToJson(true);

        flows.add(flow);
        flows.add(flow2);


        HttpGetToWsFlows httpGetToWsFlows = new HttpGetToWsFlows("hgtw-test-001", "0.0.0.0", 10086, "WBTestInterfaceImplService",
                "WBTestInterfaceImplPort", "http://localhost:8088/cxf/webServices", "http://localhost:8088/cxf/webServices?wsdl",
                "gg", "http://www.gao.xiao.com", flows);
        httpGetToWsFlows.initTask();
        httpGetToWsFlows.executeTask();
    }
}
