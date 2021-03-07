package com.redpill.api;

import com.redpill.server.AnaTask;
import com.redpill.tool.LogTool;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.config.ConfigurationException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.config.spring.SpringXmlConfigurationBuilder;
import org.mule.context.DefaultMuleContextFactory;

import java.io.File;

public class MuleLoad implements MuleTask{
    private static final DefaultMuleContextFactory defaultMuleContextFactory = new DefaultMuleContextFactory();
    private String xmlPath = (MuleConfig.dataPath.endsWith("/")?MuleConfig.dataPath:MuleConfig.dataPath+"/")+ "cf/";
    public MuleContext muleContext;
    private SpringXmlConfigurationBuilder configBuilder;
    private String taskId;
    private String xmlName;

    public String getTaskId() {
        return taskId;
    }

    public MuleLoad(String xmlName) {
        this.xmlName = xmlName;
        this.taskId = xmlName.substring(xmlName.indexOf("-", 0)+1, xmlName.lastIndexOf("."));
    }

    @Override
    public void initTask() {

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
                return null;
            });
            LogTool.logInfo(1, "        load from disk " + taskId + " start");
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
}
