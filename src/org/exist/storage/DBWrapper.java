package org.exist.storage;

import java.sql.*;
import java.io.File;
import java.io.IOException;
import org.exist.util.*;

public abstract class DBWrapper {

	protected DBConnectionPool pool;
	protected Configuration config;

	public DBWrapper(Configuration config, DBConnectionPool pool) {
		this.pool = pool;
		this.config = config;
	}

	public abstract void loadFromFile(String fname, String table)
	throws IOException;
	
	protected boolean checkFile(String fname) {
		File f = new File(fname);
		if(f.length() > 0)
			return true;
		return false;
	}

	protected void removeFile(String fname) {
		File f = new File(fname);
		f.delete();
	}
}
