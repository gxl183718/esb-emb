package com.redpill.api;

import com.redpill.server.AnaTask;
import com.redpill.tool.FileTool;
import com.redpill.tool.LogTool;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import org.apache.commons.io.FileUtils;
import org.jdom.*;
import org.jdom.input.SAXBuilder;
import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.config.ConfigurationException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.config.spring.SpringXmlConfigurationBuilder;
import org.mule.context.DefaultMuleContextFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static com.redpill.CFManager.saveDocument;

/**
 * restful api proxy
 * @author labigthree
 */
public class PathProxy implements MuleTask{
    private static final String TYPE = "T11";
    private static final String TEMPLATE_FILE = "httpPathTem.xml";
    private static final String DATA_FILE_PRE = "HPP-";
    private static final DefaultMuleContextFactory defaultMuleContextFactory = new DefaultMuleContextFactory();

    private String xmlName;
    private String xmlPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "flow/";
    private SpringXmlConfigurationBuilder configBuilder;
    public String taskId;
    public MuleContext muleContext;
    private List<HttpFlow> flows;
    private Integer listenerPort;
    private String listenerHost;
    private String requestHost;
    private Integer requestPort;
    private String serverId;

    @Override
    public String getServerId(){
        return TYPE+"-"+this.listenerPort;
    }

    @Override
    public Integer getPort() {
        return this.listenerPort;
    }

    @Override
    public String getType() {
        return TYPE;
    }

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
        this.serverId = getServerId();
        this.xmlName = DATA_FILE_PRE + serverId + ".xml";
    }

    @Override
    public boolean executeTask() {
        LogTool.logInfo(2, "Execute task " + taskId);
        try {
            configBuilder = new SpringXmlConfigurationBuilder(xmlPath + xmlName);
            muleContext = defaultMuleContextFactory.createMuleContext(configBuilder);
            muleContext.start();
            LogTool.logInfo(2, "Server starting.");
        } catch (Exception e) {
            LogTool.logInfo(1, taskId + " start error, use backup file to start.");
            e.printStackTrace();
            backupToHistory(xmlName);
            return false;
        }
        String muleId = muleContext.getConfiguration().getId();
        RedisUtils.redisPool.jedis(jedis -> {
            jedis.hset(MuleConfig.muleMonitor, taskId, serverId);
            jedis.hset(MuleConfig.muleMonitor + serverId, MuleConfig.hostIp, muleId);
            return null;
        });
        AnaTask.addToTaskMap(serverId, this);
        return true;
    }
    void backupToHistory(String xmlPath){
        try {
            File file = new File(xmlPath + xmlName);
            file.delete();
            File file1 = new File(xmlPath + xmlName + ".backup");
            file1.renameTo(new File(xmlPath + xmlName));
            //重新从磁盘load
            MuleLoad muleLoad = new MuleLoad(xmlName);
            muleLoad.initTask();
            muleLoad.executeTask();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void closeTask() {
        if (null != muleContext){
            try {
                muleContext.stop();
                RedisUtils.redisPool.jedis(jedis -> {
                    jedis.hdel(MuleConfig.muleMonitor, taskId);
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
    public boolean removeTask() throws FileNotFoundException {
        SAXBuilder saxBuilder = new SAXBuilder();
        File file = new File(xmlPath + xmlName);
        InputStream is = new FileInputStream(xmlPath + xmlName);
        Document document = null;
        try {
            document = saxBuilder.build(is);
        } catch (JDOMException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        Element rootElement = document.getRootElement();
        List<Element> children = rootElement.getChildren();
        //remove flow mapped this
        for (Element child : children) {
            if ((child.getName().equalsIgnoreCase("flow"))
            && child.getAttributeValue("name").equalsIgnoreCase(taskId)) {
                rootElement.removeChild(child.getName(), child.getNamespace());
            }
        }
        boolean haveFlows = false;
        for (Element child : children) {
            if (child.getName().equalsIgnoreCase("flow")){
                haveFlows =  true;
            }
        }
        if (haveFlows){
            try {
                saveDocument(document, file);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }else {
            file.delete();
            return false;
        }
        LogTool.logInfo(2, "rm task " + taskId);
        return false;
    }

    @Override
    public String getTaskId() {
        return taskId;
    }

    @Override
    public void initTask() {
        LogTool.logInfo(2, "init task " + taskId);
        try {
            synchronized (listenerPort){
                boolean exist = httpProxy();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JDOMException e) {
            e.printStackTrace();
        }
    }
    private boolean httpProxy() throws IOException, JDOMException {
        File file = new File(xmlPath + xmlName);
        boolean exist = true;
        if (!file.exists()){
            file.createNewFile();
            exist = false;
        }else {
            //存在则备份旧文件，用于更新失败后回退
            String backupFileName = xmlPath + xmlName + ".backup";
            File fileBackup = new File(backupFileName);
            if (!fileBackup.exists()) {
                fileBackup.createNewFile();
                FileTool.copyFile(xmlPath + xmlName, backupFileName);
            }
        }
        SAXBuilder saxBuilder = new SAXBuilder();
        InputStream is = null;
        if(exist){
            is = new FileInputStream(xmlPath + xmlName);
        }else{
            is = ClassLoader.getSystemResourceAsStream(TEMPLATE_FILE);
        }
        Document document = saxBuilder.build(is);
        Element rootElement = document.getRootElement();
        List<Element> children = rootElement.getChildren();
        String listenerConfName = getServerId() + "-in";
        String requestConfName = taskId + "-out";
        Namespace http = Namespace.getNamespace("http", "http://www.mulesoft.org/schema/mule/http");
        Namespace aa = Namespace.getNamespace("", "http://www.mulesoft.org/schema/mule/core");
        Namespace doc = Namespace.getNamespace("doc", "http://www.mulesoft.org/schema/mule/documentation");
        if (!exist){
            // 创建listener config
            Element listenerConf = new Element("listener-config", http);
            listenerConf.setAttribute("name", listenerConfName);
            listenerConf.setAttribute("host", "0.0.0.0");
            listenerConf.setAttribute("port", String.valueOf(listenerPort));
            listenerConf.setAttribute("name", listenerConfName+" configuration", doc);
            rootElement.addContent(0, listenerConf);
        }
        String request_conf_name = null;
        boolean haveConf = false;
        //when there is a request-config with the same name,
        for (Element child : children) {
            if (child.getName().equals("request-config")) {
                if (child.getAttributeValue("host").equals(requestHost)
                        && child.getAttributeValue("port").equals(String.valueOf(requestPort))){
                    request_conf_name = child.getAttributeValue("name");
                    haveConf = true;
                    break;
                }
            }
        }
        //create request-config if not exist
        if (!haveConf){
            Element requestConf = new Element("request-config", http);
            requestConf.setAttribute("name", requestConfName);
            requestConf.setAttribute("host", requestHost);
            requestConf.setAttribute("port", String.valueOf(requestPort));
            if (requestPort == 443){
                requestConf.setAttribute("protocol", "HTTPS");
            }
            requestConf.setAttribute("name", requestConfName+" configuration", doc);
            rootElement.addContent(1, requestConf);
            request_conf_name = requestConfName;
        }
        //create flow
        for (int i = 0; i < flows.size(); i++) {
            Element wsd = new Element( "flow", aa);
            wsd.setAttribute("name", flows.get(i).flowId);
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
            request.setAttribute("config-ref", request_conf_name);
            request.setAttribute("path", flows.get(i).requestPath + "#[message.inboundProperties['http.request.path'].substring(message.inboundProperties['http.listener.path'].length()-2)]");
            request.setAttribute("name", "HTTP", http);
            request.setAttribute("method", "#[message.inboundProperties['http.method']]");
            Element builder = new Element("request-builder", http);
            builder.addContent(new Element("query-params", http).setAttribute("expression", "message.inboundProperties.'http.query.params'"));
            //传递header
            for (String header : MuleConfig.headers) {
                builder.addContent(new Element("header", http).setAttribute("headerName", header).setAttribute("value", "#[message.inboundProperties.'"+header+"']"));
            }
            if (MuleConfig.headers.size() <= 0) {
                builder.addContent(new Element("header", http).setAttribute("headerName", "authorization").setAttribute("value", "#[message.inboundProperties.'authorization']"));
            }
//            builder.addContent(new Element("headers", http).setAttribute("expression", "message.inboundProperties"));
            request.addContent(builder);
            wsd.addContent(request);
            rootElement.addContent( wsd);
        }
        saveDocument(document, file);
        return exist;
    }
}
