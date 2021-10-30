package com.redpill.server;

import com.alibaba.fastjson.JSON;
import com.redpill.api.*;
import com.redpill.entity.*;
import com.redpill.tool.FileTool;
import com.redpill.tool.LogTool;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import com.zzq.dolls.config.LoadConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AnaTask {
    public static Map<String, MuleTask> taskMap = new ConcurrentHashMap<>();

    public static void rmFromMap(String id){
        taskMap.remove(id);
    }
    public static void addToTaskMap(String id, MuleTask task){
        taskMap.put(id, task);
    }
    public static MuleTask getFromTaskMap(String serverId){return taskMap.get(serverId);}
    class TaskAction {
        public static final int ADD = 0;
        public static final int UPDATE = 1;
        public static final int DELETE = 2;
    }
    public static  void loadFromDisk(String path){
        File file = new File(path);
        if (file.isDirectory()) {
        //
        }else {

        }
    }

    public static boolean taskHandle(TaskEntity taskEntity) throws Exception {
        if (null == taskEntity){
            throw new Exception(" do not fined task in task info");
        }
        if (taskEntity.getState() == TaskAction.ADD){
            add(taskEntity);
        }else if (taskEntity.getState() == TaskAction.DELETE){
            del(taskEntity);
        }
        return false;
    }

    //TODO: getServerId 获取任务中代理服务的id，代理服务id对应唯一的xml文件
    public static String getServerId(String taskInfo) throws Exception{
        TaskInfoEntity taskInfoEntity = JSON.parseObject(taskInfo, TaskInfoEntity.class);
        if (null == taskInfoEntity.getServerPort() || null == taskInfoEntity.getTask_type()){
            throw new NullPointerException("Server port and task type is required.");
        }else {
            return taskInfoEntity.getTask_type() + "@" + taskInfoEntity.getServerPort();
        }
    }

    public static String getInfoType(String taskId){
        String taskInfo = RedisUtils.redisPool.jedis(jedis -> {
            return jedis.hget(MuleConfig.taskInfo, taskId);
        });
        HttpProxyEntity httpProxyEntity1 = JSON.parseObject(taskInfo, HttpProxyEntity.class);
        return httpProxyEntity1.getTask_type();
    }

    public static boolean add(TaskEntity taskEntity) throws Exception {
        String id = taskEntity.getTask_id();
        String taskInfo = RedisUtils.redisPool.jedis(jedis -> {
            return jedis.hget(MuleConfig.taskInfo, id);
        });

        HttpProxyEntity httpProxyEntity1 = JSON.parseObject(taskInfo, HttpProxyEntity.class);
        //TODO:
//        String serverId = getServerId(taskInfo);

        /*
        每个任务仅支持配置一个接口映射
         */

        if (httpProxyEntity1.getTask_type().equalsIgnoreCase("11")){
            LogTool.logInfo(1, "type 11 : " + taskInfo);
            List<PathProxy.HttpFlow> list = new ArrayList<>();
            for (int i = 0; i < httpProxyEntity1.getFlows().size(); i++) {
                PathProxy.HttpFlow httpFlow1 = new PathProxy.HttpFlow(id, httpProxyEntity1.getFlows().get(i).getT_esb().getPath(),
                        httpProxyEntity1.getFlows().get(i).getS_esb().getPath(), httpProxyEntity1.getFlows().get(i).getT_esb().getMethod());
                list.add(httpFlow1);
            }
            PathProxy pathProxy = new PathProxy(httpProxyEntity1.getTask_id(), list, httpProxyEntity1.getFlows().get(0).getT_esb().getIp_address(),
                    Integer.valueOf(httpProxyEntity1.getFlows().get(0).getT_esb().getPort()), httpProxyEntity1.getFlows().get(0).getS_esb().getIp_address(), Integer.parseInt(httpProxyEntity1.getFlows().get(0).getS_esb().getPort()));
            //TODO://init failed
            pathProxy.initTask();
            restartMule(pathProxy);

        }else if (httpProxyEntity1.getTask_type().equalsIgnoreCase("22")){
            LogTool.logInfo(1, "type 22 : " + taskInfo);
            WsProxyEntity wsProxyEntity = JSON.parseObject(taskInfo, WsProxyEntity.class);
            List<WebServiceProxy.WsFlow> flows = new ArrayList<>();
            for (WsProxyEntity.WsFlow flow : wsProxyEntity.getFlows()) {
                String flowId = "flow-" + System.nanoTime();
                String inboundAddress = flow.getT_esb().getIp_address() + ":" + flow.getT_esb().getPort() + flow.getT_esb().getPath();
                String outboundAddress = flow.getS_esb().getIp_address() + ":" + flow.getS_esb().getPort() + flow.getS_esb().getPath();
                String wsdl = flow.getS_esb().getWsdl();
                WebServiceProxy.WsFlow wsFlow = new WebServiceProxy.WsFlow(flowId, inboundAddress, outboundAddress, wsdl );
                flows.add(wsFlow);
            }
            WebServiceProxy webServiceProxy = new WebServiceProxy(wsProxyEntity.getTask_id(), flows);
            webServiceProxy.initTask();
            webServiceProxy.executeTask();

        }else if (httpProxyEntity1.getTask_type().equalsIgnoreCase("12")) {
            LogTool.logInfo(1, "type 12 : " + taskInfo);
            HttpWsEntity httpWsEntity = JSON.parseObject(taskInfo, HttpWsEntity.class);
            //TODO:临时修改
//            httpWsEntity.getFlows().get(0).getT_esb().setMethod("get");
            HttpToWsFlows httpToWsFlows = new HttpToWsFlows(httpWsEntity);
            httpToWsFlows.initTask();
            restartMule(httpToWsFlows);
//            httpToWsFlows.executeTask();
        }else if (httpProxyEntity1.getTask_type().equalsIgnoreCase("13")){
            LogTool.logInfo(1, "type 13 : " + taskInfo);
            DatabaseEntity databaseEntity = JSON.parseObject(taskInfo, DatabaseEntity.class);
            DatabaseProxy databaseProxy = new DatabaseProxy(databaseEntity);
            databaseProxy.initTask();
            databaseProxy.executeTask();

        }else if (httpProxyEntity1.getTask_type().equalsIgnoreCase("23")){
            LogTool.logInfo(1, "type 23 : " + taskInfo);
            DatabaseEntity databaseEntity = JSON.parseObject(taskInfo, DatabaseEntity.class);
            DatabaseWsProxy databaseWsProxy = new DatabaseWsProxy(databaseEntity);
            databaseWsProxy.initTask();
            databaseWsProxy.executeTask();
        }else {
            LogTool.logInfo(1, "not existed type " + httpProxyEntity1.getTask_type());
            return false;
        }
        return false;
    }
    public static boolean del(TaskEntity taskEntity) throws Exception {
        //TODO：webservice 重新考慮
        // taskId->serverId
        // serverId->close server
        // update xml
        // restart
        // update serverId->muleId
        String infoType = getInfoType(taskEntity.getTask_id());
        if (infoType.equalsIgnoreCase("22")){
            taskMap.get(taskEntity.getTask_id()).closeTask();
            taskMap.get(taskEntity.getTask_id()).removeTask();
            AnaTask.rmFromMap(taskEntity.getTask_id());
            return true;
        }else {
            String serverId = RedisUtils.redisPool.jedis(jedis -> {
                return jedis.hget(MuleConfig.muleMonitor, taskEntity.getTask_id());
            });
            MuleTask muleTask = taskMap.get(serverId);
            boolean serverHasFlows = false;
            if (muleTask != null) {
                muleTask.closeTask();
                serverHasFlows = muleTask.removeTask();
            }
            if (serverHasFlows){
                muleTask.executeTask();
            }else {
                rmFromMap(serverId);
            }
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        String taskInfo = "";
        DatabaseEntity databaseEntity = JSON.parseObject(taskInfo, DatabaseEntity.class);
        DatabaseProxy databaseProxy = new DatabaseProxy(databaseEntity);
        databaseProxy.initTask();
        databaseProxy.executeTask();
    }

    public static void restartMule(MuleTask muleTask) throws Exception {
        synchronized (muleTask.getPort()){
            MuleTask oldMule = AnaTask.getFromTaskMap(muleTask.getServerId());
            //存在server
            if (oldMule != null){
                oldMule.closeTask();
                AnaTask.rmFromMap(muleTask.getServerId());
            }
            //启动新服务
            boolean ok = muleTask.executeTask();
            addToTaskMap(muleTask.getServerId(), muleTask);
        }
    }
}
