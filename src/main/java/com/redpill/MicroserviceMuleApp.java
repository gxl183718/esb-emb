package com.redpill;

import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.context.DefaultMuleContextFactory;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;

//@Configuration
@ComponentScan
public class MicroserviceMuleApp {

    public static void main(String[] args) throws InterruptedException, MuleException, IOException {
//        ApplicationContext context =
//                new AnnotationConfigApplicationContext(MicroserviceMuleApp.class);
        DefaultMuleContextFactory muleContextFactory = new DefaultMuleContextFactory();
//        SpringXmlConfigurationBuilder configBuilder = new SpringXmlConfigurationBuilder("conf/flow/wsProxyTem.xml");
//        SpringXmlConfigurationBuilder configBuilder = new SpringXmlConfigurationBuilder("httpGetToWsFlows.xml");
//        MuleContext muleContext = muleContextFactory.createMuleContext(configBuilder);
//        MuleContext muleContext = muleContextFactory.createMuleContext("conf/cfTem/JsonToWS.xml");
//        MuleContext muleContext = muleContextFactory.createMuleContext("conf/cfTem/httpGetToWsTem.xml");
        MuleContext muleContext = muleContextFactory.createMuleContext(
                "data/xml/DBWS/DBWS-001.xml");
//        MuleContext muleContext = muleContextFactory.createMuleContext(args[0]);
        muleContext.start();
        System.out.println("is starting");
        Thread.sleep(1000000);
        System.out.println("stopping mule server");
        muleContext.stop();
        muleContext.dispose();
//
    }

}
