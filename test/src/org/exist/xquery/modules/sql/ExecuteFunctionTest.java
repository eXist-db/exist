package org.exist.xquery.modules.sql;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import org.junit.Test;
import org.w3c.dom.Node;

import java.sql.*;

/**
 * Unit Tests for sql:execute
 */
public class ExecuteFunctionTest {

    // the function that will be tested
    final static QName functionName = new QName( "execute", SQLModule.NAMESPACE_URI, SQLModule.PREFIX );


    @Test
    public void testStringEncoding() throws ClassNotFoundException, SQLException, EXistException, XPathException {

        // mocks a simple SQL query returning a single string and checks the result

        XQueryContext context = new XQueryContextStub();
        ExecuteFunction execute = new ExecuteFunction(context, signatureByArity(ExecuteFunction.signatures, functionName, 3));

        final String sql = "SELECT NAME FROM BLA";
        final String testValue = "<&>";


        // create mock objects

        Connection connection = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData rsmd = mock(ResultSetMetaData.class);

        // mock behavior

        expect(connection.createStatement()).andReturn(stmt);
        expect(stmt.execute(sql)).andReturn(true);
        expect(stmt.getResultSet()).andReturn(rs);
        stmt.close();

        expect(rs.getMetaData()).andReturn(rsmd);
        expect(rs.next()).andReturn(true).andReturn(false);
        expect(rs.getRow()).andReturn(1);
        expect(rs.getString(1)).andReturn(testValue);
        expect(rs.wasNull()).andReturn(false);
        rs.close();

        expect(rsmd.getColumnCount()).andStubReturn(1);
        expect(rsmd.getColumnLabel(1)).andStubReturn("NAME");
        expect(rsmd.getColumnTypeName(1)).andStubReturn("VARCHAR(100)");
        expect(rsmd.getColumnType(1)).andStubReturn(Types.VARCHAR);

        replay(connection, stmt, rs, rsmd);

        // register mocked connection
        final long connId = SQLModule.storeConnection( context, connection );

        // execute function

        Sequence res = execute.eval(new Sequence[] {
                new IntegerValue(connId),
                new StringValue(sql),
                new BooleanValue(false)
            }, Sequence.EMPTY_SEQUENCE);


        // assert expectations

        assertEquals(1, res.getItemCount());
        assertEquals(Type.ELEMENT, res.getItemType());

        Node root = ((NodeValue) res.itemAt(0)).getNode();
        assertEquals("sql:result", root.getNodeName());

        Node row = root.getFirstChild();
        assertEquals("sql:row", row.getNodeName());

        Node col = row.getFirstChild();
        assertEquals("sql:field", col.getNodeName());
        assertEquals(testValue, col.getTextContent());

    }



    @Test
    public void testEncodingInErrorMessage() throws SQLException, XPathException {

        // mocks a failing SQL query returning a single string and
        // checks the resulting error report

        XQueryContext context = new XQueryContextStub();
        ExecuteFunction execute = new ExecuteFunction(context, signatureByArity(ExecuteFunction.signatures, functionName, 3));
        final String query = "SELECT '<NAME>' FROM BLA";
        final String testMessage = "Some <&> error occurred!";


        // create mock objects

        Connection connection = mock(Connection.class);
        Statement stmt = mock(Statement.class);

        // mock behavior

        expect(connection.createStatement()).andReturn(stmt);
        expect(stmt.execute(query)).andStubThrow(new SQLException(testMessage));
        stmt.close();

        replay(connection, stmt);

        // register mocked connection
        final long connId = SQLModule.storeConnection( context, connection );

        // execute function

        Sequence res = execute.eval(new Sequence[] {
                new IntegerValue(connId),
                new StringValue(query),
                new BooleanValue(false)
        }, Sequence.EMPTY_SEQUENCE);


        // assert expectations

        // <sql:exception><sql:state/><sql:message/><sql:stack-trace/><sql:sql/></sql:exception>

        assertEquals(1, res.getItemCount());
        assertEquals(Type.ELEMENT, res.getItemType());

        Node root = ((NodeValue) res.itemAt(0)).getNode();
        assertEquals("sql:exception", root.getNodeName());

        Node state = root.getFirstChild();
        assertEquals("sql:state", state.getNodeName());

        Node msg = state.getNextSibling();
        assertEquals("sql:message", msg.getNodeName());
        assertEquals(testMessage, msg.getTextContent());

        Node stacktrace = msg.getNextSibling();
        assertEquals("sql:stack-trace", stacktrace.getNodeName());

        Node sql = stacktrace.getNextSibling();
        assertEquals("sql:sql", sql.getNodeName());
        assertEquals(query, sql.getTextContent());

        Node xquery = sql.getNextSibling();
        assertEquals("sql:xquery", xquery.getNodeName());

    }


    public  static class XQueryContextStub extends XQueryContext {
        public XQueryContextStub() {
            super();
        }

        public String getCacheClass() {
            return org.exist.util.io.FileFilterInputStreamCache.class.getName();
        }

    }


    static FunctionSignature signatureByArity(FunctionSignature[] signatures, QName functionName, int arity) {

        for (FunctionSignature signature : signatures) {
            if (signature.getName().equals(functionName)
                    && signature.getArgumentCount() == arity) {
                return signature;
            }

        }
        return null;
    }

}
