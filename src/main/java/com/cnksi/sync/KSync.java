package com.cnksi.sync;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import com.alibaba.fastjson.JSON;
import com.cnksi.sync.dao.KSyncConfig;
import com.cnksi.sync.dao.KSyncMysqlDao;

public class KSync {

	private static Logger logger = Logger.getLogger(KSync.class);

	KSyncMysqlDao dao = null;

	public KSyncConfig config = null;

	public KSync(KSyncConfig config) {
		this.config = config;
		dao = new KSyncMysqlDao(config);
	}

	/**
	 * 给所有表添加字段
	 * 
	 * @return
	 * @throws SQLException
	 */
	public String init() {

		String msg = "";
		try {

			dao.clearTimeStamp(config.getLstModifyField());

			if (config.getSyncTables().isEmpty()) {
				try {
					List<Map<String, Object>> tables = dao.getAllTableName();
					for (Map<String, Object> table : tables) {
						msg += addLastModifyColumn((String) table.get("table_name"));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {

				for (Entry<String, String> entry : config.getSyncTables().entrySet()) {
					msg += addLastModifyColumn(entry.getKey());
				}
			}
		} catch (Exception ex) {
			msg = ex.getMessage();
			ex.printStackTrace();
		}

		return msg;
	}

	private String addLastModifyColumn(String tableName) throws Exception {
		return dao.addColumn(tableName, config.getLstModifyField(), config.getEnabledFiled());
	}

	public Map<String, List<Map<String, Object>>> getStruct() throws Exception {
		return dao.struct(config);
	}

	public List<Map<String, Object>> getTableData(String tableName, String where, int pageNum, String... values) throws Exception {

		if (config.getSyncTables().isEmpty() || config.getSyncTables().containsKey(tableName)) {

			String bwhere = config.getSyncCondition().get(tableName);

			if (where == null || where.length() < 1) {
				where = " 1 = 1 ";
			}

			if (bwhere != null && bwhere.length() > 0) {
				where += " and " + config.getSyncCondition().get(tableName);
			}

			String columns = "*";
			if (config.getSyncTables().containsKey(tableName)) {
				columns = config.getSyncTables().get(tableName);
			}
			String sql = String.format("select %s from %s where %s order by %s limit %d,%d", columns, tableName, where, config.getLstModifyField(), config.getPageSize() * (pageNum - 1), config.getPageSize());

			logger.info(sql);

			return dao.query(sql, values);
		} else {
			logger.error("服务器端不存在此表(" + tableName + ")或不允许下载");
			throw new Exception("服务器端不存在此表或不允许下载");
		}
	}

	/**
	 * 数据上传
	 * 
	 * @param uploadDataStr
	 * 
	 */
	@SuppressWarnings("unchecked")
	public void uploadData(String uploadDataStr) throws Exception {

		Map<String, Object> map = JSON.parseObject(uploadDataStr, Map.class);
		Map<String, List<Object[]>> tableSqls = new HashMap<String, List<Object[]>>();

		// 遍历多个表，并将数据保存到数据库中
		for (Entry<String, Object> entry : map.entrySet()) {
			String tableName = entry.getKey();
			Object value = entry.getValue();
			if (value == null)
				continue;

			if (value instanceof List) {// 处理数据为List
				List<Map<String, Object>> dataList = (List<Map<String, Object>>) entry.getValue();

				for (Map<String, Object> data : dataList) {
					genInsertSqlForMap(tableName, data, tableSqls);
				}
			} else {// 处理单条记录
				Map<String, Object> data = (Map<String, Object>) entry.getValue();
				genInsertSqlForMap(tableName, data, tableSqls);
			}
		}

		// 执行数据写入操作
		dao.executeUpdate("SET FOREIGN_KEY_CHECKS=0;", null);
		for (Entry<String, List<Object[]>> entry : tableSqls.entrySet()) {
			for (Object[] param : entry.getValue()) {
				dao.executeUpdate(entry.getKey(), param);
			}
		}
		dao.executeUpdate("SET FOREIGN_KEY_CHECKS=1;", null);
	}

	/**
	 * 生成insert/replace语句
	 * 
	 * @param tableName
	 * @param dataList
	 * @return
	 */
	private void genInsertSqlForMap(String tableName, Map<String, Object> data, Map<String, List<Object[]>> tableSqls) {
		if (data == null || data.isEmpty())
			return;

		StringBuilder insertFields = new StringBuilder("");
		StringBuilder updateFields = new StringBuilder("");
		StringBuilder qutas = new StringBuilder("");

		List<Object> valList = new ArrayList<Object>();

		for (Entry<String, Object> entry : data.entrySet()) {
			if (config != null && config.getLstModifyField() != null && config.getLstModifyField().equals(entry.getKey())) {
				continue;
			}
			if (entry.getValue() != null) {
				insertFields.append(entry.getKey()).append(",");
				updateFields.append(entry.getKey()).append("=").append("?").append(",");
				qutas.append("?").append(",");

				if (!"null".equals(entry.getValue())) {
					valList.add(entry.getValue());
				} else {
					valList.add(null);
				}
			}
		}
		insertFields.deleteCharAt(insertFields.length() - 1);
		updateFields.deleteCharAt(updateFields.length() - 1);
		qutas.deleteCharAt(qutas.length() - 1);

		String sql = String.format("insert into %s ( %s ) values(%s) ON DUPLICATE KEY UPDATE %s", tableName, insertFields.toString(), qutas.toString(), updateFields.toString());
		System.out.println(sql);

		valList.addAll(valList);
		if (tableSqls.containsKey(sql)) {
			tableSqls.get(sql).add(valList.toArray());
		} else {
			List<Object[]> dataList = new ArrayList<Object[]>();
			dataList.add(valList.toArray());
			tableSqls.put(sql, dataList);
		}
	}

	/**
	 * 
	 * @param folder
	 *            下载文件夹
	 * @param resultMap
	 * @param fileNames
	 *            过滤文件名
	 * @date 2016-3-20
	 */
	public List<String> getDownloadFile(String folder, String files, String direction) throws Exception {
		List<String> filterFileNames = new ArrayList<String>();
		if (files != null) {
			filterFileNames.addAll(Arrays.asList(files.split(",")));
		}

		List<String> fileNameList = new ArrayList<String>();
		try {

			// 获取文件夹下所有文件,判断pad缺陷的文件
			if ("down".equals(direction)) {
				File parentFile = new File(config.getFolder(), folder);
				if (parentFile.isDirectory()) {
					File[] fileColl = parentFile.listFiles();
					for (File file : fileColl) {
						if (file.isFile() && !filterFileNames.contains(file.getName())) {
							fileNameList.add(file.getName());
						}

					}
				}
			} else { // 判断服务器端缺少的文件
				for (String padFile : filterFileNames) {
					File _file = new File(config.getFolder() + File.separator + folder, padFile);
					if (!_file.exists()) {
						fileNameList.add(padFile);
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		return fileNameList;
	}

	/**
	 * 测试
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		String configName = "bdzinspection";

		String url = "jdbc:mysql://127.0.0.1/bdzinspection";

		String username = "root";

		String password = "root";

		String driver = "com.mysql.jdbc.Driver";

		KSyncConfig config1 = new KSyncConfig(configName, url, username, password, driver, "update_time", "dlt");

		config1.addSyncTable("device_part", "duid,deviceid,name,pic", "dlt=0 and pic is not null");

		KSyncConfig.getInstance().addConfig(config1);

		KSync sync = new KSync(KSyncConfig.getInstance().getConfig(configName));

		// 初始化数据库，为每张表添加数据
		sync.init();

		// Map<String, List<Map<String, Object>>> structMap = sync.getStruct();
		// 模拟分页查询
		KSyncConfig config = sync.config;
		for (Entry<String, String> entry : config.getSyncTables().entrySet()) {

			String tableName = entry.getKey();

			int page = 1;

			String where = config.getLstModifyField() + ">0";

			List<Map<String, Object>> datas = sync.getTableData(tableName, where, page);
			while (datas.size() == config.getPageSize()) {
				page++;
				datas = sync.getTableData(tableName, where, page);
			}
		}
	}

}
