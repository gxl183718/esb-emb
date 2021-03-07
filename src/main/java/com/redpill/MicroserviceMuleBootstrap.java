package com.redpill;

import static java.lang.ClassLoader.getSystemResourceAsStream;
import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.client.MuleClient;
import org.mule.config.spring.SpringXmlConfigurationBuilder;
import org.mule.context.DefaultMuleContextFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.logging.log4j.core.LoggerContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Microservice Mule bootstrap.
 */
@Component
public class MicroserviceMuleBootstrap implements ApplicationListener<ApplicationContextEvent> {


	static final Logger logger = LoggerFactory.getLogger(MicroserviceMuleBootstrap.class);
	static final String[] propertiesFiles = { "mule-app.properties", "mule-deploy.properties" };

	DefaultMuleContextFactory muleContextFactory;
	SpringXmlConfigurationBuilder configBuilder;
	MuleContext muleContext;
	MuleClient client;
	MuleMessage message;
	MuleMessage response;

	@Override
	public void onApplicationEvent(ApplicationContextEvent event) {
		System.out.println("222222222222222222222");
		if (event instanceof ContextRefreshedEvent && event.getApplicationContext().getParent() == null) {
			startMuleContext(event.getApplicationContext());
		} else if (event instanceof ContextClosedEvent) {
			stopMuleContext();
		}
	}

	public void startMuleContext(ApplicationContext parent) {
		System.out.println("11111111111111");

        LoggerContext contextLog4j2 = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);

        File file = getFile("log4j2.xml");
        // this will force a reconfiguration
        contextLog4j2.setConfigLocation(file.toURI());

		muleContextFactory = new DefaultMuleContextFactory();

		try {
			Properties props = getStartUpProperties();
			String str[] = props.getProperty("config.resources").split(",");

			configBuilder = new SpringXmlConfigurationBuilder(str);
			StaticApplicationContext staticApplicationContext = new StaticApplicationContext(parent);
			staticApplicationContext.refresh();
			configBuilder.setParentContext(staticApplicationContext);
			muleContext = muleContextFactory.createMuleContext(configBuilder, props);
			logger.info("Starting Mule Context.......");
			muleContext.start();

			Thread.sleep(5000);
			System.out.println("stopping mule server");
			muleContext.stop();

		} catch (MuleException me) {
			logger.error("Error running flow: ", me);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}


	Properties getStartUpProperties() {
		System.out.println("333333333333333333333");
		Properties props = new Properties();
		for (String propertiesFile : propertiesFiles) {
			try {
				props.load(getSystemResourceAsStream(propertiesFile));
			} catch (IOException | NullPointerException ex) {
				logger.error(format("Could not find properties file \"%s\" on classpath.", propertiesFile));
			}
		}
		return props;
	}


	void stopMuleContext() {
		try {
			muleContext.stop();
		} catch (MuleException me) {
			logger.warn("Error stopping mule: " + me.getMessage());
		}
		muleContext.dispose();
	}

	private  File getFile(String fileName) {


		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource(fileName).getFile());

		return file;

	}

	public MuleContext getMuleContext() {
		return muleContext;
	}

}