package org.exist.storage;

import java.sql.*;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import org.exist.util.*;

public class OracleWrapper extends DBWrapper {

	protected String userid;
	protected String ctlDir;

	public OracleWrapper(Configuration config, DBConnectionPool pool) {
		super(config, pool);
		String user, pass, service;
		if((user = (String)config.getProperty("user")) == null)
            user = "test";
        if((pass = (String)config.getProperty("password")) == null)
            pass = "test";
		service = (String)config.getProperty("db-connection.serviceName");
		if((ctlDir = (String)config.getProperty("parser.ctlDir")) == null)
            ctlDir = "ctl";
		userid = user + "/" + pass;
		if(service != null)
			userid = userid + "@" + service;
	}

	public void loadFromFile(String fname, String table) 
	throws IOException {
		if(!checkFile(fname))
			return;
		File f = new File(fname);
		String absolutePath = f.getAbsolutePath();

		String pathSep = System.getProperty("file.separator", "/");
		String ctl = "control=" + ctlDir + pathSep + table + ".ctl";
		String data = "data=" + absolutePath;
		String arg = "sqlldr direct=true userid=" + userid + " " + data + " " + ctl;

		System.out.println("running: " + arg);
		Process process = Runtime.getRuntime().exec(arg);
		BufferedReader buf = new BufferedReader(new InputStreamReader(process.getInputStream()));
		int exitCode = 0;
		try {
			exitCode = process.waitFor();
		} catch(InterruptedException ie) { }
		String line;
		while((line = buf.readLine()) != null)
			System.out.println(line);
		if(exitCode != 0) {
			System.err.println("sqlldr exited with error: " + exitCode);
			throw new IOException("sqlldr exited with error-code: " + exitCode);
		}
		removeFile(absolutePath);
	}
}
