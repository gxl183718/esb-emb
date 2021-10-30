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
//        jsonToWs();
//        formToWs();
    }
    static void httpPathProxyTest(){
        List<PathProxy.HttpFlow> list = new ArrayList<>();
        PathProxy.HttpFlow httpFlow1 = new PathProxy.HttpFlow("f1", "/haha", "/test/haha", "*");
        list.add(httpFlow1);

        PathProxy pathProxy = new PathProxy("hpp-001", list, "localhost", 20099, "localhost", 10002);

        pathProxy.initTask();
        pathProxy.executeTask();


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
}
