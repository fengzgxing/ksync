package com.cnksi.sync.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.cnksi.sync.KSync;
import com.cnksi.sync.dao.KSyncConfig;
import com.cnksi.util.JsonFormatTool;

/**
 * 数据同步接口API
 * 
 * <pre>
 * 数据同步原理：
 * 1、服务器端数据对比通过对比 每条记录的最后修改时间来达到判读数据是否更新
 * 2、系统自动生产数据库表结构
 * 3、同步数据库表时,单表数据分页同步，分页大小可配置
 * 4、文件下载、上传(差异同步)
 * </pre>
 */
@WebServlet(asyncSupported = true, urlPatterns = { "/ksync/api/v1" })
@MultipartConfig
public class ApiV1Servlet extends HttpServlet {

	private SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
	private SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private static final long serialVersionUID = 1L;

	private String appid = null, cmd = null;

	private KSyncConfig config = null;

	private KSync sync = null;

	private HttpServletRequest request;
	private HttpServletResponse response;

	private Map<String, Object> resultMap = null;

	private Logger logger = null;

	public ApiV1Servlet() {
		logger = Logger.getLogger(ApiV1Servlet.class);
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		this.request = request;
		this.response = response;
		this.resultMap = new LinkedHashMap<String, Object>();
		request.setCharacterEncoding("utf-8");

		appid = request.getParameter("appid");
		cmd = request.getParameter("cmd");

		if (appid == null || cmd == null) {
			resultMap.put("status", "500");
			resultMap.put("data", "Appid 或 cmd参数不能为空");
			print();
			logger.log(Level.ALL, "Appid 或 cmd参数不能为空");

		} else {

			initConfig();

			config = KSyncConfig.getInstance().getConfig(appid);

			if (config != null) {
				sync = new KSync(config);

				if ("struct".equalsIgnoreCase(cmd)) {

					this.struct();

				} else if ("down_data".equalsIgnoreCase(cmd)) {

					this.downData();

				} else if ("compare".equalsIgnoreCase(cmd)) {

					this.compare();

				} else if ("down_file".equalsIgnoreCase(cmd)) {

					this.downFile();

				} else if ("upload_data".equalsIgnoreCase(cmd)) {

					this.uploadData();

				} else if ("upload_file".equalsIgnoreCase(cmd)) {

					this.uploadFile();

				} else if ("initScheme".equalsIgnoreCase(cmd)) {
					String msg = sync.init();
					response.setCharacterEncoding("UTF-8");
					response.setContentType("text/html; charset=utf-8");
					response.getOutputStream().write(msg.getBytes("UTF-8"));
				}
			} else {
				resultMap.put("status", "300");
				resultMap.put("msg", "未找到该Appid的数据配置");
				print();
			}
		}

	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	/**
	 * 获取表结构
	 */
	private void struct() {
		try {
			Object structData = sync.getStruct();
			resultMap.put("status", "200");
			resultMap.put("type", "struct");
			resultMap.put("data", structData);
		} catch (Exception e) {
			e.printStackTrace();
			resultMap.put("data", e.getMessage());
		}
		print();
	}

	/**
	 * 下载数据
	 */
	private void downData() {
		String tableName = request.getParameter("tbl");
		String pageNum = request.getParameter("pageNum");
		String lst = request.getParameter("lst");
		logger.log(Level.INFO, String.format("downData , tbl = %s , pageNum = %s, lst=%s", tableName, pageNum, lst));
		try {
			int page = 1;
			if (pageNum == null || pageNum.isEmpty()) {
				page = 1;
			} else {
				page = Integer.parseInt(pageNum);
				if (page < 1) {
					page = 1;
				}
			}

			String where = null;
			List<Map<String, Object>> data = null;
			if (lst != null && lst.length() == 14) {
				lst = sdf2.format(sdf.parse(lst));
				where = config.getLstModifyField() + " > ? ";
				data = sync.getTableData(tableName, where, page, lst);
			} else {
				data = sync.getTableData(tableName, where, page);
			}

			resultMap.put("status", "200");
			resultMap.put("type", "down_data");
			resultMap.put("table", tableName);
			resultMap.put("data", data);
			if (data != null) {
				resultMap.put("hasData", data.size() == config.getPageSize());
				resultMap.put("dataSize", data.size());
			} else {
				resultMap.put("hasData", false);
				resultMap.put("dataSize", 0);
			}
		} catch (Exception e) {
			e.printStackTrace();
			resultMap.put("status", "500");
			resultMap.put("data", e.getMessage());
		}

		print();
	}

	/**
	 * 上传数据库
	 */
	private void uploadData() {

		try {
			String uploadDataStr = streamToString(request.getInputStream(), "utf-8");
			logger.log(Level.INFO, "获取要上传解析的数据：" + JsonFormatTool.formatJson(uploadDataStr));

			sync.uploadData(uploadDataStr);
			resultMap.put("status", "200");

			resultMap.put("data", "数据上传成功");
		} catch (Exception e) {
			e.printStackTrace();
			resultMap.put("status", "500");
			resultMap.put("data", e.getMessage());
		}
		print();
	}

	/**
	 * 对比文件
	 */
	private void compare() {
		// 对比文件
		String folder = request.getParameter("folder");
		String files = request.getParameter("files");
		String direction = request.getParameter("direction"); // down or up
		try {
			if (folder != null && !folder.isEmpty()) {
				File file = new File(config.getFolder() + File.separator + folder);
				if (!file.exists()) {
					file.mkdirs();
				}
			}
			if (direction == null) {
				direction = "down";
			}
			Object data = sync.getDownloadFile(folder, files, direction);
			resultMap.put("status", "200");
			resultMap.put("data", data);
		} catch (Exception e) {
			resultMap.put("status", "500");
			resultMap.put("data", e.getMessage());
			e.printStackTrace();
		}

		print();
	}

	/**
	 * 下载文件
	 */
	private void downFile() {
		try {

			request.setCharacterEncoding("UTF-8");

			String folder = request.getParameter("folder");

			String filename = request.getParameter("filename");

			System.out.println(folder + "/" + filename);

			response.setContentType("text/plain");
			response.setHeader("Location", filename);
			response.setHeader("Content-Disposition", "attachment; filename=" + filename);
			OutputStream outputStream = response.getOutputStream();
			String filePath = config.getFolder() + File.separator + folder + File.separator + filename;

			InputStream inputStream = new FileInputStream(filePath);
			byte[] buffer = new byte[10240];
			int i = -1;
			while ((i = inputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, i);
			}
			inputStream.close();
			outputStream.flush();
			outputStream.close();
		} catch (FileNotFoundException e1) {
			resultMap.put("status", "500");
			resultMap.put("data", e1.getMessage());
			print();
		} catch (Exception ex) {
			resultMap.put("status", "500");
			resultMap.put("data", ex.getMessage());
			print();
		}
	}

	/**
	 * 上传文件
	 */
	private void uploadFile() {
		String folder = request.getParameter("folder");
		try {
			// 获取上传的文件集合
			Collection<Part> parts = request.getParts();

			if (!parts.isEmpty()) {
				File file = new File(config.getFolder() + File.separator + folder);
				if (!file.exists()) {
					file.mkdirs();
				}
			}

			// 一次性上传多个文件
			for (Part part : parts) {// 循环处理上传的文件
				// 获取请求头，请求头的格式：form-data; name="file"; filename="snmp4j--api.zip"
				String header = part.getHeader("content-disposition");
				// 获取文件名
				String fileName = getFileName(header);
				// 把文件写到指定路径
				String fileBasePath = config.getFolder() + File.separator + folder + File.separator + fileName;
				// part.write(fileBasePath);

				FileOutputStream fos = null;
				InputStream in = null;
				try {
					in = part.getInputStream();
					fos = new FileOutputStream(new File(fileBasePath));
					byte[] buf = new byte[102400];
					int len = 0;
					// 循环将输入流读入到缓冲区当中，(len=in.read(buffer))>0就表示in里面还有数据
					while ((len = in.read(buf)) > 0) {
						// 使用FileOutputStream输出流将缓冲区的数据写入到指定的目录(savePath + "\\" + filename)当中
						fos.write(buf, 0, len);
					}
					fos.flush();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					try {
						fos.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			resultMap.put("status", "200");
			resultMap.put("data", "上传成功");
		} catch (Exception ex) {
			resultMap.put("status", "500");
			resultMap.put("data", ex.getMessage());
		}

		print();
	}

	private void print() {
		try {
			response.setCharacterEncoding("UTF-8");
			response.setContentType("application/json; charset=utf-8");

			String isFormat = request.getParameter("f");
			if ("yes".equalsIgnoreCase(isFormat)) {
				response.getWriter().append(JsonFormatTool.formatJson(JSON.toJSONString(resultMap, SerializerFeature.WriteDateUseDateFormat, SerializerFeature.UseSingleQuotes, SerializerFeature.IgnoreNonFieldGetter)));
			} else {
				PrintWriter pw = response.getWriter();// ServletGzipUtil.createGzipPw(request, response);
				pw.append(JSON.toJSONString(resultMap, SerializerFeature.WriteDateUseDateFormat, SerializerFeature.UseSingleQuotes, SerializerFeature.IgnoreNonFieldGetter));
				pw.close();
			}
			logger.log(Level.INFO, "cmd:" + cmd + " 处理结果：" + JsonFormatTool.formatJson(JSON.toJSONString(resultMap, SerializerFeature.WriteDateUseDateFormat, SerializerFeature.UseSingleQuotes, SerializerFeature.IgnoreNonFieldGetter)));

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 初始化时加载数据配置项目
	 */
	private void initConfig() {
		String configPath = this.getClass().getClassLoader().getResource("/").getPath();
		File configFile = new File(configPath, "config.json");
		String json = readConfigFile(configFile);
		try {
			List<KSyncConfig> allConfigs = JSON.parseArray(json, KSyncConfig.class);
			KSyncConfig.getInstance().addConfig(allConfigs);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	/**
	 * 获取request body中的数据
	 * 
	 * @param in
	 * @param charsetName
	 * @return
	 */
	private String streamToString(InputStream in, String charsetName) {
		String resultData = "";
		try {
			InputStreamReader isr = new InputStreamReader(in, Charset.forName(charsetName));
			BufferedReader bufferReader = new BufferedReader(isr);
			String inputLine = "";
			while ((inputLine = bufferReader.readLine()) != null) {
				resultData += inputLine + "\n";
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return resultData;
	}

	/**
	 * 根据请求头解析出文件名 请求头的格式：火狐和google浏览器下：form-data; name="file"; filename="snmp4j--api.zip" IE浏览器下：form-data; name="file"; filename="E:\snmp4j--api.zip"
	 * 
	 * @param header
	 *            请求头
	 * @return 文件名
	 */
	private String getFileName(String header) {
		/**
		 * String[] tempArr1 = header.split(";");代码执行完之后，在不同的浏览器下，tempArr1数组里面的内容稍有区别 火狐或者google浏览器下：tempArr1={form-data,name="file",filename="snmp4j--api.zip"} IE浏览器下：tempArr1={form-data,name="file",filename="E:\snmp4j--api.zip"}
		 */
		String[] tempArr1 = header.split(";");
		/**
		 * 火狐或者google浏览器下：tempArr2={filename,"snmp4j--api.zip"} IE浏览器下：tempArr2={filename,"E:\snmp4j--api.zip"}
		 */
		String[] tempArr2 = tempArr1[2].split("=");
		// 获取文件名，兼容各种浏览器的写法
		String fileName = tempArr2[1].substring(tempArr2[1].lastIndexOf("\\") + 1).replaceAll("\"", "");
		return fileName;
	}

	/**
	 * 读取json配置文件
	 * 
	 * @param configFile
	 * @return
	 */
	private String readConfigFile(File configFile) {
		String line = "", content = "";

		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(configFile));

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
}
