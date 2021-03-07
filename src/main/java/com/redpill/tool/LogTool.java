package com.redpill.tool;

import org.mule.api.MuleContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogTool {
    public static String getTime(){
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()) + " ----> ";
    }

    public static void logInfo(int level, String info){
        if (level <= MuleConfig.logLevel){
            System.out.println(getTime() + info);
        }
    }
    public static void logInfo(int level, long... args){

    }
}
