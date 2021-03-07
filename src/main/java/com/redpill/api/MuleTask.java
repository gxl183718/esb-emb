package com.redpill.api;

import org.mule.api.MuleContext;
import org.mule.config.spring.SpringXmlConfigurationBuilder;
import org.mule.context.DefaultMuleContextFactory;

public interface MuleTask {
    public static final DefaultMuleContextFactory defaultMuleContextFactory = new DefaultMuleContextFactory();
    public abstract void initTask() throws Exception;
    public abstract boolean executeTask();
    public abstract void closeTask();
    public abstract void removeTask();
}
