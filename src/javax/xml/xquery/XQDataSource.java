package javax.xml.xquery;

import java.io.PrintWriter;
import java.util.Properties;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQDataSource extends XQDataFactory {

    XQConnection getConnection() throws XQException;

    XQConnection getConnection(java.sql.Connection con) throws XQException;

    XQConnection getConnection(String username, String password) throws XQException;

    int getLoginTimeout();

    PrintWriter getLogWriter();

    String getProperty(String name) throws XQException;

    String[] getSupportedPropertyNames();

    void setCommonHandler(XQCommonHandler handler) throws XQException;

    void setLoginTimeout(int seconds) throws XQException;

    void setLogWriter(PrintWriter out) throws XQException;
    
    void setProperties(Properties props) throws XQException;

    void setProperty(String name, String value) throws XQException;




}
