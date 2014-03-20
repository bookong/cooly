package com.funshion.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 测试 maven 插件的项目
 * 
 * <pre>
 * [user@JX_Mac target]$ java -Djava.ext.dirs=".:./lib" -jar test-replace.jar 
 * </pre>
 * 
 * @author jiangxu
 *
 */
public class Main {
	protected static final Logger logger = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) {
		InputStream is = null;
		Properties props = new Properties();
		try {
			is = Main.class.getResourceAsStream("/app.properties");
			props.load(is);
			
			System.out.println("xxx1 is " + props.getProperty("xxx1"));
			System.out.println("xxx2 is " + props.getProperty("xxx2"));
			System.out.println("xxx3 is " + props.getProperty("xxx3"));
			
		} catch (Exception e) {
			logger.error("Fail to launch Main.", e);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					logger.warn("Fail to close InputStream.");
				}
			}
		}
	}
}
