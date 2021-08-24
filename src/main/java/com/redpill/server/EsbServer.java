package com.redpill.server;

import com.alibaba.fastjson.JSON;
import com.redpill.api.DatabaseProxy;
import com.redpill.api.MuleLoad;
import com.redpill.api.MuleTask;
import com.redpill.entity.DatabaseEntity;
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
    public static void main(String[] args) {
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
        if (!args[0].equals("test")){
//System.out.println("ggggggggg:"+flowData.getAbsolutePath());
            LogTool.logInfo(1, "【3】 Load history tasks, size(" + flowData.list().length + ").");
            for (String s : flowData.list()) {
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
            chose = "test";
            //for test
            String task;
            task = "{\"task_id\":\"b0dea983-5bfa-4a0f-a50e-e21e50fd0822\",\"app_id\":\"af242998-1683-4e14-9a26-b759252c0514\",\"tenant_id\":\"aded50a2-2378-4077-bdfc-948a5e554932\",\"resource_id\":\"709dedb4-8c54-4474-b11b-cfc06134dfae\",\"resource_name\":\"人均GDP(崇川)\",\"task_name\":\"应急管理局_人均GDP(崇川)_http代理\",\"task_desc\":\"应急管理局_人均GDP(崇川)_http代理\",\"task_type\":\"13\",\"state\":0,\"config\":{\"http_host\":\"0.0.0.0\",\"http_port\":9005,\"data_type\":\"MySql\",\"ip_address\":\"172.20.20.217\",\"port\":3306,\"database_ins\":\"kfqyjgljqzk\",\"user_name\":\"root\",\"password\":\"Ntdh@123\",\"character_set\":\"utf8\"},\"flows\":[{\"s_esb\":{\"operate\":\"select\",\"sql\":\" select TJSJ,ZZL,GSQ,GDP,RJCZRK,CJSJ from RJGDP where  CJSJ >=#{start_time} and CJSJ <=#{end_time}\",\"supply_dept_id\":\"c2e0e3ef-56f5-4999-8e0d-5dcbd3b0e548\",\"supply_dept\":\"应急管理局\"},\"t_esb\":{\"path\":\"/listGdp\",\"method\":\"GET\",\"demand_dept_id\":\"\",\"demand_dept\":\"\"}}]}";
            DatabaseEntity databaseEntity = JSON.parseObject(task, DatabaseEntity.class);
            DatabaseProxy databaseProxy = new DatabaseProxy(databaseEntity);
            databaseProxy.initTask();
            databaseProxy.executeTask();
        }
    }
}
