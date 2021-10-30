package com.redpill.api;

import com.redpill.server.AnaTask;
import com.redpill.tool.LogTool;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
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

public class MuleLoad implements MuleTask{
    private static final DefaultMuleContextFactory defaultMuleContextFactory = new DefaultMuleContextFactory();
    private String xmlPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "flow/";
    public MuleContext muleContext;
    private SpringXmlConfigurationBuilder configBuilder;
    private String taskId;
    private List<String> taskIds = new ArrayList<>();
    private String xmlName;


    @Override
    public String getTaskId() {
        return taskId;
    }

    @Override
    public String getServerId() {
        return xmlName.split("-")[1];
    }

    @Override
    public Integer getPort() {
        return Integer.parseInt(xmlName.split("@")[1]);
    }

    @Override
    public String getType() {
        return xmlName.split("-\\|@")[1];
    }
    public MuleLoad(String xmlName) {
        this.xmlName = xmlName;
        this.taskId = xmlName.substring(xmlName.indexOf("-", 0)+1, xmlName.lastIndexOf("."));

        System.out.println("path  " + xmlPath + xmlName + ", id   " + taskId);
    }

    @Override
    public void initTask() {
        InputStream is = null;
        try{
            is = new FileInputStream(xmlPath + xmlName);
            SAXBuilder saxBuilder = new SAXBuilder();
            Document document = null;
            try {
                document = saxBuilder.build(is);
            } catch (JDOMException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Element rootElement = document.getRootElement();
            List<Element> eles = rootElement.getChildren();

            for (Element ele : eles) {
                if("flow".equals(ele.getName())){
                    taskIds.add(ele.getAttributeValue("name"));
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (is != null){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    @Override
    public boolean executeTask() {
        try {
            configBuilder = new SpringXmlConfigurationBuilder(xmlPath + xmlName);
            muleContext = defaultMuleContextFactory.createMuleContext(configBuilder);
            muleContext.start();
            String id = muleContext.getConfiguration().getId();
            String taskInfo = RedisUtils.redisPool.jedis(jedis -> {
                jedis.hset(MuleConfig.muleMonitor + taskId, MuleConfig.hostIp, id);
                System.out.println("....1...." + MuleConfig.muleMonitor + taskId+":"+MuleConfig.hostIp+":"+ id);
                for (String s : taskIds) {
                    jedis.hset(MuleConfig.muleMonitor, s, taskId);
                }
                return null;
            });
            LogTool.logInfo(1, "        load from disk " + taskIds + " start");
            AnaTask.addToTaskMap(taskId, this);
            return true;
        } catch (Exception e) {
            LogTool.logInfo(1, taskId + " start error.");
            e.printStackTrace();
            //do not remove error task xml file.
//            removeTask();
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
    public boolean removeTask() {
        if (muleContext != null){
            this.closeTask();
        }
        File file = new File(xmlPath + xmlName);
        file.delete();
        LogTool.logInfo(2, "rm task " + taskId);
        return true;
    }
}
