package com.redpill.test;

import com.alibaba.fastjson.JSON;
import com.redpill.api.*;
import org.apache.tools.ant.taskdefs.optional.windows.Attrib;
import org.jdom.*;
import org.jdom.input.SAXBuilder;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpProxyTest {
    public static void main(String[] args) throws IOException, JDOMException {
//        httpProxyTest();//
//        wsProxyTest();//ok.2
//        httpPathProxyTest();//ok.2
        getToWs();//
//        jsonToWs();
//        formToWs();
    }
    static void httpPathProxyTest(){
        List<PathProxy.HttpFlow> list = new ArrayList<>();
        PathProxy.HttpFlow httpFlow1 = new PathProxy.HttpFlow("f1", "/haha", "/test/haha", "*");
        PathProxy.HttpFlow httpFlow2 = new PathProxy.HttpFlow("f2", "/hehe", "/test/hehe", "*");
        list.add(httpFlow1);
        list.add(httpFlow2);

        PathProxy pathProxy = new PathProxy("hpp-001", list, "localhost", 20099, "localhost", 10012);

        pathProxy.initTask();
        pathProxy.executeTask();


    }

    static void httpProxyTest(){
        HttpProxy httpProxy = new HttpProxy("hp-test-001", "0.0.0.0", 10001, "/wodege",
                "127.0.0.1", 10012, "/");
        httpProxy.initTask();
        httpProxy.executeTask();
    }
    static void wsProxyTest(){

        List<WebServiceProxy.WsFlow> flows = new ArrayList<>();
        WebServiceProxy.WsFlow wsFlow1 = new WebServiceProxy.WsFlow("ws-test-001-01",
                "http://127.0.0.1:8101/ws",
                "http://localhost:8088/cxf/webServices",
                "http://localhost:8088/cxf/webServices?wsdl");
        WebServiceProxy.WsFlow wsFlow2 = new WebServiceProxy.WsFlow("ws-test-001-02",
                "http://127.0.0.1:8102/ws",
                "http://localhost:8088/cxf/webServices",
                "http://localhost:8088/cxf/webServices?wsdl");
        flows.add(wsFlow1);
        flows.add(wsFlow2);
        WebServiceProxy webServiceProxy = new WebServiceProxy("ws-test-001", flows);

        webServiceProxy.initTask();
        webServiceProxy.executeTask();
    }
    static void getToWs(){
        Map<String, String> map = new HashMap<>();
        map.put("age","age");
        HttpGetToWs toWs = new HttpGetToWs("hgtw-test-001", "0.0.0.0", 10210, "/test",
                "WBTestInterfaceImplService", "WBTestInterfaceImplPort", "http://localhost:8088/cxf/webServices",
                "http://localhost:8088/cxf/webServices?WSDL", "sayGodBye", "gao","http://www.gao.xiao.com",
                map, true);
        toWs.initTask();
        toWs.executeTask();
    }
    static void jsonToWs(){
        JsonToWs toWs = new JsonToWs("jtw-test-001", "0.0.0.0", 10210, "/test",
                "WBTestInterfaceImplService", "WBTestInterfaceImplPort", "http://localhost:8088/cxf/webServices",
                "http://localhost:8088/cxf/webServices?WSDL", "sayGodBye",
                "gao","http://www.gao.xiao.com", true);
        toWs.initTask();
        toWs.executeTask();
    }
    static void formToWs(){
        HttpPostToWs toWs = new HttpPostToWs("ftw-test-001", "0.0.0.0", 10210, "/test",
                "WBTestInterfaceImplService", "WBTestInterfaceImplPort", "http://localhost:8088/cxf/webServices",
                "http://localhost:8088/cxf/webServices?WSDL", "sayGodBye",
                "gao","http://www.gao.xiao.com", true);
        toWs.initTask();
        toWs.executeTask();
    }
}
