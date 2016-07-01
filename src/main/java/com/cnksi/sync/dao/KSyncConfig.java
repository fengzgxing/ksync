package com.cnksi.sync.dao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KSyncConfig {

	private String configName = "appid";

	private String url = "jdbc:mysql://127.0.0.1/appid";

	private String username = "root";

	private String password = "root";

	private String driver = "com.mysql.jdbc.Driver";

	private int pageSize = 500;

	private String lstModifyField = "last_modify_time", enabledFiled = "enabled";

	// 同步Folder
	private String folder ="";

	/**
	 * 要同步到PAD端的表及字段数据
	 */
	private Map<String, String> syncTables = new LinkedHashMap<String, String>();

	/**
	 * 要同步到PAD端的表及同步条件
	 */
	private Map<String, String> syncCondition = new LinkedHashMap<String, String>();

	private Map<String, KSyncConfig> allConfig = new LinkedHashMap<String, KSyncConfig>();

	private static KSyncConfig config = null;

	public static KSyncConfig getInstance() {
		if (config == null) {
			config = new KSyncConfig();
		}
		return config;
	}

	public KSyncConfig() {

	}

	public KSyncConfig(String configName, String url, String username, String password, String driver, String lstModifyField, String enabledFileds) {
		this(configName, url, username, password);
		this.driver = driver;
		this.lstModifyField = lstModifyField;
		this.enabledFiled = enabledFileds;
	}

	public KSyncConfig(String configName, String url, String username, String password) {
		this.configName = configName;
		this.setUrl(url);
		this.username = username;
		this.password = password;
	}

	// 添加同步配置
	public void addConfig(List<KSyncConfig> configs) {
		this.allConfig.clear();
		for (KSyncConfig config : configs) {
			this.allConfig.put(config.getConfigName(), config);
		}
	}

	// 添加同步配置
	public void addConfig(KSyncConfig config) {
		this.allConfig.put(config.getConfigName(), config);
	}

	// 获取同步配置
	public KSyncConfig getConfig(String configName) {

		return this.allConfig.get(configName);
	}

	/**
	 * 添加同步到PAD端的数据库表及字段信息
	 * 
	 * @param tableName
	 * @param fields
	 */
	public void addSyncTable(String tableName, String fields, String condition) {
		syncTables.put(tableName, fields);
		syncCondition.put(tableName, condition);
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		
		this.url = url;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getDriver() {
		return driver;
	}

	public void setDriver(String driver) {
		this.driver = driver;
	}

	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	public Map<String, String> getSyncTables() {
		return syncTables;
	}

	public void setSyncTables(Map<String, String> syncTables) {
		this.syncTables = syncTables;
	}

	public String getLstModifyField() {
		return lstModifyField;
	}

	public void setLstModifyField(String lstModifyField) {
		this.lstModifyField = lstModifyField;
	}

	public String getEnabledFiled() {
		return enabledFiled;
	}

	public void setEnabledFiled(String enabledFileds) {
		this.enabledFiled = enabledFileds;
	}

	public Map<String, String> getSyncCondition() {
		return syncCondition;
	}

	public void setSyncCondition(Map<String, String> syncCondition) {
		this.syncCondition = syncCondition;
	}

	public String getConfigName() {
		return configName;
	}

	public void setConfigName(String configName) {
		this.configName = configName;
	}

	public String getFolder() {
		return folder;
	}

	public void setFolder(String folder) {
		this.folder = folder;
	}

}
