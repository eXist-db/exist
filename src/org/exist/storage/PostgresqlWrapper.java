
package org.exist.storage;

import java.sql.*;
import java.io.File;
import java.io.IOException;
import org.exist.util.*;
import java.util.StringTokenizer;

/**
 *  Description of the Class
 *
 * @author     wolf
 * @created    3. Juni 2002
 */
public class PostgresqlWrapper extends DBWrapper {

    /**
     *  Constructor for the PostgresqlWrapper object
     *
     * @param  config  Description of the Parameter
     * @param  pool    Description of the Parameter
     */
    public PostgresqlWrapper(Configuration config, DBConnectionPool pool) {
        super(config, pool);
    }


    /**
     *  Description of the Method
     *
     * @param  fname            Description of the Parameter
     * @param  table            Description of the Parameter
     * @exception  IOException  Description of the Exception
     */
    public void loadFromFile(String fname, String table)
             throws IOException {
        if (!checkFile(fname))
            return;
        File f = new File(fname);
        String absolutePath = f.getAbsolutePath();

				// fix for windows 
				if (File.separator.equals("\\")) {
					String newPath = escapeChars(absolutePath);
					//System.out.println("old: "+absolutePath+"   new:"+newPath);
					absolutePath = newPath;
				}

        Connection con = pool.get();
        String sql = "COPY " + table + " FROM '" + absolutePath + "'" +
                " USING DELIMITERS '|'";
        try {
            con.setAutoCommit(false);
            Statement stmt = con.createStatement();
            stmt.executeUpdate(sql);
            stmt.getConnection().commit();
            System.err.println("\n" + sql);
        } catch (SQLException se) {
            se.printStackTrace(System.err);
            throw new IOException(se.toString());
        }
        pool.release(con);
        //removeFile(absolutePath);
    }


	private String escapeChars(String str) {

		StringTokenizer tok = new StringTokenizer(str, "\\");
		StringBuffer buf = new StringBuffer();
		while (tok.hasMoreTokens()) {
			String token = tok.nextToken();
			buf.append(token);
			if (tok.hasMoreElements())
				buf.append("\\\\");
		}
		return buf.toString();
	} 

}

