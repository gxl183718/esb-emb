package com.redpill.server;

import com.alibaba.fastjson.JSON;
import com.redpill.api.MuleTask;
import com.redpill.tool.HttpRequest;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import lombok.Data;
import lombok.Value;
import org.apache.tools.ant.taskdefs.Ant;

import java.util.Map;
import java.util.TimerTask;

public class MuleMonitor extends TimerTask {
    @Override
    public void run() {
        Map<String, MuleTask> taskMap = AnaTask.taskMap;
        for (String taskId : taskMap.keySet()) {
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
                String urlR = "http://"+MuleConfig.remoteIp+":8899/jolokia/read/Mule." + remoteId +
                        ":type=Application,name=!%22application%20totals!%22/TotalEventsReceived";
                try {
                    String s2 = HttpRequest.sendGet(urlR, null);
                    ValueEnt valueEnt2 = JSON.parseObject(s2, ValueEnt.class);
                    value2 = valueEnt2.getValue();
                }catch (Exception e){
                }
                value += value2;
            }
            int total = value;

            RedisUtils.redisPool.jedis(jedis -> {
                jedis.hset(MuleConfig.eventTotal, taskId, String.valueOf(total));
                return null;
            });
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
