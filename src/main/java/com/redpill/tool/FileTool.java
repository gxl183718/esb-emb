package com.redpill.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class FileTool {
    public static boolean copyFile(String source, String destination){
        FileChannel input = null;
        FileChannel output = null;

        try {
            input = new FileInputStream(new File(source)).getChannel();
            output = new FileOutputStream(new File(destination)).getChannel();
            output.transferFrom(input, 0, input.size());
        } catch (Exception e) {
            LogTool.logInfo(3, " copy xml file backup error occur while copy");
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    public static boolean rmFile(String fn){
        File file = new File(fn);
        if (file.exists()){
            file.delete();
            return true;
        }
        return false;
    }
}
