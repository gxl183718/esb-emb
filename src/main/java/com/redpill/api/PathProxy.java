package com.redpill.api;

import com.redpill.server.AnaTask;
import com.redpill.tool.LogTool;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import org.jdom.*;
import org.jdom.input.SAXBuilder;
import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.config.ConfigurationException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.config.spring.SpringXmlConfigurationBuilder;
import org.mule.context.DefaultMuleContextFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.redpill.CFManager.saveDocument;

public class PathProxy implements MuleTask{
    private static final String xmlTemplatePath = "httpPathTem.xml";
    private static final String xmlPre = "HPP-";
    private static final DefaultMuleContextFactory defaultMuleContextFactory = new DefaultMuleContextFactory();
    private String xmlName;
    private String xmlPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "flow/";
    private SpringXmlConfigurationBuilder configBuilder;
    public String taskId;
    public MuleContext muleContext;
    private List<HttpFlow> flows;
    private String listenerHost;
    private Integer listenerPort;
    private String requestHost;
    private Integer requestPort;
    public static class HttpFlow{
        private String flowId;
        private String listenerPath;
        private String requestPath;
        private String method;

        public HttpFlow(String flowId, String listenerPath, String requestPath, String method) {
            this.flowId = flowId;
            this.method = method;
            if (listenerPath.endsWith("/")){
            listenerPath = listenerPath + "*";
            }else {
                listenerPath = listenerPath + "/*";
            }
            if (!listenerPath.startsWith("/")){
                listenerPath = "/" + listenerPath;
            }
            this.listenerPath = listenerPath;
            if (!requestPath.startsWith("/")){
                requestPath = "/" + requestPath;
            }
            if (requestPath.endsWith("/")){
                requestPath = requestPath.substring(0, requestPath.length() -1);
            }
            this.requestPath = requestPath;
        }
    }



    public static void main(String[] args) throws JDOMException, IOException {
        FileInputStream fileInputStream = new FileInputStream(new File("data/flow/hpp-hpp-001.xml"));
        SAXBuilder saxBuilder = new SAXBuilder();
        Document document2 = saxBuilder.build(fileInputStream);
        for (Object child : document2.getRootElement().getChildren()) {
            System.out.println(((Element) child));
            for (Object attribute : ((Element) child).getAttributes()) {
                System.out.println(((Attribute) attribute).getName());
            }
        }
    }

    public PathProxy(String taskId, List<HttpFlow> flows, String listenerHost, Integer listenerPort, String requestHost, Integer requestPort) {
        this.taskId = taskId;
        this.flows = flows;
        this.listenerHost = listenerHost;
        this.listenerPort = listenerPort;
        this.requestHost = requestHost;
        this.requestPort = requestPort;
        this.xmlName = xmlPre + taskId + ".xml";
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

    @Override
    public void initTask() {
        LogTool.logInfo(2, "init task " + taskId);
        try {
            httpProxy();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JDOMException e) {
            e.printStackTrace();
        }
    }
    private String httpProxy() throws IOException, JDOMException {
        SAXBuilder saxBuilder = new SAXBuilder();
        InputStream is = ClassLoader.getSystemResourceAsStream(xmlTemplatePath);
        Document document = saxBuilder.build(is);
        Element rootElement = document.getRootElement();
        List<Element> children = rootElement.getChildren();
        String listenerConfName = taskId + "-in";
        String requestConfName = taskId + "-out";
        for (Element child : children) {
            if (child.getName().equals("listener-config")){
                child.getAttribute("name").setValue(listenerConfName);
                child.getAttribute("host").setValue(listenerHost);
                child.getAttribute("port").setValue(String.valueOf(listenerPort));
                child.getAttribute("name", Namespace.getNamespace("doc", "http://www.mulesoft.org/schema/mule/documentation")).setValue(taskId + "-conf-in");
            }else if (child.getName().equals("request-config")){
                child.getAttribute("name").setValue(requestConfName);
                child.getAttribute("host").setValue(requestHost);
                child.getAttribute("port").setValue(String.valueOf(requestPort));
                child.getAttribute("name", Namespace.getNamespace("doc", "http://www.mulesoft.org/schema/mule/documentation")).setValue(taskId + "-conf-out");
            }
        }
        Namespace http = Namespace.getNamespace("http", "http://www.mulesoft.org/schema/mule/http");
        Namespace aa = Namespace.getNamespace("", "http://www.mulesoft.org/schema/mule/core");

        //TODO:add flow
        for (int i = 0; i < flows.size(); i++) {
            Element wsd = new Element( "flow", aa);
            wsd.setAttribute("name", "flow-" + flows.get(i).flowId);
            Element listener = new Element("listener", http);
            listener.setAttribute("config-ref", rootElement.getChild("listener-config", http).getAttributeValue("name"));
            listener.setAttribute("path", flows.get(i).listenerPath);
            if (!(flows.get(i).method).contains("*")){
                listener.setAttribute("allowedMethods", flows.get(i).method);
            }else {
                listener.removeAttribute("allowedMethods");
            }
            listener.setAttribute("name", "HTTP", http);
            wsd.addContent(listener);
            Element request = new Element("request", http);
            request.setAttribute("config-ref", rootElement.getChild("request-config", http).getAttributeValue("name"));
            request.setAttribute("path", flows.get(i).requestPath + "#[message.inboundProperties['http.request.path'].substring(message.inboundProperties['http.listener.path'].length()-2)]");
            request.setAttribute("name", "HTTP", http);
            request.setAttribute("method", "#[message.inboundProperties['http.method']]");
            Element builder = new Element("request-builder", http);
            builder.addContent(new Element("query-params", http).setAttribute("expression", "message.inboundProperties.'http.query.params'"));
            request.addContent(builder);
            wsd.addContent(request);
            rootElement.addContent( wsd);
        }
        File file = new File(xmlPath + xmlName);
        if (!file.exists()){
            file.createNewFile();
        }
        saveDocument(document, file);
        return xmlPath + xmlName;
    }
}
