package com.redpill.api;

import com.redpill.entity.DatabaseEntity;
import com.redpill.server.AnaTask;
import com.redpill.tool.LogTool;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import com.zzq.dolls.config.LoadConfig;
import org.jdom.*;
import org.jdom.input.SAXBuilder;
import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.config.spring.SpringXmlConfigurationBuilder;
import org.mule.context.DefaultMuleContextFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.redpill.CFManager.saveDocument;

public class DatabaseWsProxy implements MuleTask{
    private static final String xmlTemplatePath = "databaseWs.xml";
    private static final String wsdlTemplatePath = "wsdlTemp.wsdl";

    private static final String targetNamespace = "http://redpill.default";
    private static final String portType = "redpill";
    private static final String port = portType + "Port";
    private static final String binding = portType+ "PortBinding";
    private static final String service = portType + "Service";

    private static final DefaultMuleContextFactory defaultMuleContextFactory = new DefaultMuleContextFactory();
    String xmlPre = "DBWS-";
    private String xmlName;
    private String xmlPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "flow/";
    private String wsdlName;
    private String wsdlPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "wsdl/";
    private SpringXmlConfigurationBuilder configBuilder;
    public MuleContext muleContext;

    private DatabaseEntity databaseEntity;

    public DatabaseWsProxy(DatabaseEntity databaseEntity) {
        this.databaseEntity = databaseEntity;
        this.xmlName = xmlPre + databaseEntity.getTask_id() + ".xml";
        this.wsdlName = xmlPre + databaseEntity.getTask_id() + ".wsdl";
    }

    @Override
    public boolean executeTask() {
        LogTool.logInfo(2, "execute task " + databaseEntity.getTask_id());
        try {
            configBuilder = new SpringXmlConfigurationBuilder(xmlPath + xmlName);
            muleContext = defaultMuleContextFactory.createMuleContext(configBuilder);
            muleContext.start();
            String id = muleContext.getConfiguration().getId();
            String taskInfo = RedisUtils.redisPool.jedis(jedis -> {
                jedis.hset(MuleConfig.muleMonitor + databaseEntity.getTask_id(), MuleConfig.hostIp, id);
                return null;
            });
            AnaTask.addToTaskMap(databaseEntity.getTask_id(), this);
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
                    jedis.del(MuleConfig.muleMonitor + databaseEntity.getTask_id());
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
    public void removeTask() {
        if (muleContext != null){
            this.closeTask();
        }
        File file = new File(xmlPath + xmlName);
        file.delete();
        LogTool.logInfo(2, "rm task " + databaseEntity.getTask_id());
    }

    @Override
    public void initTask() {
        LogTool.logInfo(2, "init task " + databaseEntity.getTask_id());
        for (int i = 0; i < databaseEntity.getFlows().size(); i++) {
            String path = databaseEntity.getFlows().get(i).getTEsb().getPath();
        }
        try {
            wsdlInit(databaseEntity);
        } catch (JDOMException | IOException e) {
            LogTool.logInfo(1, "wsdl init error " + databaseEntity.getTask_id());
            e.printStackTrace();
            return;
        }
        try {
            databaseProxy();
        } catch (IOException | JDOMException e) {
            LogTool.logInfo(1, "dbWs init error " + databaseEntity.getTask_id());
            e.printStackTrace();
            return;
        }
    }

    private void wsdlInit(DatabaseEntity databaseEntity) throws JDOMException, IOException {
        SAXBuilder saxBuilder = new SAXBuilder();
        InputStream is = ClassLoader.getSystemResourceAsStream(wsdlTemplatePath);
        Document document = saxBuilder.build(is);//exception
        Element rootElement = document.getRootElement();
        Namespace xsNs = Namespace.getNamespace("xs", "http://www.w3.org/2001/XMLSchema");
        Namespace soapNs = Namespace.getNamespace("soap", "http://schemas.xmlsoap.org/wsdl/soap/");
        Namespace defaultNs = Namespace.getNamespace("", "http://schemas.xmlsoap.org/wsdl/");
        Namespace tnsNs = Namespace.getNamespace("tns", targetNamespace);
        Namespace wsamNs = Namespace.getNamespace("wsam", "http://www.w3.org/2007/05/addressing/metadata");

        rootElement.addNamespaceDeclaration(tnsNs);
        rootElement.setAttribute("targetNamespace", targetNamespace);
        Element types = rootElement.getChild("types", defaultNs);
        Element schema = types.getChild("schema", xsNs);
        schema.removeContent();
        List<Attribute> attributes = schema.getAttributes();
        for (Attribute attribute : attributes) {
            System.out.println(attribute.getName());
        }
        for (DatabaseEntity.Flow flow : databaseEntity.getFlows()) {
            String operation = flow.getTEsb().getMethod();

            Element xsElement1 = new Element("element", xsNs);
            xsElement1.setAttribute("type", "tns:" + operation);
            xsElement1.setAttribute("name", operation);
            schema.addContent(xsElement1);

            Element xsElement2 = new Element("element", xsNs);
            xsElement2.setAttribute("type", "tns:" + operation + "Response");
            xsElement2.setAttribute("name", operation + "Response");
            schema.addContent(xsElement2);

            Element complexType1 = new Element("complexType", xsNs);
            complexType1.setAttribute("name", operation);
            Element sequence = new Element("sequence", xsNs);
            Element childElement = new Element("element", xsNs);
            childElement.setAttribute("name", "params")
                    .setAttribute("type", "xs:string")
                    .setAttribute("minOccurs", "0");
            sequence.addContent(childElement);
            complexType1.addContent(sequence);
            schema.addContent(complexType1);

            Element complexType2 = new Element("complexType", xsNs);
            complexType2.setAttribute("name", operation + "Response");
            Element sequence2 = new Element("sequence", xsNs);
            Element childElement2 = new Element("element", xsNs);
            childElement2.setAttribute("name", "result")
                    .setAttribute("type", "xs:string")
                    .setAttribute("minOccurs", "0");
            sequence2.addContent(childElement2);
            complexType2.addContent(sequence2);
            schema.addContent(complexType2);

            Element message1 = new Element("message", defaultNs)
                    .setAttribute("name", operation);
            Element part1 = new Element("part", defaultNs)
                    .setAttribute("name", "parameters")
                    .setAttribute("element", "tns:" + operation);
            message1.addContent(part1);
            rootElement.addContent(message1);

            Element message2 = new Element("message", defaultNs)
                    .setAttribute("name", operation + "Response");
            Element part2 = new Element("part", defaultNs)
                    .setAttribute("name", "parameters")
                    .setAttribute("element", "tns:" + operation + "Response");
            message2.addContent(part2);
            rootElement.addContent(message2);

            Element element = rootElement.getChild("portType", defaultNs);
            element.setAttribute("name", portType);
            Element element1 = new Element("operation", defaultNs).setAttribute("name", operation);
            Element element2 = new Element("input", defaultNs)
                    .setAttribute("Action", targetNamespace + "/" + portType + "/" + operation + "Request", wsamNs)
                    .setAttribute("message", "tns:" + operation);
            Element element3 = new Element("output", defaultNs)
                    .setAttribute("Action", targetNamespace + "/" + portType + "/" + operation + "Response", wsamNs)
                    .setAttribute("message", "tns:" + operation + "Response");
            element1.addContent(element2);
            element1.addContent(element3);
            element.addContent(element1);

            Element elementBinding = rootElement.getChild("binding", defaultNs)
                    .setAttribute("name", binding)
                    .setAttribute("type", "tns:" + portType);
            Element elementOperation = new Element("operation", defaultNs)
                    .setAttribute("name", operation);
            Element elementSoap = new Element("operation", soapNs)
                    .setAttribute("soapAction", "");
            Element body1 = new Element("body", soapNs).setAttribute("use", "literal");
            Element body2 = new Element("body", soapNs).setAttribute("use", "literal");
            Element input = new Element("input", defaultNs);
            Element output = new Element("output", defaultNs);
            input.addContent(body1);
            output.addContent(body2);
            elementOperation.addContent(elementSoap).addContent(input).addContent(output);
            elementBinding.addContent(elementOperation);
        }
        Element elementService = rootElement.getChild("service", defaultNs);
        elementService.setAttribute("name", service);
        Element port1 = elementService.getChild("port", defaultNs);
        port1.setAttribute("name", port).setAttribute("binding", "tns:" + binding);
        port1.getChild("address", soapNs).setAttribute("location", "localhost:8080/mule");

        File file = new File( wsdlPath + wsdlName );
        if (!file.exists()){
            file.createNewFile();
        }
        saveDocument(document, file);
    }

    private String databaseProxy() throws IOException, JDOMException {
        SAXBuilder saxBuilder = new SAXBuilder();
        InputStream is = ClassLoader.getSystemResourceAsStream(xmlTemplatePath);
        Document document = saxBuilder.build(is);
        Element rootElement = document.getRootElement();
        List<Element> children = rootElement.getChildren();
        String httpConfName = databaseEntity.getTask_id() + "-listener-conf";
        String databaseConfName = databaseEntity.getTask_id() + "-db-conf";
        Namespace jsonNs = Namespace.getNamespace("json", "http://www.mulesoft.org/schema/mule/json");
        Namespace dbNs = Namespace.getNamespace("db", "http://www.mulesoft.org/schema/mule/db");
        Namespace httpNs = Namespace.getNamespace("http", "http://www.mulesoft.org/schema/mule/http");
        Namespace mulexmlNs = Namespace.getNamespace("mulexml", "http://www.mulesoft.org/schema/mule/xml");
        Namespace cxfNs = Namespace.getNamespace("cxf", "http://www.mulesoft.org/schema/mule/cxf");
        Namespace docNs = Namespace.getNamespace("doc", "http://www.mulesoft.org/schema/mule/documentation");
        Namespace xsiNs = Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        Namespace defaultNs = Namespace.getNamespace("", "http://www.mulesoft.org/schema/mule/core");

        Element globalListener = rootElement.getChild("listener-config", httpNs);
        String listenerConfName = databaseEntity.getTask_id() + "-listener-conf";
        globalListener.setAttribute("name", listenerConfName);
        globalListener.setAttribute("port", String.valueOf(databaseEntity.getConfig().getHttp_port()));
        globalListener.setAttribute("host", databaseEntity.getConfig().getHttp_host());

        Element cxfConf = rootElement.getChild("configuration", cxfNs);
        String cxfConfName = databaseEntity.getTask_id() + "-cxf-conf";
        cxfConf.setAttribute("name", cxfConfName);

        Element dbConf = rootElement.getChild("generic-config", dbNs);
        String dbConfName = databaseEntity.getTask_id() + "-db-conf";
        dbConf.setAttribute("name", dbConfName);
        dbConf.setAttribute("url", databaseEntity.getConfig().getDatabase_url());
        dbConf.setAttribute("driverClassName", databaseEntity.getConfig().getDriverClassName());

        Element flow = rootElement.getChild("flow", defaultNs);
        Element flowListener = flow.getChild("listener", httpNs);
        flowListener.setAttribute("config-ref", listenerConfName);
        flowListener.setAttribute("path", databaseEntity.getFlows().get(0).getTEsb().getPath());

        Element proxyService = flow.getChild("proxy-service", cxfNs);
        proxyService.setAttribute("wsdlLocation", wsdlPath + wsdlName);
        proxyService.setAttribute("service", service);
        proxyService.setAttribute("port", port);
        proxyService.setAttribute("namespace", targetNamespace);

        Element flowChoice = flow.getChild("choice", defaultNs);

        Element exception = flow.getChild("catch-exception-strategy", defaultNs);
        Element exceptionChoice = exception.getChild("choice", defaultNs);

        //add flow
        for (int i = 0; i < databaseEntity.getFlows().size(); i++) {
            String subFlowName = "sub-flow-" + i;
            String wsOperation = databaseEntity.getFlows().get(i).getTEsb().getMethod();
            String sql = databaseEntity.getFlows().get(i).getSEsb().getSql();
            Element when = new Element("when", defaultNs);
            when.setAttribute("expression", "#[flowVars.varOperation == '" + wsOperation + "']");
            Element flowRef = new Element("flow-ref", defaultNs);
            flowRef.setAttribute("name", subFlowName);
            flowRef.setAttribute("name", "Flow Reference", docNs);
            when.addContent(flowRef);
            flowChoice.addContent(when);

            Element exceptionWhen = new Element("when", defaultNs);
            exceptionWhen.setAttribute("expression", "#[flowVars.varOperation == '" + wsOperation + "']");
            Element exceptionPayload = new Element("set-payload", defaultNs);
            exceptionPayload.setAttribute("value", "<ns2:" + wsOperation + "Response xmlns:ns2=\"" + targetNamespace + "\">           <result>            {\"status\": false, \"data\": [], \"msg\": \"错误\"}          </result>        </ns2:"+wsOperation+"Response> ");
            exceptionWhen.addContent(exceptionPayload);
            exceptionChoice.addContent(exceptionWhen);

            Element subFlow = new Element("sub-flow", defaultNs);
            subFlow.setAttribute("name", subFlowName);
            //sql
            sql = sqlCompletion(sql, databaseEntity.getFlows().get(i).getTEsb().getMethod());
            databaseEntity.getFlows().get(i).getSEsb().setSql(sql);
            String sqlOperation = databaseEntity.getFlows().get(i).getSEsb().getOperate();
            Element dbOpe = null;
            if ("select".equalsIgnoreCase(sqlOperation)){
                dbOpe = new Element("select", dbNs);
            }else if ("insert".equalsIgnoreCase(sqlOperation)){
                dbOpe = new Element("insert", dbNs);
            }else if ("update".equalsIgnoreCase(sqlOperation)){
                dbOpe = new Element("update", dbNs);
            }else if ("delete".equalsIgnoreCase(sqlOperation)){
                dbOpe = new Element("delete", dbNs);
            }else {
                throw new IllegalArgumentException("no such sb operate '" + sqlOperation + "'.");
            }
            dbOpe.setAttribute("config-ref", databaseConfName);
            dbOpe.setAttribute("name", "database", docNs);
            Element dbSql = new Element("dynamic-query", dbNs);
            CDATA cdata = new CDATA(sql);
            dbSql.addContent(cdata);
            dbOpe.addContent(dbSql);
            subFlow.addContent(dbOpe);
            Element json = new Element("object-to-json-transformer", jsonNs);
            json.setAttribute("name", "Object to JSON", jsonNs);
            json.setAttribute("name", "Object to JSON", docNs);
            subFlow.addContent(json);
            Element payload = new Element("set-payload", defaultNs);
            payload.setAttribute("name", "Set Payload", docNs);
            payload.setAttribute("value",
                    "<ns2:" + wsOperation + "Response xmlns:ns2=\""+targetNamespace+"\">" +
                           "<result>"+
                            "{\"status\": true, \"data\": #[payload], \"msg\": \"获取信息成功\"}"
                            +"</result>"+
                           "</ns2:"+wsOperation+"Response>"
            );
            subFlow.addContent(payload);
            rootElement.addContent(subFlow);

        }
        File file = new File(xmlPath + xmlName);
        if (!file.exists()){
            boolean newFile = file.createNewFile();
//            System.out.println(newFile);
        }
//        System.out.println(file.length());
        saveDocument(document, file);
        return xmlPath + xmlName;
    }

    private String sqlCompletion(String sql, String method){
        String pre = "flowVars.";
        sql = sql.replaceAll("#\\{", "#[" + pre);
        sql = sql.replaceAll("}", "]");
        return sql;
    }

    //for test
    public static void main(String[] args){

        DatabaseEntity databaseEntity = new DatabaseEntity();
        databaseEntity.setTask_id("001");
        databaseEntity.getConfig().setHttp_port(8888);
        databaseEntity.getConfig().setHttp_host("0.0.0.0");
        databaseEntity.getConfig().setData_type("");
        databaseEntity.getConfig().setIp_address("172.20.20.226");
        databaseEntity.getConfig().setPort(5866);
        databaseEntity.getConfig().setDatabase_ins("highgo");
        databaseEntity.getConfig().setData_type("highgo");
        databaseEntity.getConfig().setUser_name("sysdba");
        databaseEntity.getConfig().setPassword("Ntdh@123456");

        DatabaseEntity.Flow flow = new DatabaseEntity.Flow();
        DatabaseEntity.TEsb tEsb = new DatabaseEntity.TEsb();
        tEsb.setMethod("FuncOne");
        tEsb.setPath("/dbws");
        flow.setTEsb(tEsb);
        DatabaseEntity.SEsb sEsb = new DatabaseEntity.SEsb();
        sEsb.setSql("select * from user_tables");
        sEsb.setOperate("select");
        flow.setSEsb(sEsb);

        DatabaseEntity.Flow flow2 = new DatabaseEntity.Flow();
        DatabaseEntity.TEsb tEsb2 = new DatabaseEntity.TEsb();
        tEsb2.setMethod("FuncTwo");
        tEsb2.setPath("/dbws");
        flow2.setTEsb(tEsb2);
        DatabaseEntity.SEsb sEsb2 = new DatabaseEntity.SEsb();
        sEsb2.setSql("select #{c} from #{d}");
        sEsb2.setOperate("select");
        flow2.setSEsb(sEsb2);

        List list = new ArrayList();
        list.add(flow);
        list.add(flow2);
        databaseEntity.setFlows(list);

        DatabaseWsProxy databaseWsProxy = new DatabaseWsProxy(databaseEntity);
        databaseWsProxy.initTask();
        databaseWsProxy.executeTask();
    }

}
