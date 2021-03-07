package com.redpill;

import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.config.ConfigurationException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.config.spring.SpringXmlConfigurationBuilder;
import org.mule.context.DefaultMuleContextFactory;

import java.io.File;

public class MuleUtil {
    static final DefaultMuleContextFactory muleContextFactory = new DefaultMuleContextFactory();

    public static MuleContext exeTask(String[] muleTask){
        SpringXmlConfigurationBuilder configBuilder = null;
        try {
            configBuilder = new SpringXmlConfigurationBuilder(muleTask);
            MuleContext muleContext = muleContextFactory.createMuleContext(configBuilder);
            muleContext.start();
            return muleContext;
        } catch (ConfigurationException e) {
            e.printStackTrace();
        } catch (InitialisationException e) {
            e.printStackTrace();
        } catch (MuleException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void loadAllTasks(String path){
        if (!path.endsWith("/"))
            path = "/" + path;
        File file = new File(path);
        String[] ss = null;
        String[] fileList = null;
        if (file.isDirectory()) {
            fileList = file.list();
            ss = new String[(int)fileList.length];
        }
        for (int i = 0; i < file.list().length; i++) {
            ss[i] = path + fileList[i];
            System.out.println(ss[i]);
        }
        exeTask(ss);
    }

    public static void delTask(String taskId){

    }

    public static void close(MuleContext muleContext){
        try {
            muleContext.stop();
        } catch (MuleException e) {
            e.printStackTrace();
        }
        muleContext.dispose();
    }
}
