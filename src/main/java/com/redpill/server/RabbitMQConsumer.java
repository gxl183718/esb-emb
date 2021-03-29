package com.redpill.server;

import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import com.redpill.tool.LogTool;
import com.redpill.tool.MuleConfig;
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
                return true;
            });
            rabbitConsumer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
