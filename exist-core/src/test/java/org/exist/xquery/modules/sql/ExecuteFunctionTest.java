package org.exist.xquery.modules.sql;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.exist.dom.QName;
import org.exist.dom.memtree.ElementImpl;
import org.exist.dom.memtree.MemTreeBuilder;
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
    public void testStringEncoding() throws SQLException, XPathException {

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

        Object[] mocks = new Object[] { connection, stmt, rs, rsmd };

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

        replay(mocks);

        // register mocked connection
        final long connId = SQLModule.storeConnection( context, connection );

        // execute function

        Sequence res = execute.eval(new Sequence[] {
                new IntegerValue(connId),
                new StringValue(sql),
                new BooleanValue(false)
            }, Sequence.EMPTY_SEQUENCE);


        // assert expectations

        verify(mocks);

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
    public void testEmptyParameters() throws SQLException, XPathException {

        // mocks a simple SQL prepared statement with one parameter
        // is filled with an empty xsl:param element

        XQueryContext context = new XQueryContextStub();
        ExecuteFunction execute = new ExecuteFunction(context, signatureByArity(ExecuteFunction.signatures, functionName, 3));

        // this is what an empty xsl:param element of type varchar should use to fill prepared statement parameters
        final String emptyStringValue = null;
        final Integer emptyIntValue = null;

        final String sql = "SELECT ? AS COL1, ? AS COL2";

        // create mock objects

        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData rsmd = mock(ResultSetMetaData.class);

        Object[] mocks = new Object[] { connection, preparedStatement, rs, rsmd };

        // register mocked connection and prepared statement
        final long connId = SQLModule.storeConnection( context, connection );
        final long stmtId = SQLModule.storePreparedStatement( context, new PreparedStatementWithSQL(sql, preparedStatement));

        // mock behavior

        preparedStatement.setObject(1, emptyStringValue, Types.VARCHAR);
        preparedStatement.setObject(2, emptyIntValue, Types.INTEGER);

        expect(preparedStatement.execute()).andReturn(true);
        expect(preparedStatement.getResultSet()).andReturn(rs);
        expect(rs.next()).andReturn(true).andReturn(false);
        expect(rs.getRow()).andReturn(1);
        expect(rs.getString(1)).andReturn(emptyStringValue);
        expect(rs.wasNull()).andReturn(emptyStringValue == null);
        expect(rs.getString(2)).andReturn(null);
        expect(rs.wasNull()).andReturn(emptyIntValue == null);

        expect(rs.getMetaData()).andStubReturn(rsmd);
        expect(rsmd.getColumnCount()).andStubReturn(2);
        expect(rsmd.getColumnLabel(1)).andStubReturn("COL1");
        expect(rsmd.getColumnLabel(2)).andStubReturn("COL2");
        expect(rsmd.getColumnTypeName(1)).andStubReturn("VARCHAR");
        expect(rsmd.getColumnTypeName(2)).andStubReturn("INTEGER");
        expect(rsmd.getColumnType(1)).andStubReturn(Types.VARCHAR);
        expect(rsmd.getColumnType(2)).andStubReturn(Types.INTEGER);
        rs.close();

        replay(mocks);

        // execute function

        MemTreeBuilder paramBuilder = new MemTreeBuilder(context);

        paramBuilder.startDocument();

        paramBuilder.startElement(new QName("parameters", SQLModule.NAMESPACE_URI), null);
        paramBuilder.startElement(new QName("param", SQLModule.NAMESPACE_URI), null);
        paramBuilder.addAttribute(new QName("type", SQLModule.NAMESPACE_URI), "varchar");
        paramBuilder.endElement();
        paramBuilder.startElement(new QName("param", SQLModule.NAMESPACE_URI), null);
        paramBuilder.addAttribute(new QName("type", SQLModule.NAMESPACE_URI), "integer");
        paramBuilder.endElement();
        paramBuilder.endElement();

        paramBuilder.endDocument();

        final ElementImpl sqlParams = (ElementImpl) paramBuilder.getDocument().getFirstChild();

        execute.eval(new Sequence[] {
                new IntegerValue(connId),
                new IntegerValue(stmtId),
                sqlParams,
                new BooleanValue(false)
            }, Sequence.EMPTY_SEQUENCE);


        // assert expectations

        verify(preparedStatement);

    }

    @Test
    public void testSQLException() throws SQLException, XPathException {

        // mocks a simple SQL prepared statement with one parameter that fails on execution
        // and verifies the error message

        // the parameter is filled with an empty sql:param element

        XQueryContext context = new XQueryContextStub();
        ExecuteFunction execute = new ExecuteFunction(context, signatureByArity(ExecuteFunction.signatures, functionName, 3));


        final String sql = "SELECT ?";
        final String test_message = "SQL ERROR";
        final String test_sqlState = "SQL STATE";

        // create mock objects

        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);

        Object[] mocks = new Object[] { connection, preparedStatement };

        // register mocked connection and prepared statement
        final long connId = SQLModule.storeConnection( context, connection );
        final long stmtId = SQLModule.storePreparedStatement( context, new PreparedStatementWithSQL(sql, preparedStatement));

        // mock behavior

        preparedStatement.setObject(1, null, Types.VARCHAR);
        expect(preparedStatement.execute()).andThrow(new SQLException(test_message, test_sqlState));

        replay(mocks);

        // execute function

        MemTreeBuilder paramBuilder = new MemTreeBuilder(context);

        paramBuilder.startDocument();

        paramBuilder.startElement(new QName("parameters", SQLModule.NAMESPACE_URI), null);
        paramBuilder.startElement(new QName("param", SQLModule.NAMESPACE_URI), null);
        paramBuilder.addAttribute(new QName("type", SQLModule.NAMESPACE_URI), "varchar");
        paramBuilder.endElement();
        paramBuilder.endElement();

        paramBuilder.endDocument();

        final ElementImpl sqlParams = (ElementImpl) paramBuilder.getDocument().getFirstChild();

        Sequence res = execute.eval(new Sequence[] {
                new IntegerValue(connId),
                new IntegerValue(stmtId),
                sqlParams,
                new BooleanValue(false)
            }, Sequence.EMPTY_SEQUENCE);


        // assert expectations

        verify(mocks);

        assertEquals(1, res.getItemCount());
        assertEquals(Type.ELEMENT, res.getItemType());

        Node root = ((NodeValue) res.itemAt(0)).getNode();
        assertEquals("sql:exception", root.getNodeName());

        assertEquals(6, root.getChildNodes().getLength());

        Node node = root.getFirstChild();

        Node state = node;
        assertEquals("sql:state", state.getNodeName());
        assertEquals(test_sqlState, state.getTextContent());

        node = node.getNextSibling();
        Node message = node;
        assertEquals("sql:message", message.getNodeName());
        assertEquals(test_message, message.getTextContent());

        node = node.getNextSibling();
        Node stackTrace = node;
        assertEquals("sql:stack-trace", stackTrace.getNodeName());

        node = node.getNextSibling();
        Node sqlErr = node;
        assertEquals("sql:sql", sqlErr.getNodeName());
        assertEquals(sql, sqlErr.getTextContent());

        node = node.getNextSibling();
        Node parameters = node;
        assertEquals("sql:parameters", parameters.getNodeName());

        Node param1 = parameters.getFirstChild();
        assertEquals("sql:param", param1.getNodeName());
        assertEquals("varchar", param1.getAttributes().getNamedItemNS(SQLModule.NAMESPACE_URI, "type").getTextContent());
        assertEquals("", param1.getTextContent());

        node = node.getNextSibling();
        Node xquery = node;
        assertEquals("sql:xquery", xquery.getNodeName());

    }

    @Test
    public void testMissingParamType() {

        // mocks a simple SQL prepared statement with one parameter that lacks a type attribute.
        // This should throw an informative error.

        XQueryContext context = new XQueryContextStub();
        ExecuteFunction execute = new ExecuteFunction(context, signatureByArity(ExecuteFunction.signatures, functionName, 3));


        final String sql = "SELECT ?";

        // create mock objects

        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);

        Object[] mocks = new Object[] { connection, preparedStatement };

        // register mocked connection and prepared statement
        final long connId = SQLModule.storeConnection( context, connection );
        final long stmtId = SQLModule.storePreparedStatement( context, new PreparedStatementWithSQL(sql, preparedStatement));

        // mock behavior

        // no behavior necessary - error should be thrown before first call

        replay(mocks);

        // execute function

        MemTreeBuilder paramBuilder = new MemTreeBuilder(context);

        paramBuilder.startDocument();

        paramBuilder.startElement(new QName("parameters", SQLModule.NAMESPACE_URI), null);
        paramBuilder.startElement(new QName("param", SQLModule.NAMESPACE_URI), null);
        paramBuilder.endElement();
        paramBuilder.endElement();

        paramBuilder.endDocument();

        final ElementImpl sqlParams = (ElementImpl) paramBuilder.getDocument().getFirstChild();

        try {
            execute.eval(new Sequence[] {
                    new IntegerValue(connId),
                    new IntegerValue(stmtId),
                    sqlParams,
                    new BooleanValue(false)
            }, Sequence.EMPTY_SEQUENCE);

            fail("This should have thrown");
        } catch (XPathException e) {
            assertTrue(e.getMessage().contains("<sql:param> must contain attribute sql:type"));
        }

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

        Object[] mocks = new Object[] { connection, stmt };

        // mock behavior

        expect(connection.createStatement()).andReturn(stmt);
        expect(stmt.execute(query)).andStubThrow(new SQLException(testMessage));
        stmt.close();

        replay(mocks);

        // register mocked connection
        final long connId = SQLModule.storeConnection( context, connection );

        // execute function

        Sequence res = execute.eval(new Sequence[] {
                new IntegerValue(connId),
                new StringValue(query),
                new BooleanValue(false)
        }, Sequence.EMPTY_SEQUENCE);


        // assert expectations

        verify(mocks);

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

        @Override
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
