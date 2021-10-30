package com.redpill.server;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.redpill.api.MuleTask;
import com.redpill.tool.HttpRequest;
import com.redpill.tool.Log4C;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import com.zzq.dolls.db.JDBCPool;
import lombok.Data;
import lombok.Value;
import org.apache.tools.ant.taskdefs.Ant;

import javax.json.Json;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Date;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
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

        //key is serverId
        Map<String, MuleTask> taskMap = AnaTask.taskMap;
        for (String serverId : taskMap.keySet()) {
//            print("serverId--" + serverId);
            List<String> taskIds = getTaskIds(serverId);
            for (String taskId : taskIds) {
//                print("taskId--" + taskId);
                int allSize = 0;
                long currentSize = 0;
                String muleId = RedisUtils.redisPool.jedis(jedis -> {
                    return jedis.hget(MuleConfig.muleMonitor + serverId, MuleConfig.hostIp);
                });
                String urlH = "http://"+MuleConfig.hostIp+":8899/jolokia/read/Mule." + muleId +
                        ":type=Flow,name=!%22"+ taskId +"!%22/";
//                print("url--" + urlH);
                int total = 0;
                int avr = 1;
                try {
                    String s = HttpRequest.sendGet(urlH, null);
                    REnt rEnt = JSON.parseObject(s, REnt.class);
                    ValueEnt valueEnt = rEnt.getValue();
                    total = valueEnt.getTotalEventsReceived();
                    avr = valueEnt.getAverageProcessingTime();
                }catch (Exception e){

                }
//                print("value--" + total);
                for (String remoteIp : MuleConfig.remoteIp) {
                    int value2 = 0;
                    String remoteId = null;
                    remoteId = RedisUtils.redisPool.jedis(jedis -> {
                        return jedis.hget(MuleConfig.muleMonitor + taskId, remoteIp);
                    });
                    String urlR = "http://"+remoteIp+":8899/jolokia/read/Mule." + remoteId +
                            ":type=Flow,name=!%22"+ taskId +"!%22/";
                    try {
                        String s2 = HttpRequest.sendGet(urlR, null);
                        REnt rEnt = JSON.parseObject(s2, REnt.class);
                        ValueEnt valueEnt2 = rEnt.getValue();
                        value2 = valueEnt2.getTotalEventsReceived();
                    }catch (Exception e){

                    }
                    total += value2;
                }
                allSize = total;
                //跨越一天，将历史访问次数设置为allSize，将lastDay访问次数更新为allSize-historySize
                if ((currentDay - lastDay) > (24*60*60)){
                    lastDay = currentDay - (24*60*60);
                    historySize.put(taskId, allSize);
                    //TODO：更新lastDay size
                    String sql = "insert into esb_table(task_id,day,request_num,avr_request_time) values('"+taskId+"','"+new Date(lastDay*1000)+"'," + allSize +
                            "," + avr + ") ON DUPLICATE KEY UPDATE request_num=" + allSize;
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
    }

    private List<String> getTaskIds(String serverId) {
        List<String> tasks = new ArrayList<>();
        String taskInfo = RedisUtils.redisPool.jedis(jedis -> {
            Map<String, String> map = jedis.hgetAll(MuleConfig.muleMonitor);
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (entry.getValue().equals(serverId)){
                    tasks.add(entry.getKey());
                }
            }
            return null;
        });
        return tasks;
    }

    @Data
    public static class ValueEnt{
        int totalEventsReceived;
        int minProcessingTime;
        int maxProcessingTime;
        int averageProcessingTime;
        int executionErrors;
    }
    @Data
    public static class REnt{
        ValueEnt value;
    }

    public static void main(String[] args) {
        String s = "{\"request\":{\"mbean\":\"Mule.c70f2650-6614-11eb-ae0b-803049ce2e18:name=\\\"application totals\\\",type=Application\",\"attribute\":\"TotalEventsReceived\",\"type\":\"read\"},\"valuae\":0,\"timestamp\":1612352585,\"status\":200}";
        ValueEnt valueEnt = JSON.parseObject(s, ValueEnt.class);
        System.out.println(valueEnt.totalEventsReceived);
    }

    public static void print(String s){
        StringBuilder sb = new StringBuilder("");
        sb.append("【monitor log】:");
        sb.append(s);
        System.out.println(sb.toString());
    }
}
