package com.redpill.server;

import com.alibaba.fastjson.JSON;
import com.redpill.api.DatabaseProxy;
import com.redpill.api.MuleLoad;
import com.redpill.api.MuleTask;
import com.redpill.api.PathProxy;
import com.redpill.entity.DatabaseEntity;
import com.redpill.entity.HttpProxyEntity;
import com.redpill.tool.LogTool;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import com.zzq.dolls.config.LoadConfig;
import com.zzq.dolls.db.JDBCPool;
import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.annotations.expressions.Mule;
import org.mule.api.config.ConfigurationException;
import org.mule.api.lifecycle.InitialisationException;

import java.io.File;
import java.io.IOException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class EsbServer {

    public static JDBCPool jdbc;

    public static String chose;
    public static void main(String[] args) throws Exception {
        try {
            LoadConfig.load(MuleConfig.class);
        } catch (IOException e) {
            LogTool.logInfo(1, "load config error, system out with code(0). " + e.getMessage());
            System.exit(0);
            e.printStackTrace();
        }

        jdbc = JDBCPool.builder()
                .driver("com.mysql.jdbc.Driver")
                .url(MuleConfig.db_url)
                .userName(MuleConfig.db_user)
                .password(MuleConfig.db_password)
                .build();

        LogTool.logInfo(1, "【1】CONFIG params : " + LoadConfig.toString(MuleConfig.class));
        //create data dir if not exist
        String wsdl = MuleConfig.dataPath + "wsdl/";
        String flow = MuleConfig.dataPath + "flow/";
        String xslt = MuleConfig.dataPath + "xslt/";
        File file = new File(MuleConfig.dataPath);
        if (!file.exists()){
            LogTool.logInfo(1, "【1】Please make sure tha data path '"+ MuleConfig.dataPath +"' existed!");
            return;
        }
        LogTool.logInfo(1, "【1】DATA path : " + MuleConfig.dataPath);

        File wsdlData = new File(wsdl);
        File flowData = new File(flow);
        File xsltData = new File(xslt);
        boolean dataSons = true;
        if (!wsdlData.exists()) {
            dataSons = dataSons & wsdlData.mkdir();
        }
        if (!flowData.exists()) {
            dataSons = dataSons & flowData.mkdir();
        }
        if (!xsltData.exists()){
            dataSons = dataSons & xsltData.mkdir();
        }
        if (!dataSons){
            LogTool.logInfo(1, "Create Secondary data dirs error, do not have permission.");
            return;
        }

//        first start to load history task
        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                StringBuilder stringBuilder = new StringBuilder("  ");
                AnaTask.taskMap.forEach((k, v)->{
                    stringBuilder.append(k + ",");
                });
                stringBuilder.deleteCharAt(stringBuilder.length()-1);
                LogTool.logInfo(2, "【JOB】running task : " + stringBuilder.toString());
            }
        };
        timer.schedule(timerTask, 10*1000L, 600*1000L);
        //start jolokia for monitor
        try {
            MuleContext muleContext = MuleTask.defaultMuleContextFactory.createMuleContext("jolokia.xml");
            muleContext.start();

            Timer timer1 = new Timer();
            MuleMonitor muleMonitor = new MuleMonitor(jdbc);
            Date today = new Date(System.currentTimeMillis());
//            TODO:查看库中本时段请求次数HS，将mulemMnitor中历史访问量设置为 -HS
            String sql = "select task_id,request_num from esb_table where day='" + today + "'";
            System.out.println("..........................sql is : " + sql);
            Map<String, Integer> results = jdbc.select(sql, resultSet -> {
                Map<String, Integer> result = new HashMap<>();
                while(resultSet.next()){
                    String id = resultSet.getString("task_id");
                    int num = resultSet.getInt("request_num");
                    result.put(id, num);
                    System.out.println("....." + id + ":" + num);
                }
                return result;
            });
            for (Map.Entry<String, Integer> entry : results.entrySet()) {
                System.out.println("历史访问次数：" + entry.getKey() + ":" + entry.getValue());
                MuleMonitor.historySize.put(entry.getKey(), -entry.getValue());
            }
            timer1.schedule(muleMonitor, 10*1000L, MuleConfig.monitorSch);
            LogTool.logInfo(1, "【2】MONITOR for mule server");
        } catch (InitialisationException e) {
            e.printStackTrace();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        } catch (MuleException | SQLException e) {
            e.printStackTrace();
        }

        //task consume thread
        System.out.println("1:"+args[0]);
        if (!args[0].equals("test")){
System.out.println("ggggggggg:"+flowData.getAbsolutePath());
            LogTool.logInfo(1, "【3】 Load history tasks, size(" + flowData.list().length + ").");
            for (String s : flowData.list()) {
                if(!s.endsWith("xml")){
                    continue;
                }
                LogTool.logInfo(1, "    LOAD task : " + s);
                MuleLoad muleLoad = new MuleLoad(s);
                muleLoad.initTask();
                muleLoad.executeTask();
            }
            chose = "sys";
            RabbitMQConsumer rabbitMQConsumer = new RabbitMQConsumer();
            Thread thread = new Thread(rabbitMQConsumer, "rabbit-consume");
            thread.start();

            //consume from redis
            new Thread(()->{
                LogTool.logInfo(1, "sync data from back nodes .");
                while(true){
                    String task = RedisUtils.redisPool.jedis(jedis -> {
                        String info =  jedis.lpop("back-" + MuleConfig.hostIp);
                        return info;
                    });
                    if (task == null){
                        try {
                            Thread.sleep(10*1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        continue;
                    }
                    TaskEntity taskEntity = null;
                    try{
                        taskEntity = JSON.parseObject(task, TaskEntity.class);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    LogTool.logInfo(2, "recv-back task : " + task);
                    try {
                        AnaTask.taskHandle(taskEntity);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    LogTool.logInfo(2, "task ok : " + task);
                }
            }).start();


        }else {
            String task;
            Scanner scanner = new Scanner(System.in);
            while(true){
                System.out.println("please enter a task（enter ‘exit’  to exit!）:");
                task = scanner.nextLine();
                if ("exit".equals(task)){
                    break;
                }
                System.out.println("get a task :" + task);
                chose = "test";
                //for test
                HttpProxyEntity httpProxyEntity1 = JSON.parseObject(task, HttpProxyEntity.class);
                String id = httpProxyEntity1.getTask_id();

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
                AnaTask.restartMule(pathProxy);
            }
        }
    }
}
