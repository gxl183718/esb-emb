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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.redpill.CFManager.saveDocument;

public class HttpProxy implements MuleTask{
    private static final String xmlTemplatePath = "httpProxyTem.xml";
    private static final String xmlPre = "hp-";
    private static final DefaultMuleContextFactory defaultMuleContextFactory = new DefaultMuleContextFactory();
    private String xmlName;
    private String xmlPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "cf/";
    private SpringXmlConfigurationBuilder configBuilder;
    public String taskId;
    public MuleContext muleContext;

    private String listenerHost;
    private Integer listenerPort;
    private String listenerPath;
    private String requestHost;
    private Integer requestPort;
    private String requestPath;

    public HttpProxy() {
    }

    public HttpProxy(String taskId, String listenerHost, Integer listenerPort, String listenerPath,
                     String requestHost, Integer requestPort, String requestPath) {
        this.taskId = taskId;
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
            }else if (child.getName().equals("flow")){
                Namespace http = Namespace.getNamespace("http", "http://www.mulesoft.org/schema/mule/http");
                child.getChild("listener",http).setAttribute("config-ref", listenerConfName);
                child.getChild("listener",http).setAttribute("path",listenerPath);
                child.getChild("request",http).setAttribute("config-ref", requestConfName);
                String rPath = child.getChild("request", http).getAttributeValue("path");
                child.getChild("request", http).setAttribute("path", requestPath + rPath);
            }
        }
        File file = new File(xmlPath + xmlName);
        if (!file.exists())
            file.createNewFile();
        saveDocument(document, file);
        return xmlPath + xmlName;
    }
}
