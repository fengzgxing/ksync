package com.cnksi.sync.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.fastjson.JSON;
import com.cnksi.sync.KSync;
import com.cnksi.sync.dao.KSyncConfig;

/**
 * 数据同步配置Servlet
 * 
 * @author joe
 *
 */
@WebServlet(urlPatterns = { "/ksync/config" }, initParams = { @WebInitParam(name = "api-url", value = "/ksync/api/v1") })
public class AdminServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	List<KSyncConfig> allConfig = null;

	File configFile = null;

	String configPath = "", servletPath = "/";

	String basePath = "", apiPath = "";

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		apiPath = config.getInitParameter("api-url");

		configPath = this.getClass().getClassLoader().getResource("/").getPath();

		// 判断classs文件夹是否存在
		File configFolder = new File(configPath);
		if (!configFolder.exists()) {
			configFolder.mkdirs();
		}

		configFile = new File(configPath, "config.json");

		// 如果配置文件不存在，则新建配置文件
		try {
			if (!configFile.exists()) {
				configFile.createNewFile();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		String json = readConfigFile(configFile);
		try {
			if (json != null && json.length() > 10) {
				allConfig = JSON.parseArray(json, KSyncConfig.class);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		if (allConfig == null) {
			allConfig = new ArrayList<KSyncConfig>();
		}

	}

	/**
	 * @param request
	 * @param response
	 * @throws ServletException
	 * @throws IOException
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		basePath = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
		servletPath = request.getServletPath();

		response.setCharacterEncoding("UTF-8");
		response.setContentType("text/html; charset=utf-8");
		String cmd = request.getParameter("cmd");

		// 修改配置文件
		if ("edit".equals(cmd)) {
			edit(request, response);
		} else if ("initScheme".equals(cmd)) {
			String appid = request.getParameter("appid");
			if (appid != null) {
				for (KSyncConfig config : allConfig) {
					if (config.getConfigName().equals(appid)) {
						KSync sync = new KSync(config);
						String msg = sync.init();
						response.getOutputStream().write(msg.getBytes("UTF-8"));
					}
				}
			}

		}
		// 删除配置文件
		else if ("delete".equals(cmd)) {
			delete(request, response);
		}
		// 跳转到配置表列表
		else if ("table".equals(cmd)) {
			table(request, response);
		}
		// 跳转到编辑配置表表单页面
		else if ("edittable".equals(cmd)) {
			edittable(request, response);
		}
		// 删除配置表，返回配置表页面页面
		else if ("deletetable".equals(cmd)) {
			deletetable(request, response);
		}
		// 跳转到schema列表页面
		else if ("intro".equalsIgnoreCase(cmd)) {
			InputStream indexJspIn = this.getClass().getClassLoader().getResourceAsStream("index.txt");
			String content = readFileContent(indexJspIn);
			content = content.replaceAll("<%=basePath%>", basePath);
			content = content.replaceAll("<%=ApiPath%>", basePath + apiPath);
			String appid = request.getParameter("appid");
			if (appid != null) {
				content = content.replaceAll("<%=AppID%>", appid);
			}
			response.getOutputStream().write(content.getBytes("UTF-8"));
		} else {
			response.getOutputStream().write(listHtml().getBytes("UTF-8"));
		}
		response.getOutputStream().flush();
	}

	/**
	 * 跳转到编辑schema表单
	 */
	private void edit(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String appid = request.getParameter("appid");

		KSyncConfig config = new KSyncConfig();
		for (KSyncConfig _config : allConfig) {
			if (_config.getConfigName().equals(appid)) {
				config = _config;
				break;
			}
		}
		response.getOutputStream().write(formHtml(config).getBytes("UTF-8"));
	}

	/**
	 * 删除配置文件
	 */
	private void delete(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String appid = request.getParameter("appid");
		if (appid != null) {

			List<KSyncConfig> _allConfig = new ArrayList<KSyncConfig>();

			for (KSyncConfig config : allConfig) {
				if (!config.getConfigName().equals(appid)) {
					_allConfig.add(config);
				}
			}

			allConfig = _allConfig;
					
			String _json = JSON.toJSONString(_allConfig);

			writeFile(_json);
		}

		response.sendRedirect(getServletContext().getContextPath() + servletPath);
	}

	/**
	 * 跳转到配置表列表
	 */
	private void table(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String appid = request.getParameter("appid");
		KSyncConfig config = new KSyncConfig();
		for (KSyncConfig _config : allConfig) {
			if (_config.getConfigName().equals(appid)) {
				config = _config;
				break;
			}
		}
		response.getOutputStream().write(tableHtml(config).getBytes("UTF-8"));
	}

	/**
	 * 跳转到编辑配置表表单页面
	 */
	private void edittable(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String appid = request.getParameter("appid");
		String tablename = request.getParameter("tablename");
		KSyncConfig config = new KSyncConfig();
		String columns = "", conditions = "";

		// 先匹配config
		for (KSyncConfig _config : allConfig) {
			if (_config.getConfigName().equals(appid)) {
				config = _config;
				break;
			}
		}
		// 再匹配syncTables
		if (tablename != null) {
			for (Entry<String, String> entry : config.getSyncTables().entrySet()) {
				if (entry.getKey().equals(tablename)) {
					columns = entry.getValue();
					conditions = config.getSyncCondition().get(tablename);
					break;
				}
			}
		}

		response.getOutputStream().write(edittableHtml(config.getConfigName(), tablename, columns, conditions).getBytes("UTF-8"));
	}

	/**
	 * 删除配置表，返回配置表页面页面
	 */
	private void deletetable(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String appid = request.getParameter("appid");
		String tablename = request.getParameter("tablename");
		if (appid != null && tablename != null) {

			for (KSyncConfig config : allConfig) {
				if (config.getConfigName().equals(appid)) {
					// 删除syncTables
					Map<String, String> syncTables = config.getSyncTables();
					for (Entry<String, String> table : syncTables.entrySet()) {
						if (table.getKey().equals(tablename)) {
							syncTables.remove(table.getKey());
							break;
						}
					}
					// 删除syncCondition
					Map<String, String> syncCondition = config.getSyncCondition();
					for (Entry<String, String> condition : syncCondition.entrySet()) {
						if (condition.getKey().equals(tablename)) {
							syncCondition.remove(condition.getKey());
							break;
						}
					}
				}
			}

			String _json = JSON.toJSONString(allConfig);

			writeFile(_json);
		}
		response.sendRedirect(getServletContext().getContextPath() + servletPath + "?cmd=table&appid=" + appid);
	}

	/**
	 * 保存配置
	 * 
	 * @throws IOException
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

		response.setCharacterEncoding("UTF-8");
		response.setContentType("text/html; charset=utf-8");

		String cmd = request.getParameter("cmd");

		// 保存配置表
		if ("savetable".equals(cmd))
			savetable(request, response);

		// 保存配置
		else
			save(request, response);
	}

	/**
	 * 保存配置表
	 * 
	 * @throws IOException
	 */
	private void savetable(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String appid = request.getParameter("appid");
		String tablename = request.getParameter("tablename");
		String columns = request.getParameter("columns");
		String conditions = request.getParameter("conditions");

		if (tablename == null || columns == null || conditions == null) {
			response.getOutputStream().write("table,columns,conditions 不能为空".getBytes("UTF-8"));
		} else {
			// 先匹配config
			KSyncConfig config = null;
			for (KSyncConfig _config : allConfig) {
				if (_config.getConfigName().equals(appid)) {
					config = _config;
					break;
				}
			}

			// 更新syncTables
			Map<String, String> syncTables = config.getSyncTables();
			Map<String, String> syncCondition = config.getSyncCondition();
			syncTables.put(tablename, columns);
			syncCondition.put(tablename, conditions);
			config.setSyncTables(syncTables);
			config.setSyncCondition(syncCondition);

			String json = JSON.toJSONString(allConfig);

			writeFile(json);

			response.sendRedirect(getServletContext().getContextPath() + servletPath + "?cmd=table&appid=" + appid);
		}

	}

	/**
	 * 保存配置
	 */
	private void save(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String appid = request.getParameter("appid");
		String url = request.getParameter("url");
		String username = request.getParameter("username");
		String password = request.getParameter("password");
		String driver = request.getParameter("driver");
		String pageSize = request.getParameter("pageSize");
		String modfield = request.getParameter("modfield");
		String enabledField = request.getParameter("enabledField");
		String folder = request.getParameter("folder");

		System.out.println(String.format(" appid : %s, url:%s, username:%s,password:%s,driver:%s", appid, url, username, password, driver));

		if (appid == null || url == null || username == null || password == null || driver == null) {

			response.getOutputStream().write("appid,url,username,password 不能为空".getBytes("UTF-8"));
		} else {

			KSyncConfig config = null;

			boolean exist = false;
			for (KSyncConfig _config : allConfig) {
				if (_config.getConfigName().equals(appid)) {
					config = _config;
					exist = true;
					break;
				}
			}
			if (config == null) {
				config = new KSyncConfig();
			}

			config.setConfigName(appid);
			config.setUrl(url);
			config.setUsername(username);
			config.setPassword(password);
			config.setDriver(driver);

			if (enabledField != null)
				config.setEnabledFiled(enabledField);
			if (modfield != null)
				config.setLstModifyField(modfield);

			folder = folder == null ? "" : folder;
			config.setFolder(folder);
			pageSize = pageSize == null ? "500" : pageSize;
			config.setPageSize(Integer.parseInt(pageSize));

			if (!exist) {
				allConfig.add(config);
			}
			String json = JSON.toJSONString(allConfig);

			writeFile(json);

			response.sendRedirect(getServletContext().getContextPath() + servletPath);
		}
	}

	/**
	 * 配置表列表页面
	 * 
	 * @param config
	 * @return
	 */
	private String tableHtml(KSyncConfig config) {
		InputStream cssFileIn = this.getClass().getClassLoader().getResourceAsStream("table.css");
		String contextPath = getServletContext().getContextPath();
		StringBuffer buffer = new StringBuffer();
		buffer.append(String.format("<html><header><style type=text/css>%s</style></header><body>", readFileContent(cssFileIn)));

		buffer.append(String.format("<p>  <a href='" + contextPath + servletPath + "?cmd=edittable&appid=%s' >新增配置表</a><a style='margin-left:20px;' href='" + contextPath + servletPath + "' >返回Schema列表</a></p>", config.getConfigName()));

		buffer.append("<table border=1 class=gridtable>");
		buffer.append("<tr><th>序号</th><th>AppID</th><th>TableName</th><th>Columns</th><th>Conditions</th><th>操作</th></tr>");

		String template = "<tr><td>%d</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td><a href='" + contextPath + servletPath + "?cmd=edittable&appid=%s&tablename=%s'>修改</a> | <a href='" + contextPath + servletPath + "?cmd=deletetable&appid=%s&tablename=%s'>删除</a></td></tr>";

		int i = 0;
		for (Entry<String, String> entry : config.getSyncTables().entrySet()) {
			i++;
			String tablename = entry.getKey();
			String columns = entry.getValue();
			String conditions = config.getSyncCondition().get(tablename);
			buffer.append(String.format(template, i, config.getConfigName(), tablename, columns, conditions, config.getConfigName(), tablename, config.getConfigName(), tablename));
		}

		buffer.append("</table>");
		buffer.append("</body></html>");
		return buffer.toString();
	}

	/**
	 * 读取css文件
	 * 
	 * @return
	 */
	private String readFileContent(InputStream in) {

		String line = "", content = "";

		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(in));

			while ((line = br.readLine()) != null) {
				content += line;
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {

			if (br != null) {
				try {
					br.close();
				} catch (IOException e1) {
				}
			}
		}
		return content;
	}

	/**
	 * 新增配置表页面
	 * 
	 * @param config
	 * @return
	 */
	private String edittableHtml(String appid, String tablename, String columns, String conditions) {
		InputStream cssFileIn = this.getClass().getClassLoader().getResourceAsStream("table.css");
		String contextPath = getServletContext().getContextPath();
		StringBuffer buffer = new StringBuffer();
		buffer.append(String.format("<html><header></script><style type=text/css>%s</style></header><body>", readFileContent(cssFileIn)));
		buffer.append("<form action='" + contextPath + servletPath + "?cmd=savetable' method='post'>");

		buffer.append(String.format("<div>APPID:</div><input type='text' disabled='disabled' value='%s' /><input type='hidden' name=appid value='%s' /> </br>", appid == null ? "" : appid, appid == null ? "" : appid));
		buffer.append(String.format("<div>Table:</div><input type='text' name=tablename value='%s' /> </br>", tablename == null ? "" : tablename));
		buffer.append(String.format("<div>Columns:</div><input type='text' name=columns value='%s' /> </br>", columns == null ? "" : columns));
		buffer.append(String.format("<div>Conditions:</div><input type='text' name=conditions value=\"%s\" /> </br>", conditions == null ? "" : conditions));

		buffer.append("</br><input type='submit' value='提交'/>");
		buffer.append("</form>");
		buffer.append("</body></html>");
		return buffer.toString();
	}

	/**
	 * 获取列表HTML
	 * 
	 * @return
	 */
	private String listHtml() {
		InputStream cssFileIn = this.getClass().getClassLoader().getResourceAsStream("table.css");

		String contextPath = getServletContext().getContextPath();
		StringBuffer buffer = new StringBuffer();
		buffer.append(String.format("<html><header><style type=text/css>%s</style></header><body>", readFileContent(cssFileIn)));

		buffer.append("<p>  <a href='" + contextPath + servletPath + "?cmd=edit' >新增同步Schema</a> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;  <a target='_blank' href='" + contextPath + servletPath + "?cmd=intro' >同步接口说明</a> </p>");

		buffer.append("<table border=1 class=gridtable>");
		buffer.append("<tr><th>序号</th><th>AppID</th><th style='width:20%;'>URL</th><th>UserName</th><th>Password</th><th>Driver</th><th>PageSize</th><th>ModifyField</th><th>EnabledField</th><th>同步Folder</th><th>操作</th></tr>");

		String template = "<tr><td>%d</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td> <a href='" + contextPath + apiPath + "?cmd=initScheme&appid=%s' target='_blank'>初始化字段</a> | <a href='" + contextPath + servletPath + "?cmd=table&appid=%s'  target='_blank'>配置表</a> | <a href='" + contextPath + servletPath + "?cmd=edit&appid=%s'  target='_blank'>修改</a> | <a href='" + contextPath + servletPath + "?cmd=delete&appid=%s' >删除</a> | <a href='" + contextPath + servletPath + "?cmd=intro&appid=%s'>查看api</a> </td></tr>";

		for (KSyncConfig config : allConfig) {
			buffer.append(String.format(template, 1, config.getConfigName(), config.getUrl(), config.getUsername(), config.getPassword(), config.getDriver(), config.getPageSize(), config.getLstModifyField(), config.getEnabledFiled(), config.getFolder(), config.getConfigName(), config.getConfigName(), config.getConfigName(), config.getConfigName(), config.getConfigName()));
		}
		buffer.append("</table>");
		buffer.append("</body></html>");
		return buffer.toString();
	}

	/**
	 * schema表单页面内容
	 * 
	 * @param config
	 * @return
	 */
	private String formHtml(KSyncConfig config) {

		InputStream cssFileIn = this.getClass().getClassLoader().getResourceAsStream("table.css");

		String contextPath = getServletContext().getContextPath();
		StringBuffer buffer = new StringBuffer();
		buffer.append(String.format("<html><header><style type=text/css>%s</style></header><body>", readFileContent(cssFileIn)));
		buffer.append("<form action='" + contextPath + servletPath + "?cmd=save' method='post'>");

		buffer.append(String.format("<div>APPID:</div><input type='text' name=appid value='%s' /> </br>", config.getConfigName()));
		buffer.append(String.format("<div>Url:</div><input type='text' name=url value='%s' /> </br>", config.getUrl()));
		buffer.append(String.format("<div>Username:</div><input type='text' name=username value='%s' /> </br>", config.getUsername()));
		buffer.append(String.format("<div>Paassword:</div><input type='text' name=password value='%s' /> </br>", config.getPassword()));
		buffer.append(String.format("<div>Driver:</div><input type='text' name=driver value='%s' /> </br>", config.getDriver()));
		buffer.append(String.format("<div>PageSize:</div><input type='text' name=pageSize value='%s' /> </br>", config.getPageSize()));
		buffer.append(String.format("<div>ModifField:</div><input type='text' name=modfield value='%s' /> </br>", config.getLstModifyField()));
		buffer.append(String.format("<div>EnabledField:</div><input type='text' name=enabledField value='%s' /> </br>", config.getEnabledFiled()));
		buffer.append(String.format("<div>SYNC Folder:</div><input type='text' name=folder value='%s' /> </br>", config.getFolder()));

		buffer.append("</br><input type='submit' value='提交'/>");
		buffer.append("</form>");
		buffer.append("</body></html>");
		return buffer.toString();
	}

	@SuppressWarnings("all")
	private String readConfigFile(File file) {
		String line = "", content = "";

		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(file));

			while ((line = br.readLine()) != null) {
				content += line;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e1) {
				}
			}
		}
		return content;
	}

	private void writeFile(String content) {
		try {
			// 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
			FileWriter writer = new FileWriter(configFile, false);
			writer.write(content);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
