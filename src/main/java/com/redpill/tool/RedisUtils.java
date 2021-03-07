package com.redpill.tool;
import com.zzq.dolls.config.LoadConfig;
import com.zzq.dolls.redis.RedisPool;
import com.zzq.dolls.redis.mini.MiniPool;

import java.io.IOException;


public class RedisUtils {

    public static RedisPool cluster;
    public final static MiniPool redisPool;
    static {
//        redisPool = RedisPool.builder().urls(TSMConf.redisUrls)
//                .masterName(TSMConf.redisMaster)
//                .password(TSMConf.redisPass)
//                .redisMode(TSMConf.redisMode)
//                .db(TSMConf.redisDb).build();
        cluster = RedisPool.builder()
                .urls(MuleConfig.redisUrls)
                .redisMode(MuleConfig.redisMode)
                .password(MuleConfig.redisPass)
                .masterName(MuleConfig.redisMaster)
                .db(MuleConfig.redisDb)
                .timeout(1000)
                .build();
        redisPool = cluster.mini();
    }

}
