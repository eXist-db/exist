
package org.exist.storage;

import java.sql.*;
import java.io.File;
import java.io.IOException;
import org.exist.util.*;
import org.exist.storage.*;

public class MysqlWrapper extends DBWrapper {

    public MysqlWrapper(Configuration config, DBConnectionPool pool) {
        super(config, pool);
    }

	private static final String replaceBS(String fname) {
		StringBuffer n = new StringBuffer();
		char c;
		for(int i = 0; i < fname.length(); i++) {
			c = fname.charAt(i);
			switch(c) {
			case '\\': 
				n.append("\\\\");
				break;
			default:
				n.append(c);
				break;
			}
		}
		return n.toString();
	}

    public void loadFromFile(String fname, String table)
    throws IOException {
        if(!checkFile(fname))
            return;
        File f = new File(fname);
        String absolutePath = f.getAbsolutePath();
		
		// replace backslashes since MySQL interprets them as escape sequences
        if(absolutePath.indexOf('\\') > -1)
            //absolutePath = absolutePath.replace('\\', '/');
		    absolutePath = replaceBS(absolutePath);

		System.out.println("loading data from file '" + absolutePath + "'");
        Connection con = pool.get();
        String sql = "LOAD DATA INFILE '" + absolutePath + "' IGNORE INTO TABLE " 
            + table +
            " FIELDS TERMINATED BY '|'";
        try {
            Statement stmt = con.createStatement();
            stmt.executeUpdate(sql);
        } catch(SQLException se) {
            throw new IOException(se.toString());
        }
        pool.release(con);
        removeFile(absolutePath);
    }

	public static void main(String args[]) {
		System.out.println(replaceBS('\\' + "sql" + '\\' + "mysql.sql"));
	}
}

