package com.redpill.api;

import org.mule.api.MuleContext;
import org.mule.config.spring.SpringXmlConfigurationBuilder;
import org.mule.context.DefaultMuleContextFactory;

/**
 * @author labig3
 */
public interface MuleTask {

    public static final DefaultMuleContextFactory defaultMuleContextFactory = new DefaultMuleContextFactory();

    /**
     * init mule task
     * @return
     * @throws Exception
     */
    public abstract void initTask() throws Exception;
    /**
     * start this task
     * @return
     * @throws Exception
     */
    public abstract boolean executeTask() throws Exception;
    /**
     * close this task
     * @return
     * @throws Exception
     */
    public abstract void closeTask() throws Exception;
    /**
     * when a task closed, remove the xml file and so on.
     * @return
     * @throws Exception
     */
    public abstract boolean removeTask() throws Exception;
    /**
     *  get taskId form a mule task
     * @return taskId
     * @throws Exception
     */
    public abstract String getTaskId() throws Exception;
    /**
     * get server id from a mule task, server id is composed of 'TYPE' and proxy server port.
     * @return serverId
     * @throws Exception
     */
    public abstract String getServerId() throws Exception;
    /**
     * get server id from a mule task, server id is composed of 'TYPE' and proxy server port.
     * @return proxy server port
     * @throws Exception
     */
    public abstract Integer getPort() throws Exception;
    /**
     * return a mule task server type
     * @return server type
     * @throws Exception
     */
    public abstract String getType() throws Exception;

}
