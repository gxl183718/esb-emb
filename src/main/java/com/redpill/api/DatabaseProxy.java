package com.redpill.api;

import com.redpill.entity.DatabaseEntity;
import com.redpill.server.AnaTask;
import com.redpill.tool.FileTool;
import com.redpill.tool.LogTool;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import com.zzq.dolls.config.LoadConfig;
import org.jdom.*;
import org.jdom.input.SAXBuilder;
import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.config.ConfigurationException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.config.spring.SpringXmlConfigurationBuilder;
import org.mule.context.DefaultMuleContextFactory;
import org.omg.CORBA.PUBLIC_MEMBER;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.redpill.CFManager.saveDocument;

public class DatabaseProxy implements MuleTask{
    public static final String TYPE = "T13";
    private static final String xmlTemplatePath = "database.xml";
    private static final DefaultMuleContextFactory defaultMuleContextFactory = new DefaultMuleContextFactory();
    String xmlPre = "HDB-";
    private String xmlName;
    private String xmlPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "flow/";
    private SpringXmlConfigurationBuilder configBuilder;
    public MuleContext muleContext;

    private DatabaseEntity databaseEntity;
    private String serverId;

    public DatabaseProxy(DatabaseEntity databaseEntity) {
        this.databaseEntity = databaseEntity;

        this.serverId = getServerId();
        this.xmlName = xmlPre + serverId + ".xml";
    }
    @Override
    public String getType() {
        return TYPE;
    }
//TEST
public static void main(String[] args) throws InterruptedException {
    try {
        LoadConfig.load(MuleConfig.class);
    } catch (IOException e) {
        e.printStackTrace();
    }
    Thread.sleep(5000);
    DatabaseEntity databaseEntity = new DatabaseEntity();
    databaseEntity.setTask_id("dbProxy-0001");
    System.out.println("..........." + databaseEntity.getTask_id());
    DatabaseEntity.GlobalConf globalConf = new DatabaseEntity.GlobalConf();
    globalConf.setData_type("oscar");
    globalConf.setUser_name("SHSYJQZK");
    globalConf.setPassword("Ntdh@123");
    globalConf.setIp_address("172.20.20.228");
    globalConf.setPort(2003);
    globalConf.setDatabase_ins("OSRDB");
    globalConf.setCharacter_set("UTF-8");
    globalConf.setHttp_host("0.0.0.0");
    globalConf.setHttp_port(10099);
    databaseEntity.setConfig(globalConf);
    DatabaseEntity.Flow flow = new DatabaseEntity.Flow();
    DatabaseEntity.SEsb sEsb = new DatabaseEntity.SEsb();
    sEsb.setOperate("select");
    sEsb.setSql("select * from test where a > 1");
    flow.setSEsb(sEsb);
    DatabaseEntity.TEsb tEsb = new DatabaseEntity.TEsb();
    tEsb.setMethod("get");
    tEsb.setPath("/select");
    flow.setTEsb(tEsb);
    databaseEntity.getFlows().add(flow);

    DatabaseProxy databaseProxy = new DatabaseProxy(databaseEntity);
    databaseProxy.initTask();
    databaseProxy.executeTask();
}

    @Override
    public boolean executeTask() {
        LogTool.logInfo(2, "execute task " + databaseEntity.getTask_id());
        try {
            configBuilder = new SpringXmlConfigurationBuilder(xmlPath + xmlName);
            muleContext = defaultMuleContextFactory.createMuleContext(configBuilder);
            muleContext.start();
            String muleId = muleContext.getConfiguration().getId();
            RedisUtils.redisPool.jedis(jedis -> {
                jedis.hset(MuleConfig.muleMonitor, getTaskId(), serverId);
                jedis.hset(MuleConfig.muleMonitor + serverId, MuleConfig.hostIp, muleId);
                return null;
            });
            AnaTask.addToTaskMap(getServerId(), this);
            return true;
        } catch (Exception e) {
            LogTool.logInfo(1, databaseEntity.getTask_id() + " start error.");
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
                    jedis.hdel(MuleConfig.muleMonitor, getTaskId());
                    return null;
                });
            } catch (MuleException e) {
                LogTool.logInfo(1, databaseEntity.getTask_id() + " stop error.");
                e.printStackTrace();
            }
            muleContext.dispose();
        }
        LogTool.logInfo(2, "stop task " + databaseEntity.getTask_id());
    }

    @Override
    public boolean removeTask() {
        File file = new File(xmlPath + xmlName);
        SAXBuilder saxBuilder = new SAXBuilder();
        InputStream is = ClassLoader.getSystemResourceAsStream(xmlPath + xmlName);
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

        for (Element child : children) {
            if ((child.getName().equalsIgnoreCase("flow") || (child.getName().equalsIgnoreCase("generic-config ")))
                    && child.getAttribute("name").getValue().equalsIgnoreCase(getTaskId())) {
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
                LogTool.logInfo(2, "error, delete task " + getTaskId() + ", re-save file.");
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }else {
            LogTool.logInfo(2, "error, delete task " + getTaskId() + ", delete file.");
            file.delete();
            return false;
        }

        LogTool.logInfo(2, "rm task " + getTaskId());

        return false;
    }

    @Override
    public String getTaskId() {
        return databaseEntity.getTask_id();
    }

    @Override
    public String getServerId() {
        return TYPE + "-" + databaseEntity.getConfig().getHttp_port();
    }

    @Override
    public Integer getPort() {
        return databaseEntity.getConfig().getHttp_port();
    }

    @Override
    public void initTask() {
        LogTool.logInfo(2, "init task " + databaseEntity.getTask_id());
        for (int i = 0; i < databaseEntity.getFlows().size(); i++) {
            String path = databaseEntity.getFlows().get(i).getTEsb().getPath();
        }

        try {
            databaseProxy();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JDOMException e) {
            e.printStackTrace();
        }
    }
    private String databaseProxy() throws IOException, JDOMException {

        File file = new File(xmlPath + xmlName);
        boolean exist = true;
        if (!file.exists()){
            file.createNewFile();
            exist = false;
        }else {
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
            is = ClassLoader.getSystemResourceAsStream(xmlTemplatePath);
        }
        Document document = saxBuilder.build(is);
        Element rootElement = document.getRootElement();
        List<Element> children = rootElement.getChildren();
        Namespace httpNs = Namespace.getNamespace("http", "http://www.mulesoft.org/schema/mule/http");
        Namespace dbNs = Namespace.getNamespace("db", "http://www.mulesoft.org/schema/mule/db");
        Namespace defaultNs = Namespace.getNamespace("", "http://www.mulesoft.org/schema/mule/core");
        Namespace docNs = Namespace.getNamespace("doc", "http://www.mulesoft.org/schema/mule/documentation");
        Namespace jsonNs = Namespace.getNamespace("json", "http://www.mulesoft.org/schema/mule/json");

        String listenerConfName = getServerId() + "-in";
        String genericConfig = getTaskId();
        if (!exist){
            // 创建listener config
            Element listenerConf = new Element("listener-config", httpNs);
            listenerConf.setAttribute("name", listenerConfName);
            listenerConf.setAttribute("host", "0.0.0.0");
            listenerConf.setAttribute("port", String.valueOf(getPort()));
            listenerConf.setAttribute("name", listenerConfName+" configuration", docNs);
            rootElement.addContent(0, listenerConf);
        }

        Element elementGenericConfig = new Element("generic-config", dbNs);
        if (databaseEntity.getConfig().getData_type().equalsIgnoreCase("oracle")){
            elementGenericConfig.setName("oracle-config");
            elementGenericConfig.setAttribute("name", genericConfig);
            elementGenericConfig.setAttribute("name", genericConfig, docNs);
            elementGenericConfig.setAttribute("host", databaseEntity.getConfig().getIp_address());
            elementGenericConfig.setAttribute("port", String.valueOf(databaseEntity.getConfig().getPort()));
            elementGenericConfig.setAttribute("instance", databaseEntity.getConfig().getDatabase_ins());
            elementGenericConfig.setAttribute("user", databaseEntity.getConfig().getUser_name());
            elementGenericConfig.setAttribute("password", databaseEntity.getConfig().getPassword());
        }else {
            elementGenericConfig.getAttribute("name").setValue(genericConfig);
            elementGenericConfig.getAttribute("url").setValue(databaseEntity.getConfig().getDatabase_url());
            elementGenericConfig.getAttribute("driverClassName").setValue(databaseEntity.getConfig().getDriverClassName());
            elementGenericConfig.getAttribute("name", docNs).setValue(genericConfig);
        }
        rootElement.addContent(elementGenericConfig);

        //add flow
        for (int i = 0; i < databaseEntity.getFlows().size(); i++) {
            Element wsd = new Element( "flow", defaultNs);
            wsd.setAttribute("name", getTaskId());
            Element listener = new Element("listener", httpNs);
            listener.setAttribute("config-ref", listenerConfName);
            listener.setAttribute("path", databaseEntity.getFlows().get(i).getTEsb().getPath());
            if (!(databaseEntity.getFlows().get(i).getTEsb().getMethod()).contains("*")){
                listener.setAttribute("allowedMethods", databaseEntity.getFlows().get(i).getTEsb().getMethod());
            }else {
                listener.removeAttribute("allowedMethods");
            }
            listener.setAttribute("name", "HTTP", httpNs);
            wsd.addContent(listener);
            //sql
            String s = sqlCompletion(databaseEntity.getFlows().get(i).getSEsb().getSql(), databaseEntity.getFlows().get(i).getTEsb().getMethod());
            databaseEntity.getFlows().get(i).getSEsb().setSql(s);
            Element dbOpe = null;
            if ("select".equalsIgnoreCase(databaseEntity.getFlows().get(i).getSEsb().getOperate())){
                dbOpe = new Element("select", dbNs);
            }else if ("insert".equalsIgnoreCase(databaseEntity.getFlows().get(i).getSEsb().getOperate())){
                dbOpe = new Element("insert", dbNs);
            }else if ("update".equalsIgnoreCase(databaseEntity.getFlows().get(i).getSEsb().getOperate())){
                dbOpe = new Element("update", dbNs);
            }else if ("delete".equalsIgnoreCase(databaseEntity.getFlows().get(i).getSEsb().getOperate())){
                dbOpe = new Element("delete", dbNs);
            }else {
                throw new IllegalArgumentException("no such sb operate '" + databaseEntity.getFlows().get(i).getSEsb().getOperate() + "'.");
            }
            dbOpe.setAttribute("config-ref", genericConfig);
            dbOpe.setAttribute("name", "database", docNs);
            Element dbSql = new Element("dynamic-query", dbNs);
//            dbSql.addContent("<![CDATA["+ databaseEntity.getFlows().get(i).getSEsb().getSql()+"]]>");
            CDATA cdata = new CDATA(databaseEntity.getFlows().get(i).getSEsb().getSql());
            dbSql.addContent(cdata);
            dbOpe.addContent(dbSql);
            wsd.addContent(dbOpe);

            Element objectToJson = new Element("object-to-json-transformer", jsonNs);
            objectToJson.setAttribute("name", "Object to JSON", jsonNs);
            wsd.addContent(objectToJson);

            Element formatPayload = new Element("set-payload", defaultNs);
            formatPayload.setAttribute("value",
                    "{status: true, data: #[payload], msg: \"获取信息成功\"}");
            formatPayload.setAttribute("name", "Set Payload", docNs);
            wsd.addContent(formatPayload);

            Element otjTrue = new Element("object-to-json-transformer", jsonNs);
            otjTrue.setAttribute("name", "Object to JSON", docNs);
            wsd.addContent(otjTrue);

            Element exception = new Element("catch-exception-strategy", defaultNs);
            exception.setAttribute("name", "Catch Exception Strategy", docNs);

            Element excPayload = new Element("set-payload",  defaultNs);
            excPayload.setAttribute("value", "{status: false, data: [], msg: \"错误\"}");
            excPayload.setAttribute("name", "Set Payload", docNs);
            exception.addContent(excPayload);

            Element otjElement = new Element("object-to-json-transformer", jsonNs);
            otjElement.setAttribute("name", "Object to JSON", docNs);
            exception.addContent(otjElement);

            wsd.addContent(exception);

            rootElement.addContent(wsd);
        }
        saveDocument(document, file);
        return xmlPath + xmlName;
    }

    private String sqlCompletion(String sql, String method){
        String pre;
        if ("post".equalsIgnoreCase(method)){
            pre = "payload.";
        } else {
            pre = "message.inboundProperties.'http.query.params'.";
        }
        sql = sql.replaceAll("#\\{", "#[" + pre);
        sql = sql.replaceAll("}", "]");
        return sql;
    }
}
