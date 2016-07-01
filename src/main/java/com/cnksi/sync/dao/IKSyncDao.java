package com.cnksi.sync.dao;

import java.util.List;
import java.util.Map;

public interface IKSyncDao {

	int executeUpdate(String sql, Object[] params)  throws Exception;

	List<Map<String, Object>> query(String sql, Object[] params) throws Exception;

	/**
	 * 获取数据库的表及字段信息
	 * 
	 * @return Map<tableName,FieldsInfoMap>
	 * 
	 */
	Map<String, List<Map<String, Object>>> struct(KSyncConfig config) throws Exception;
}
