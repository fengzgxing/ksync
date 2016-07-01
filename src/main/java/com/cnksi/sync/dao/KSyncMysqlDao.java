package com.cnksi.sync.dao;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

/**
 * 数据库连接类 说明:封装了 无参，有参，存储过程的调用
 * 
 * @author iflytek
 *
 */
public class KSyncMysqlDao implements IKSyncDao {

	Logger logger = Logger.getLogger(KSyncMysqlDao.class);

	/**
	 * 数据库驱动类名称
	 */
	private static String DRIVER = "com.mysql.jdbc.Driver";

	/**
	 * 连接字符串
	 */
	private String url = "jdbc:mysql://127.0.0.1/bdzinspection1";

	/**
	 * 用户名
	 */
	private String username = "root";

	/**
	 * 密码
	 */
	private String password = "root";

	public KSyncMysqlDao() {

	}

	public KSyncMysqlDao(String driver, String url, String username, String password) {
		DRIVER = driver;
		this.url = url;
		this.username = username;
		this.password = password;
	}

	public KSyncMysqlDao(KSyncConfig config) {
		DRIVER = config.getDriver();
		this.url = config.getUrl();
		this.username = config.getUsername();
		this.password = config.getPassword();
	}

	/**
	 * 创建数据库连接对象
	 */
	private Connection connnection = null;

	/**
	 * 创建PreparedStatement对象
	 */
	private PreparedStatement preparedStatement = null;

	/**
	 * 创建CallableStatement对象
	 */
	private CallableStatement callableStatement = null;

	/**
	 * 创建结果集对象
	 */
	private ResultSet resultSet = null;

	static {
		try {
			// 加载数据库驱动程序
			Class.forName(DRIVER);
		} catch (ClassNotFoundException e) {
			System.out.println("加载驱动错误");
			e.printStackTrace();
		}
	}

	/**
	 * 建立数据库连接
	 * 
	 * @return 数据库连接
	 */
	public Connection getConnection() {
		try {

			if (connnection == null || connnection.isClosed()) {
				// 获取连接
				if (DRIVER.contains("mysql")) {
					if (!url.contains("?")) {
						url += "?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull";
					}
				}
				connnection = DriverManager.getConnection(url, username, password);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return connnection;
	}

	/**
	 * 查询所有的数据库表
	 * 
	 * @return
	 */
	public List<Map<String, Object>> getAllTableName() {
		List<Map<String, Object>> tables = null;
		try {
			String scheme = this.getConnection().getCatalog();
			String sql = "select table_name,table_comment from information_schema.tables where table_schema=? and table_type=?";
			tables = query(sql, new String[] { scheme, "BASE TABLE" });
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return tables;
	}

	/**
	 * 清理数据表的timestamp字段
	 * 
	 * @throws Exception
	 */
	public void clearTimeStamp(String lstModifyColumn) throws Exception {
		String scheme = this.getConnection().getCatalog();
		// 解决MySQL中的只能有一个timestamp字段有默认值的情况
		String delTimeStampSql = "SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=? AND column_default =? and column_name !=?";
		List<Map<String, Object>> tables = query(delTimeStampSql, new String[] { scheme, "CURRENT_TIMESTAMP", lstModifyColumn });
		if (tables != null && tables.size() > 0) {
			for (Map<String, Object> tableMap : tables) {
				String delColumnSql = "ALTER TABLE " + tableMap.get("table_name") + " MODIFY COLUMN " + tableMap.get("column_name") + " DATETIME;";
				executeUpdate(delColumnSql, null);
			}
		}
	}

	/**
	 * 为某个表添加last_modify_time 和 enabled 字段
	 * 
	 * @param database
	 * @param table
	 * @param lstColumn最后修改时间
	 * @param enabledColumn逻辑删除标记
	 * @return
	 * @throws SQLException
	 */
	public String addColumn(String table, String lstColumn, String enabledColumn) throws Exception {
		String msg = "";
		String scheme = this.getConnection().getCatalog();
		String lstColumn_exists = String.format("SELECT * FROM information_schema.columns WHERE table_schema='%s' AND table_name = '%s'  AND column_name = '%s'", scheme, table, lstColumn);
		Object columnObject = querySingle(lstColumn_exists, null);
		if (columnObject == null) {
			String sql = String.format("ALTER TABLE %s ADD COLUMN %s TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", table, lstColumn);
			String usql = String.format("update %s set %s = now() where %s is null", table, lstColumn, lstColumn);
			try {
				executeUpdate(new String[] { sql, usql });
				msg = "表" + table + "添加列" + lstColumn + "成功 <br/>";
			} catch (SQLException e) {
				e.printStackTrace();
				msg = "表" + table + "添加列" + lstColumn + "失败，原因：" + e.getMessage() + "<br/>";
			}
		} else {
			msg = "表 " + table + " 已存在列" + lstColumn + "<br/>";
		}

		String enabledColumn_exists = String.format("SELECT * FROM information_schema.columns WHERE table_schema='%s' AND table_name = '%s'  AND column_name = '%s'", scheme, table, enabledColumn);
		columnObject = querySingle(enabledColumn_exists, null);
		if (columnObject == null) {
			String sql = String.format("ALTER TABLE %s ADD COLUMN %s int(11) DEFAULT 0", table, enabledColumn);
			String usql = String.format("update %s set %s = 0 where %s is null", table, enabledColumn, enabledColumn);
			try {
				executeUpdate(new String[] { sql, usql });
				msg = "表" + table + "添加列" + enabledColumn + "成功 <br/>";
			} catch (SQLException e) {
				e.printStackTrace();
				msg = "表" + table + "添加列" + enabledColumn + "失败，原因：" + e.getMessage() + "<br/>";
			}
		} else {
			msg = "表 " + table + " 已存在列" + enabledColumn + "<br/>";
		}

		return msg;
	}

	/**
	 * insert update delete SQL语句的执行的统一方法
	 * 
	 * @param sql
	 *            SQL语句
	 * @param params
	 *            参数数组，若没有参数则为null
	 * @return 受影响的行数
	 * @throws SQLException
	 */
	public int executeUpdate(String sql, Object[] params) throws SQLException {
		// 受影响的行数
		int affectedLine = 0;

		try {
			// 获得连接
			connnection = this.getConnection();
			// 调用SQL
			preparedStatement = connnection.prepareStatement(sql);

			System.out.println(sql);

			// 参数赋值
			if (params != null) {
				for (int i = 0; i < params.length; i++) {
					preparedStatement.setObject(i + 1, params[i]);
				}
			}

			// 执行
			affectedLine = preparedStatement.executeUpdate();

		} catch (SQLException e) {
			throw e;
		} finally {
			// 释放资源
			closeAll();
		}
		return affectedLine;
	}

	/**
	 * insert update delete SQL语句的执行的统一方法
	 * 
	 * @param sql
	 *            SQL语句
	 * @param params
	 *            参数数组，若没有参数则为null
	 * @return 受影响的行数
	 * @throws SQLException
	 */
	public int executeUpdate(String[] sqls) throws SQLException {
		// 受影响的行数
		int affectedLine = 0;

		try {
			// 获得连接
			connnection = this.getConnection();

			for (String sql : sqls) {
				// 调用SQL
				preparedStatement = connnection.prepareStatement(sql);
				// 执行
				affectedLine = preparedStatement.executeUpdate();
			}
		} catch (SQLException e) {
			throw e;
		} finally {
			// 释放资源
			closeAll();
		}
		return affectedLine;
	}

	/**
	 * SQL 查询将查询结果直接放入ResultSet中
	 * 
	 * @param sql
	 *            SQL语句
	 * @param params
	 *            参数数组，若没有参数则为null
	 * @return 结果集
	 */
	private ResultSet queryRS(String sql, Object[] params) {
		try {
			// 获得连接
			connnection = this.getConnection();

			// 调用SQL
			preparedStatement = connnection.prepareStatement(sql);

			// 参数赋值
			if (params != null) {
				for (int i = 0; i < params.length; i++) {
					preparedStatement.setObject(i + 1, params[i]);
				}
			}

			// 执行
			resultSet = preparedStatement.executeQuery();

		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}

		return resultSet;
	}

	/**
	 * SQL 查询将查询结果：一行一列
	 * 
	 * @param sql
	 *            SQL语句
	 * @param params
	 *            参数数组，若没有参数则为null
	 * @return 结果集
	 */
	public Object querySingle(String sql, Object[] params) {
		Object object = null;
		try {
			// 获得连接
			connnection = this.getConnection();

			// 调用SQL
			preparedStatement = connnection.prepareStatement(sql);

			// 参数赋值
			if (params != null) {
				for (int i = 0; i < params.length; i++) {
					preparedStatement.setObject(i + 1, params[i]);
				}
			}

			// 执行
			resultSet = preparedStatement.executeQuery();

			if (resultSet.next()) {
				object = resultSet.getObject(1);
			}

		} catch (SQLException e) {
			System.out.println(e.getMessage());
		} finally {
			closeAll();
		}

		return object;
	}

	/**
	 * 获取结果集，并将结果放在List中
	 * 
	 * @param sql
	 *            SQL语句
	 * @return List 结果集
	 */
	public List<Map<String, Object>> query(String sql, Object[] params) throws Exception {
		// 执行SQL获得结果集
		ResultSet rs = queryRS(sql, params);

		// 创建ResultSetMetaData对象
		ResultSetMetaData rsmd = null;

		// 结果集列数
		int columnCount = 0;
		try {
			rsmd = rs.getMetaData();

			// 获得结果集列数
			columnCount = rsmd.getColumnCount();
		} catch (SQLException e1) {
			throw new Exception(e1);
		}

		// 创建List
		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

		try {
			// 将ResultSet的结果保存到List中
			while (rs.next()) {
				Map<String, Object> map = new LinkedHashMap<String, Object>();
				for (int i = 1; i <= columnCount; i++) {
					map.put(rsmd.getColumnLabel(i), rs.getObject(i));
				}
				list.add(map);
			}
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		} finally {
			// 关闭所有资源
			closeAll();
		}

		return list;
	}

	/**
	 * 存储过程带有一个输出参数的方法
	 * 
	 * @param sql
	 *            存储过程语句
	 * @param params
	 *            参数数组
	 * @param outParamPos
	 *            输出参数位置
	 * @param SqlType
	 *            输出参数类型
	 * @return 输出参数的值
	 */
	public Object query(String sql, Object[] params, int outParamPos, int SqlType) {
		Object object = null;
		connnection = this.getConnection();
		try {
			// 调用存储过程
			callableStatement = connnection.prepareCall(sql);

			// 给参数赋值
			if (params != null) {
				for (int i = 0; i < params.length; i++) {
					callableStatement.setObject(i + 1, params[i]);
				}
			}

			// 注册输出参数
			callableStatement.registerOutParameter(outParamPos, SqlType);

			// 执行
			callableStatement.execute();

			// 得到输出参数
			object = callableStatement.getObject(outParamPos);

		} catch (SQLException e) {
			System.out.println(e.getMessage());
		} finally {
			// 释放资源
			closeAll();
		}

		return object;
	}

	/***
	 * 获取表结构
	 * 
	 * @param config
	 */
	@Override
	public Map<String, List<Map<String, Object>>> struct(KSyncConfig config) throws Exception {
		connnection = this.getConnection();
		Map<String, List<Map<String, Object>>> structMap = new LinkedHashMap<String, List<Map<String, Object>>>();
		try {
			String scheme = this.connnection.getCatalog();
			// 如果没配置同步数据表，则默认同步所有数据库 查询数据库中的基础数据库表
			if (config.getSyncTables().isEmpty()) {
				String sql = "select table_name,table_comment from information_schema.tables where table_schema=? and table_type=?";
				List<Map<String, Object>> tables = query(sql, new String[] { scheme, "BASE TABLE" });

				logger.debug(String.format("查询%s数据库中所有的基础表：%s", scheme, sql));

				for (Map<String, Object> table : tables) {
					config.addSyncTable(table.get("table_name").toString(), "*", "");
					logger.debug(String.format("添加同步数据表：%s", table.get("table_name")));
				}
			}
			for (Entry<String, String> entry : config.getSyncTables().entrySet()) {
				String tableName = entry.getKey();
				String fieldStr = config.getSyncTables().get(tableName);
				String sql_tableinfo = "";
				if ("*".equals(fieldStr.trim())) {
					sql_tableinfo = "select column_name,column_default,is_nullable,data_type,column_key,column_comment from information_schema.columns where table_schema=? and table_name=? order by ordinal_position;";
					List<Map<String, Object>> fields = query(sql_tableinfo, new String[] { scheme, tableName });
					structMap.put(tableName, fields);
				} else {
					String[] schemeTable = new String[] { scheme, tableName };
					String[] fieldStrs = fieldStr.split(",");
					StringBuffer quta = new StringBuffer();
					for (int i = 0; i < fieldStrs.length; i++) {
						quta.append("?,");
					}
					quta.deleteCharAt(quta.length() - 1);

					String[] args = Arrays.copyOf(schemeTable, schemeTable.length + fieldStrs.length);
					System.arraycopy(fieldStrs, 0, args, schemeTable.length, fieldStrs.length);
					sql_tableinfo = String.format("select column_name,column_default,is_nullable,data_type,column_key,column_comment from information_schema.columns where table_schema=? and table_name=? and column_name in(%s) order by ordinal_position", quta.toString());
					List<Map<String, Object>> fields = query(sql_tableinfo, args);
					structMap.put(tableName, fields);
				}
				logger.debug(String.format("获取数据表%s结构SQL：%s", tableName, sql_tableinfo));
			}
		} catch (Exception ex) {
			throw ex;
		} finally {
			closeAll();
		}
		return structMap;
	}

	/**
	 * 关闭所有资源
	 */
	private void closeAll() {
		// 关闭结果集对象
		if (resultSet != null) {
			try {
				resultSet.close();
			} catch (SQLException e) {
				logger.error("关闭resultSet错误：" + e.getMessage());
			}
		}

		// 关闭PreparedStatement对象
		if (preparedStatement != null) {
			try {
				preparedStatement.close();
			} catch (SQLException e) {
				logger.error("关闭PreparedStatement错误：" + e.getMessage());
			}
		}

		// 关闭CallableStatement 对象
		if (callableStatement != null) {
			try {
				callableStatement.close();
			} catch (SQLException e) {
				logger.error("关闭CallableStatement错误：" + e.getMessage());
			}
		}

		// 关闭Connection 对象
		if (connnection != null) {
			try {
				connnection.close();
			} catch (SQLException e) {
				logger.error("关闭connnection错误：" + e.getMessage());
			}
		}
	}

}
