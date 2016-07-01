package com.cnksi.ksync.web;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.verify;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.cnksi.sync.web.ApiV1Servlet;

public class ApiV1ServletTest {

	private ApiV1Servlet servlet;
	private HttpServletRequest mockRequest;
	private HttpServletResponse mockResponse;

	@Before
	public void setUp() throws Exception {
		servlet = new ApiV1Servlet();

		mockRequest = createMock(HttpServletRequest.class); // 加载
		mockResponse = createMock(HttpServletResponse.class);
	}

	@After
	public void tearDown() throws Exception {
		verify(mockRequest); // 验证
		verify(mockResponse);
	}

	@Test
	public void test() {
		mockRequest.getParameter("appid"); // 传入参数
		expectLastCall().andReturn("1");
//
//		mockRequest.getParameter("name"); // 传入参数
//		expectLastCall().andReturn("chevy");
//
//		mockRequest.getParameter("gender"); // 传入参数
//		expectLastCall().andReturn("男");
//
//		replay(mockRequest); // 回放
//		replay(mockResponse);

		try {
			servlet.doPost(mockRequest, mockResponse);
		} catch (ServletException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} // 调用

	}

}
