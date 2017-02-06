/*

Copyright 2008-2016 E-Hentai.org
https://forums.e-hentai.org/
ehentai@gmail.com

This file is part of Hentai@Home.

Hentai@Home is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Hentai@Home is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Hentai@Home.  If not, see <http://www.gnu.org/licenses/>.

*/

package org.hath.base;


import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;


import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.After;

import org.junit.Before;
import org.junit.Test;

public class OutTest {

	private static final String ERROR_LOG_FILE_NAME = "log_err";
	private static final String OUTPUT_LOG_FILE_NAME = "log_out";

	private File logdir;

	@SuppressWarnings("deprecation")
	@Before
	public void setUp() throws Exception {

		logdir = Files.createTempDirectory(OutTest.class.getSimpleName()).toFile();
		Settings.setLogDir(logdir);
		Out.startLoggers();
	}

	@After
	public void tearDown() {
		Out.disableLogging();
	}

	private String readFile(Path logfile) throws FileNotFoundException, IOException {
		try (BufferedReader reader = Files.newBufferedReader(logfile)) {

			StringBuilder sb = new StringBuilder();
			String line;

			do {
				line = reader.readLine();
				sb.append(line);
				sb.append(System.lineSeparator());
			} while (line != null);

			return sb.toString();
		}
	}

	public void testDebug() throws Exception {
		Out.debug("debugLevel Foo bar");
		Out.flushLogs();

		assertThat(readFile(logdir.toPath().resolve("log_out")), containsString("debugLevel Foo bar"));
	}

	@Test
	public void testInfo() throws Exception {
		Out.info("infoLevel Foo bar");
		Out.flushLogs();

		assertThat(readFile(logdir.toPath().resolve("log_out")), containsString("infoLevel Foo bar"));
	}

	@Test
	public void testWarning() throws Exception {
		Out.warning("warningLevel Foo bar");
		Out.flushLogs();

		assertThat(readFile(logdir.toPath().resolve("log_out")), containsString("warningLevel Foo bar"));
	}

	@Test
	public void testError() throws Exception {
		Out.error("errorLevel Foo bar");
		Out.flushLogs();

		assertThat(readFile(logdir.toPath().resolve("log_out")), containsString("errorLevel Foo bar"));
	}

	@Test
	public void testWarningInErrorLog() throws Exception {
		Out.warning("warningLevel Foo bar");
		Out.flushLogs();

		assertThat(readFile(logdir.toPath().resolve("log_err")), containsString("warningLevel Foo bar"));
	}

	@Test
	public void testErrorInErrorLog() throws Exception {
		Out.error("errorLevel Foo bar");
		Out.flushLogs();

		assertThat(readFile(logdir.toPath().resolve("log_err")), containsString("errorLevel Foo bar"));
	}
}
