package com.redpill;

import com.redpill.api.MuleLoad;
import com.redpill.api.MuleTask;
import com.redpill.tool.MuleConfig;
import com.zzq.dolls.config.LoadConfig;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MuleCoreServer {
    private static final String cf = "conf/cf";
    private static Map<String, MuleTask> map = new ConcurrentHashMap();
    public static void main(String[] args) {
        try {
            LoadConfig.load(MuleConfig.class);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        File file = new File(cf);
        if (file.isDirectory()) {
            for (String s : file.list()) {
                MuleLoad muleTask = new MuleLoad(s);
                muleTask.initTask();
                if (muleTask.executeTask()){
                    map.put(muleTask.getTaskId(), muleTask);
                }
            }
        }
    }
}
