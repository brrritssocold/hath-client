package org.hath.base;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class OutTest {

	@Before
	public void setUp() throws Exception {
	}

	@Ignore
	@Test
	public void testOverrideDefaultOutput() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Ignore
	@Test
	public void testAddOutListener() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Ignore
	@Test
	public void testRemoveOutListener() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Ignore
	@Test
	public void testDisableLogging() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Test
	public void testFlushLogs() throws Exception {
		Out.flushLogs();
	}

	@Test
	public void testDebug() throws Exception {
		Out.debug("debug Foo bar");
		Out.flushLogs();
	}

	@Test
	public void testInfo() throws Exception {
		Out.info("info Foo bar");
		Out.flushLogs();
	}

	@Test
	public void testWarning() throws Exception {
		Out.warning("warning Foo bar");
		Out.flushLogs();
	}

	@Test
	public void testError() throws Exception {
		Out.error("error Foo bar");
		Out.flushLogs();
	}
}
