package com.redpill.tool;


import com.zzq.dolls.config.From;
import com.zzq.dolls.config.LoadConfig;

import java.io.IOException;
import java.util.List;

/**
 * @author
 * @version 1.0
 * @date 2019-7-23 14:54
 */
@From(name = "conf/MuleConfig.yml", alternateNames = "MUleConfig.yml")
public class MuleConfig {

    @From(must = true)
    public static int logLevel = 2;
    @From(must = true)
    public static String dataPath = "conf/";

    @From(alternateNames = "redisUrl")
    public static List<String> redisUrls;
    @From(must = false)
    public static String redisMaster;
    @From(must = false)
    public static int redisMode = 0;
    @From(must = false)
    public static int redisDb = 0;
    @From(alternateNames = "redisPass")
    public static String redisPass;
    //2.rabbitMq配置
    @From(name = "rabbitMqUsername")
    public static String rabbitMqUsername;
    @From(name = "rabbitMqPassword")
    public static String rabbitMqPassword;
    @From(name = "rabbitMqHostName")
    public static String rabbitMqHostName;
    @From(name = "rabbitMqPort")
    public static int rabbitMqPort;
    @From(name = "rabbitMqQueueName")
    public static String rabbitMqQueueName;
    @From(must = true)
    public static String taskInfo;

    //monitor conf
    @From(must = false)
    public static String muleMonitor = "mule.monitor.";
    @From(must = false)
    public static String eventTotal = "event.total";
    @From(must = true)
    public static String hostIp;
    @From(must = false)
    public static String remoteIp;
    @From(must = false)
    public static long monitorSch = 60 * 1000L;

    public static void main(String[] args) throws IOException {
        LoadConfig.load(MuleConfig.class);
        System.out.println("level " + MuleConfig.redisUrls.toString());
        RedisUtils.redisPool.jedis(jedis->{
            System.out.println("222222222222");
            String a = jedis.get("mule.monitor");
            System.out.println("lllllll:" + a);
            return null;
        });
    }

}
