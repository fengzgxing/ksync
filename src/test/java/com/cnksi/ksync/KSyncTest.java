package com.cnksi.ksync;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.alibaba.fastjson.JSON;
import com.cnksi.sync.KSync;
import com.cnksi.sync.dao.KSyncConfig;
import com.cnksi.util.JsonFormatTool;

public class KSyncTest {

	KSync sync = null;
	KSyncConfig config = null;

	Logger logger = Logger.getLogger(KSyncTest.class);

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
		sync = null;
	}

	/**
	 * 获取所有的表结构
	 */
	@Test
	public void testGetStruct() {
		try {

			// 配置要同步的数据库
			config = new KSyncConfig("test", "jdbc:mysql://127.0.0.1/test", "root", "root");
			sync = new KSync(config);

			// 查询数据库test中的所有表、字段，所有数据
			Map<String, List<Map<String, Object>>> structMap = sync.getStruct();
			String jsonStr = JSON.toJSONString(structMap);
			logger.debug("查询所有的表结构");
			logger.info(JsonFormatTool.formatJson(jsonStr));

			logger.debug("只查询blog的表结构");
			config = new KSyncConfig("test", "jdbc:mysql://127.0.0.1/test", "root", "root");
			// 只查询blog表中的id,content,title,enabled字段，返回enabled=0的数据
			config.addSyncTable("blog", "id,content,title,enabled", " enabled = 0 and title = {title}");
			sync = new KSync(config);
			structMap = sync.getStruct();
			jsonStr = JSON.toJSONString(structMap);
			logger.info(JsonFormatTool.formatJson(jsonStr));

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
