
/* eXist xml document repository and xpath implementation
 * Copyright (C) 2000,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.exist.storage;

import java.sql.*;
import java.util.Stack;
import org.apache.log4j.Category;

/**
 * This class provides batch processing of inserts. Mysql allows
 * to add multiple rows in one insert statement. It makes inserts
 * very fast. I don't know if any other database supports this
 * function, but it comes very handy here.
 *
 * The two static variables MAX_INSERTS and MAX_BUF_LEN control
 * how much data is collected until the actual insert is done.
 * MAX_INSERTS says how many rows should be inserted at a time.
 * MAX_BUF_LEN contains the maximum length of the sql command
 * buffer. Please note that the current version of the mm.mysql
 * driver has a bug here. Normally the maximum buffer size is
 * determined by a mysqld server variable called max_allowed_packet.
 * With the current driver this does not work and the
 * variable is set internally to 65000.
 *
 * I manually increased this value to 1000000 in the drivers
 * source code. If you run into problems, please use the mm.mysql
 * jar package contained in my distribution.
 */
public class TableInsert {

  protected final static int MAX_INSERTS = 5000;
  protected final static int MAX_BUF_LEN = 1000000;
  private static Category LOG = Category.getInstance(TableInsert.class.getName());

  protected DBConnectionPool pool;
  protected int size = 0;
  protected StringBuffer sql;
  protected String sqlStart;
  protected String table;
  protected Statement stmt;
  protected boolean direct = false;

  public TableInsert(DBConnectionPool pool, String table, String sqlStart, boolean directInsert) {
    this.pool = pool;
    this.sql = new StringBuffer();
    this.table = table;
    this.sqlStart = sqlStart;
	this.direct = directInsert;
    Connection con = pool.get();
    try {
      stmt = con.createStatement();
    } catch(SQLException e) {
      LOG.warn(e);
    }
    pool.release(con);
  }

  public void append(Object[] items) {
	  if(direct) {
		  directInsert(items);
		  return;
	  }
    if(size == MAX_INSERTS || sql.length() > MAX_BUF_LEN)
      flush();
    if(sql.length() > 0)
      sql.append(", (");
    else
      sql.append('(');
    String temp;
    for(int i = 0; i < items.length; i++) {
      if(i > 0)
        sql.append(", ");
      if(items[i] instanceof String) {
        sql.append('\'');
        temp = (String)items[i];
        temp = temp.replace('\'', '~');
        sql.append(temp);
        sql.append('\'');
      } else
        sql.append(items[i].toString());
    }
    sql.append(')');
    size++;
  }

  public void flush() {
    if(sql.length() == 0)
      return;
    //Connection con = pool.get();
    String cmd = sqlStart + sql.toString();
    try {
      //LOG.info(cmd);
      stmt.executeUpdate(cmd);
    } catch(SQLException e) {
      LOG.warn(cmd);
      LOG.warn(e);
    }
    sql.delete(0, sql.length());
    size = 0;
    //pool.release(con);
  }

	protected void directInsert(Object[] items) {
		StringBuffer cmd = new StringBuffer(sqlStart);
		cmd.append('(');
		String temp;
		for(int i = 0; i < items.length; i++) {
			if(i > 0)
				cmd.append(',');
			if(items[i] instanceof String) {
				cmd.append('\'');
				temp = (String)items[i];
				temp = temp.replace('\'', '~');
				cmd.append(temp);
				cmd.append('\'');
			} else
				cmd.append(items[i]).toString();
		}
		cmd.append(')');
		try {
			stmt.executeUpdate(cmd.toString());
		} catch(SQLException e) {
			LOG.warn(cmd.toString());
			LOG.warn(e);
		}
	}
}
