package com.redpill.api;

import com.redpill.server.AnaTask;
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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static com.redpill.CFManager.saveDocument;

public class WebServiceProxy implements MuleTask {
    private static final String xmlTemplatePath = "wsProxyTem.xml";
    private static final DefaultMuleContextFactory defaultMuleContextFactory = new DefaultMuleContextFactory();
    private static final String xmlPre = "WSP-";
    private String xmlName;
    private String xmlPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "cf/";
    private SpringXmlConfigurationBuilder configBuilder;
    private String taskId;
    private MuleContext muleContext;
    private List<WsFlow> wsFlows;

    public static class WsFlow{
        private String id;
        private String inboundAddress;
        private String outboundAddress;
        private String wsdl;

        public WsFlow(String id , String inboundAddress, String outboundAddress, String wsdl) {
            String port = inboundAddress.split(":")[1];
            InetAddress[] a = new InetAddress[0];
            try {
                a = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            for (InetAddress ia : a) {
                if (ia.getHostAddress().contains("172")) {
                    this.inboundAddress = ia.getHostAddress()+":"+port;
                }else {
                }
            }
            this.id = id;
            if (inboundAddress.startsWith("http://")){
                this.inboundAddress = inboundAddress;
            }else {
                this.inboundAddress = "http://" + inboundAddress;
            }
            if (outboundAddress.startsWith("http://")){
                this.outboundAddress = outboundAddress;
            }else {
                this.outboundAddress = "http://" + outboundAddress;
            }
            if (wsdl.startsWith("http://")){
                this.wsdl = wsdl;
            }else {
                this.wsdl = "http://" + wsdl;
            }
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getInboundAddress() {
            return inboundAddress;
        }

        public void setInboundAddress(String inboundAddress) {
            this.inboundAddress = inboundAddress;
        }

        public String getOutboundAddress() {
            return outboundAddress;
        }

        public void setOutboundAddress(String outboundAddress) {
            this.outboundAddress = outboundAddress;
        }

        public String getWsdl() {
            return wsdl;
        }

        public void setWsdl(String wsdl) {
            this.wsdl = wsdl;
        }
    }

    public WebServiceProxy(String taskId, List<WsFlow> wsFlows) {
        this.taskId = taskId;
        this.xmlName = xmlPre + taskId + ".xml";
        this.wsFlows = wsFlows;
    }

    @Override
    public void initTask() {
        try {
            wsProxy();
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
                String id = muleContext.getConfiguration().getId();
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

    public String wsProxy() throws IOException, JDOMException {
        SAXBuilder saxBuilder = new SAXBuilder();
        InputStream is = ClassLoader.getSystemResourceAsStream(xmlTemplatePath);
        Document document = saxBuilder.build(is);
        Element rootElement = document.getRootElement();
        List<Element> children = rootElement.getChildren();
        Namespace pattern = Namespace.getNamespace("pattern", "http://www.mulesoft.org/schema/mule/pattern");
        for (int i = 0; i < wsFlows.size(); i++) {
            Element wsd = new Element( "web-service-proxy", pattern);
            wsd.setAttribute("inboundAddress", wsFlows.get(i).inboundAddress);
            wsd.setAttribute("outboundAddress", wsFlows.get(i).outboundAddress);
            wsd.setAttribute("wsdlLocation", wsFlows.get(i).wsdl);
            wsd.setAttribute("name", "ws-proxy-" + wsFlows.get(i).id);
            rootElement.addContent(i, wsd);
        }
        File file = new File(xmlPath+xmlName);
        if (!file.exists())
            file.createNewFile();
        saveDocument(document, file);
        return xmlPath+xmlName;
    }
}
