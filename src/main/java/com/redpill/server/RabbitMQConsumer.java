package com.redpill.server;

import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import com.redpill.tool.LogTool;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import com.zzq.dolls.mq.rabbit.RabbitConsumer;

import java.util.concurrent.atomic.AtomicBoolean;

public class RabbitMQConsumer implements Runnable {
    public static AtomicBoolean isWaiting = new AtomicBoolean(false);


    @Override
    public void run() {

        LogTool.logInfo(1, "consumer is starting.");
        try {
            RabbitConsumer rabbitConsumer = RabbitConsumer.builder()
                    .host(MuleConfig.rabbitMqHostName)
                    .port(MuleConfig.rabbitMqPort)
                    .user(MuleConfig.rabbitMqUsername)
                    .password(MuleConfig.rabbitMqPassword)
                    .topic(MuleConfig.rabbitMqQueueName)
                    .build();

            rabbitConsumer.message(body -> {
                String task = new String(body);
                //写入备用节点
                for (String remoteIp : MuleConfig.remoteIp) {
                    String taskInfo = RedisUtils.redisPool.jedis(jedis -> {
                        jedis.lpush("back-" + remoteIp, task);
                        LogTool.logInfo(2, "Have back node ( " + remoteIp + ") , push task to remote.");
                        return null;
                    });
                }

                TaskEntity taskEntity = null;
                try{
                    taskEntity = JSON.parseObject(task, TaskEntity.class);
                }catch (Exception e){
                    e.printStackTrace();
                    return true;
                }
                LogTool.logInfo(2, "recv task : " + task);
                try {
                    AnaTask.taskHandle(taskEntity);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                LogTool.logInfo(2, "task ok : " + task);
                return true;
            });
            rabbitConsumer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
