package com.redpill.server;

import com.alibaba.fastjson.JSON;
import com.redpill.api.MuleTask;
import com.redpill.tool.HttpRequest;
import com.redpill.tool.Log4C;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import com.zzq.dolls.db.JDBCPool;
import lombok.Data;
import lombok.Value;
import org.apache.tools.ant.taskdefs.Ant;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Date;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class MuleMonitor extends TimerTask {


    public static ConcurrentHashMap<String, Integer> historySize = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Long> allSize = new ConcurrentHashMap<>();;
    private long currentDay;
    private long lastDay;
    private JDBCPool jdbcPool;
    private Log4C log4C = new Log4C();

    public MuleMonitor(JDBCPool jdbcPool) {
        this.jdbcPool = jdbcPool;
        long now = System.currentTimeMillis() / 1000L;
        long daySecond = 60 * 60 * 24;
        this.lastDay = now - (now + 8 * 3600) % daySecond - daySecond;

        File file = new File("/var/log/esb/");
        if(!file.exists()){
            file.mkdir();
        }
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis() / 1000L;
        long daySecond = 60 * 60 * 24;
        currentDay = now - (now + 8 * 3600) % daySecond;

        Map<String, MuleTask> taskMap = AnaTask.taskMap;
        for (String taskId : taskMap.keySet()) {
            int allSize = 0;
            long currentSize = 0;
            String hostId = RedisUtils.redisPool.jedis(jedis -> {
                return jedis.hget(MuleConfig.muleMonitor + taskId, MuleConfig.hostIp);
            });

            String urlH = "http://"+MuleConfig.hostIp+":8899/jolokia/read/Mule." + hostId +
                    ":type=Application,name=!%22application%20totals!%22/TotalEventsReceived";
            int value = 0;
            try {
                String s = HttpRequest.sendGet(urlH, null);
                ValueEnt valueEnt = JSON.parseObject(s, ValueEnt.class);
                value = valueEnt.getValue();
            }catch (Exception e){

            }

            for (String remoteIp : MuleConfig.remoteIp) {
                int value2 = 0;
                String remoteId = null;
                remoteId = RedisUtils.redisPool.jedis(jedis -> {
                    return jedis.hget(MuleConfig.muleMonitor + taskId, remoteIp);
                });
                String urlR = "http://"+remoteIp+":8899/jolokia/read/Mule." + remoteId +
                        ":type=Application,name=!%22application%20totals!%22/TotalEventsReceived";
                try {
                    String s2 = HttpRequest.sendGet(urlR, null);
                    ValueEnt valueEnt2 = JSON.parseObject(s2, ValueEnt.class);
                    value2 = valueEnt2.getValue();
                }catch (Exception e){
                }
                value += value2;
            }
            allSize = value;
            //跨越一天，将历史访问次数设置为allSize，将lastDay访问次数更新为allSize-historySize
            if ((currentDay - lastDay) > (24*60*60)){
                lastDay = currentDay - (24*60*60);
                historySize.put(taskId, allSize);
                //TODO：更新lastDay size
                String sql = "insert into esb_table(task_id,day,request_num) values('"+taskId+"','"+new Date(lastDay*1000)+"'," + allSize +
                        ") ON DUPLICATE KEY UPDATE request_num=" + allSize;
                try {
                    jdbcPool.update(sql);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            currentSize = allSize - historySize.getOrDefault(taskId, 0);
//          TODO:更新当前天的访问次数
            String sql = "insert into esb_table(task_id,day,request_num) values('"+taskId+"','"+new Date(currentDay*1000)+"'," + allSize +
                    ") ON DUPLICATE KEY UPDATE request_num=" + currentSize;

            //TODO:日志写入文件
            SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd");
            String da = sdf.format(currentDay*1000);
            String line =  log4C.info("ESB", "INFO", "", "", "", "", taskId, currentSize, 0L, "running") + "\n";
            try(FileWriter fw = new FileWriter(new File("/var/log/esb/esb-" + da + ".log"), true);
                BufferedWriter bw = new BufferedWriter(fw)){
                bw.write(line);
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                jdbcPool.update(sql);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            //            RedisUtils.redisPool.jedis(jedis -> {
//                jedis.hset(MuleConfig.eventTotal, taskId, String.valueOf(total));
//                return null;
//            });


        }
    }
    @Data
    static class ValueEnt{
        int value = 0;
    }

    public static void main(String[] args) {
        String s = "{\"request\":{\"mbean\":\"Mule.c70f2650-6614-11eb-ae0b-803049ce2e18:name=\\\"application totals\\\",type=Application\",\"attribute\":\"TotalEventsReceived\",\"type\":\"read\"},\"valuae\":0,\"timestamp\":1612352585,\"status\":200}";
        ValueEnt valueEnt = JSON.parseObject(s, ValueEnt.class);
        System.out.println(valueEnt.value);
    }
}
