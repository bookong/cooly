package com.github.bookong.cooly;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

import com.github.bookong.cooly.sdk.xml.cr.Prop;
import com.github.bookong.cooly.sdk.xml.cr.Props;

/**
 * 替换文件内容
 * 
 * <pre>
 * mvn clean package deploy
 * mvn net.bookong.maven.plugin:cooly-plugin:0.2.0:replace
 * </pre>
 * 
 * 使用 process-test-classes 这个 phase 的原因是为了在跳过单元测试时 （mvn install -Dmaven.test.skip=true）也可以执行
 * 
 * @author jiangxu
 */
@Mojo(name="replace", defaultPhase=LifecyclePhase.PROCESS_TEST_CLASSES)
public class ConfigReplace extends AbstractMojo {

	/** 要替换的扩展名 */
	@Parameter(property="extName", defaultValue="ci_tmpl" )
	private String extName;
	
	/** 记录配置信息的文件名，与工程根路径的相对位置 */
	@Parameter(property="propsFilename", defaultValue="ci_props.xml" )
	private String propsFilename;
	
	/** 用于匹配变量的前缀，如 “${VAR}” 的 “${” */
	@Parameter(property="propPrefix", defaultValue="${" )
	private String propPrefix;
	
	/** 用于匹配变量的后缀，如 “${VAR}” 的 “}”  */
	@Parameter(property="propSuffix", defaultValue="}" )
	private String propSuffix;
	
	
	/** 对那些目录下（与 target 的相对路径）的内容进行查找替换操作。可填写多个，用“,”分割 */
	@Parameter(property="directoryToOperate", defaultValue="classes" )
	private String directoryToOperate;
	
	/** 正则表达式中需要转义的关键字 */
	private static final char[] REG_EXP_TRIGGER_CHARS = {'^', '$', '(', ')', '[', ']', '{', '}', '.', '?', '+', '*', '|', '\\'};
	
	/** 项目根目录 */
	@Parameter(property="basedir")
	private String basedir;
	
	/** 构建目录，缺省为 target */
	@Parameter(property="project.build.directory")
	private String projectBuildDirectory;
	
	/** 要替换的属性 */
	private Map<String, String> props = new HashMap<String, String>();
	
	/** 一个正则表达式, 用于检查是否有 ci_props.xml 中未定义的属性 */
	private Pattern verifyReg;

	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			// 以非贪婪算法在最终替换后的文件中匹配类似 ${VAR} 类型的内容
			String regex = StringUtils.escape(propPrefix, REG_EXP_TRIGGER_CHARS, '\\') + ".*?"
					+ StringUtils.escape(propSuffix, REG_EXP_TRIGGER_CHARS, '\\');
			getLog().info("regex: " + regex);
			verifyReg = Pattern.compile(regex);
			
			extName = "." + extName;
			loadPropsFromXml(basedir + File.separatorChar +  propsFilename);
			
			for (String dir : directoryToOperate.split(",")) {
				if (!StringUtils.isBlank(dir)) {
					dir = projectBuildDirectory + File.separatorChar + dir;
					getLog().info("Scanning directory : " + dir);
					File file = new File(dir);
					File[] files = file.listFiles();
					if(files != null){
						for (File item : files) {
							doReplace(item);
						}
					}
				}
			}
			
		} catch (Exception e) {
			getLog().error(e);
			throw new MojoExecutionException("Fail to execute cooly:replace, Message: " + e.getMessage(), e);
		}
	}
	
	/** 替换文件中的内容 */
	private void doReplace(File file) throws Exception {
		if (file.isFile()) {
			String filepath = file.getAbsolutePath();
			if (filepath.endsWith(extName)) {
				String confFilepath = filepath.substring(0, filepath.lastIndexOf('.'));
				String content = FileUtils.fileRead(filepath, "UTF-8");

				getLog().info("Replace file: " + confFilepath);
				for (String key : props.keySet()) {
					content = StringUtils.replace(content, (propPrefix + key + propSuffix), props.get(key));
				}
				
				// 替换文件内容后，检查是不是有内容没有在 ci_props.xml 中定义
				Matcher m = verifyReg.matcher(content);
				StringBuilder buff = new StringBuilder();
				boolean found = m.find();
				if (found) {
					buff.append("Missing property ");
				}
				
				while(found){
					buff.append("\"").append(m.group()).append("\" ");
					found = m.find();
				}
				
				if (buff.length() > 0) {
					buff.append(" in ").append(propsFilename).append(", when handle ").append(confFilepath);
					throw new Exception(buff.toString());
				}
				
				FileUtils.fileWrite(confFilepath, content);
				
				new File(filepath).delete();
			}

		} else if (file.isDirectory()) {
			File[] files = file.listFiles();
			for (File item : files) {
				doReplace(item);
			}
		}
	}
	
	/** 读取配置内容 */
	private void loadPropsFromXml(String filepath) throws Exception {
		getLog().info("Load variables from :" + filepath);
		JAXBContext cxt = JAXBContext.newInstance("com.github.bookong.cooly.sdk.xml.cr");
		Unmarshaller unm = cxt.createUnmarshaller();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(filepath);
			Props root = (Props) unm.unmarshal(fis);
			for (Prop item : root.getProp()) {
				String value = item.getValue().trim();
				props.put(item.getName(), value);
				getLog().info("    " + item.getName() + " -> \"" + value + "\"");
			}
		} catch (Exception e) {
			throw e;
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (Exception e2) {
					getLog().warn("Fail to close FileInputStream.");
				}
			}
		}
	}
	
	/*
	public static void main(String[] args) {
		try {
			// 产生 xml 解析类
			com.sun.tools.xjc.XJCFacade.main(new String[]{
					"-p", "com.github.bookong.cooly.sdk.xml.cr",
					"-d", "/Volumes/MacData/data/Work/git/cooly/src/main/java/", 
					"/Volumes/MacData/data/Work/git/cooly/src/main/resources/schema/cr.xsd"});
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	//*/
}
