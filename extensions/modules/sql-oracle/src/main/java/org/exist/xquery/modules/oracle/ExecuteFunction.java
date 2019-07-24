/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.oracle;

import java.io.PrintStream;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import oracle.jdbc.OracleTypes;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.sql.SQLModule;
import org.exist.xquery.modules.sql.SQLUtils;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * eXist Oracle Module Extension ExecuteFunction
 * 
 * Execute a PL/SQL stored procedure within an Oracle RDBMS.
 * 
 * @author <a href="mailto:robert.walpole@metoffice.gov.uk">Robert Walpole</a>
 * @serial 2009-03-23
 * @version 1.0
 * 
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext,
 *      org.exist.xquery.FunctionSignature)
 */
public class ExecuteFunction extends BasicFunction {
		
    private static final Logger LOG = LogManager.getLogger(ExecuteFunction.class);
	
	final static FunctionSignature[] signatures = {
		new FunctionSignature(
			new QName( "execute", OracleModule.NAMESPACE_URI, OracleModule.PREFIX ),
	        "Executes a PL/SQL stored procedure passed as the second argument against an Oracle RDBMS specified by the connection " +
	        "in the first argument with the position of the result set cursor at the fourth argument. Stored procedure parameters " +
	        "may be passed in the third argument using an XML fragment with the following structure: " + 
	        "<oracle:parameters><orace:param oracle:pos=\"{param-position}\" oracle:type=\"{param-type}\"/>{param-value}" +
			"</oracle:parameters>.",
	        new SequenceType[] {
				new FunctionParameterSequenceType( "connection-handle", Type.INTEGER, Cardinality.EXACTLY_ONE, "The connection handle" ),
	            new FunctionParameterSequenceType( "plsql-statement", Type.STRING, Cardinality.EXACTLY_ONE, "The PL/SQL stored procedure"),
	            new FunctionParameterSequenceType( "parameters", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "Input parameters for the stored procedure (if any)" ),
	            new FunctionParameterSequenceType( "result-set-position", Type.INTEGER, Cardinality.ZERO_OR_ONE, "The position of the result set cursor"),
	            new FunctionParameterSequenceType( "make-node-from-column-name", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "The flag that indicates whether " + "" +
	            		"the xml nodes should be formed from the column names (in this mode a space in a Column Name will be replaced by an underscore!)" )
			},
			new FunctionReturnSequenceType( Type.NODE, Cardinality.ZERO_OR_ONE, "the results" )
		),
		new FunctionSignature(
				new QName( "execute", OracleModule.NAMESPACE_URI, OracleModule.PREFIX ),
				"Executes a PL/SQL stored procedure passed as the second argument against an Oracle RDBMS specified by the connection " +
		        "in the first argument with the position of the result set cursor at the fourth argument. Stored procedure parameters " +
		        "may be passed in the third argument using an XML fragment with the following structure: " + 
		        "<oracle:parameters><orace:param oracle:pos=\"{param-position}\" oracle:type=\"{param-type}\"/>{param-value}" +
				"</oracle:parameters>. An additional return code parameter is supported which can be used to specify an integer value returned " +
				"in the first position of the statement to indicate success of the PL/SQL call.",
		        new SequenceType[] {
					new FunctionParameterSequenceType( "connection-handle", Type.INTEGER, Cardinality.EXACTLY_ONE, "The connection handle" ),
		            new FunctionParameterSequenceType( "plsql-statement", Type.STRING, Cardinality.EXACTLY_ONE, "The PL/SQL stored procedure" ),
		            new FunctionParameterSequenceType( "parameters", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "Input parameters for the stored procedure (if any)" ),
		            new FunctionParameterSequenceType( "result-set-position", Type.INTEGER, Cardinality.EXACTLY_ONE, "The position of the result set cursor" ),
		            new FunctionParameterSequenceType( "make-node-from-column-name", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "The flag that indicates whether " +
		            		"the xml nodes should be formed from the column names (in this mode a space in a Column Name will be replaced by an underscore!)" ),
		            new FunctionParameterSequenceType( "return-code", Type.INTEGER, Cardinality.EXACTLY_ONE, "The expected function return code which indicates successful execution" )
				},
				new FunctionReturnSequenceType( Type.NODE, Cardinality.ZERO_OR_ONE, "the results" )
			)
	};
	
	private final static String PARAMETERS_ELEMENT_NAME = "parameters";
	private final static String PARAM_ELEMENT_NAME = "param";
    private final static String TYPE_ATTRIBUTE_NAME = "type";
    private final static String POSITION_ATTRIBUTE_NAME = "pos";
    
    private DateFormat xmlDf;

	/**
     * ExecuteFunction Constructor
     *
     * @param context
     *            The Context of the calling XQuery
     */
    public ExecuteFunction( XQueryContext context, FunctionSignature signature ) {
        super( context, signature );
        xmlDf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    }
    
	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		
		if(args.length == 5 || args.length == 6) {
			// was a connection and PL/SQL statement specified?
			if( args[0].isEmpty() || args[1].isEmpty() ) {
				return( Sequence.EMPTY_SEQUENCE );
			}
			
			// get the Connection
			long connectionUID = ((IntegerValue) args[0].itemAt(0)).getLong();
			Connection connection = SQLModule.retrieveConnection( context, connectionUID );
			
			if( connection == null ) {
				return( Sequence.EMPTY_SEQUENCE );
			}
			
			// get the PL/SQL statement
			String plSql = args[1].getStringValue();
			
			// get the input parameters (if any)
			Element parameters = null;
			if(!args[2].isEmpty()) {
				parameters = (Element)args[2].itemAt(0);
			}
			
			// was a result set position specified?
			int resultSetPos = 0;
			if(!args[3].isEmpty()) 	{
				resultSetPos = ((IntegerValue) args[3].itemAt(0)).getInt();
			} 
			
			boolean haveReturnCode = false;
			int plSqlSuccess = 1;	// default value of 1 for success
			if(args.length == 6) {
				// a return code is expected so what is the value indicating success?
				 plSqlSuccess = ((IntegerValue) args[5].itemAt(0)).getInt();
				 haveReturnCode = true;
			}

			CallableStatement statement = null;
			ResultSet resultSet = null;
			
			try {
				MemTreeBuilder builder = context.getDocumentBuilder();
				int iRow = 0;
				
				statement = connection.prepareCall(plSql);
				if(haveReturnCode)
				{
					statement.registerOutParameter(1, Types.NUMERIC);
				}			
				if(resultSetPos != 0)
				{
					statement.registerOutParameter(resultSetPos, OracleTypes.CURSOR);
				}
				if(!args[2].isEmpty())
				{
					setParametersOnPreparedStatement(statement, parameters);
				}
							
				statement.execute();
				
				if(haveReturnCode) {
					int returnCode = statement.getInt(1);
					if(returnCode != plSqlSuccess) {
						LOG.error(plSql + " failed [" + returnCode + "]");
						return( Sequence.EMPTY_SEQUENCE );
					}
				}			
				
				if(resultSetPos != 0) {
					// iterate through the result set building an XML document
					builder.startDocument();
						
					builder.startElement(new QName("result", OracleModule.NAMESPACE_URI, OracleModule.PREFIX), null);
					builder.addAttribute(new QName("count", null, null), String.valueOf(-1 ));
						
					resultSet = (ResultSet)statement.getObject(resultSetPos);
						
					ResultSetMetaData rsmd = resultSet.getMetaData();
					int iColumns = rsmd.getColumnCount();
						
					while (resultSet.next())
					{
						builder.startElement(new QName("row", OracleModule.NAMESPACE_URI, OracleModule.PREFIX), null);
		        		builder.addAttribute(new QName("index", null, null), String.valueOf(resultSet.getRow()));

						// get each tuple in the row
						for(int i = 0; i < iColumns; i++)
						{
							String columnName = rsmd.getColumnLabel(i + 1);
							if(columnName != null)
							{
								String colValue = resultSet.getString(i + 1);
									
								String colElement = "field";

								if(((BooleanValue)args[4].itemAt(0)).effectiveBooleanValue() && columnName.length() > 0)
								{
									// use column names as the XML node

									/**
									 * Spaces in column names are replaced with
									 * underscore's
									 */
										
									colElement = SQLUtils.escapeXmlAttr(columnName.replace(' ', '_'));
								} 
								
								builder.startElement(new QName(colElement, OracleModule.NAMESPACE_URI, OracleModule.PREFIX ), null);
								
								if(!((BooleanValue)args[4].itemAt(0)).effectiveBooleanValue() || columnName.length() <= 0)
								{
									String name;
									
									if(columnName.length() > 0) {
										name = SQLUtils.escapeXmlAttr(columnName);
									} else {
										name = "Column: " + String.valueOf(i + 1);
									}
									
									builder.addAttribute(new QName("name", null, null), name);
								}
								
								builder.addAttribute(new QName("type", OracleModule.NAMESPACE_URI, OracleModule.PREFIX), rsmd.getColumnTypeName(i + 1));
								builder.addAttribute(new QName("type", Namespaces.SCHEMA_NS, "xs"), Type.getTypeName(SQLUtils.sqlTypeToXMLType(rsmd.getColumnType(i + 1))));
								
								if(resultSet.wasNull())
								{
									// Add a null indicator attribute if the value was SQL Null
									builder.addAttribute(new QName("null", OracleModule.NAMESPACE_URI, OracleModule.PREFIX), "true");
								}
								
								if(colValue != null)
								{
									builder.characters(SQLUtils.escapeXmlText(colValue));
								}
								
								builder.endElement();
							}
						}

						builder.endElement();
							
						iRow++;
					}
					builder.endElement();
					
					// Change the root element count attribute to have the correct value
					
					NodeValue node = (NodeValue)builder.getDocument().getDocumentElement();

					Node count = node.getNode().getAttributes().getNamedItem("count");
					
					if(count != null)
					{
						count.setNodeValue(String.valueOf(iRow));
					}
					builder.endDocument();
					
					// return the XML result set
					return(node);
				}
				else
				{
					// there was no result set so just return an empty sequence
					return( Sequence.EMPTY_SEQUENCE );
				}
			}
			catch(SQLException sqle) {
				
				LOG.error("oracle:execute() Caught SQLException \"" + sqle.getMessage() + "\" for PL/SQL: \"" + plSql + "\"", sqle);
				
				//return details about the SQLException
				MemTreeBuilder builder = context.getDocumentBuilder();
				
				builder.startDocument();
				builder.startElement(new QName("exception", OracleModule.NAMESPACE_URI, OracleModule.PREFIX), null);
				
				boolean recoverable = false;
				if(sqle instanceof SQLRecoverableException)
				{
					recoverable = true;
				}
				builder.addAttribute(new QName("recoverable", null, null), String.valueOf(recoverable));
							
				builder.startElement(new QName("state", OracleModule.NAMESPACE_URI, OracleModule.PREFIX), null);
				String sqlState = sqle.getSQLState();
				if(sqlState != null) {
					builder.characters(sqle.getSQLState());
				}
				else {
					builder.characters("null");
				}
												
				builder.endElement();
				
				builder.startElement(new QName("message", OracleModule.NAMESPACE_URI, OracleModule.PREFIX), null);
				builder.characters(sqle.getMessage());
				builder.endElement();
				
				builder.startElement(new QName("stack-trace", OracleModule.NAMESPACE_URI, OracleModule.PREFIX), null);
				ByteArrayOutputStream bufStackTrace = new ByteArrayOutputStream();
				sqle.printStackTrace(new PrintStream(bufStackTrace));
				builder.characters(new String(bufStackTrace.toByteArray()));
				builder.endElement();
				
				builder.startElement(new QName("oracle", OracleModule.NAMESPACE_URI, OracleModule.PREFIX), null);
				builder.characters(SQLUtils.escapeXmlText(plSql));
				builder.endElement();
				
					int line = getLine();
					int column = getColumn();
					
					builder.startElement(new QName("xquery", OracleModule.NAMESPACE_URI, OracleModule.PREFIX), null);
					builder.addAttribute(new QName("line", null, null), String.valueOf(line));
					builder.addAttribute(new QName("column", null, null), String.valueOf(column));
					builder.endElement();

				builder.endElement();
				builder.endDocument();
				
				return (NodeValue)builder.getDocument().getDocumentElement();		
			}
			finally {
				release(connection, statement, resultSet);
			}
        }
        else {
        	throw new XPathException("Invalid number of arguments [" + args.length + "]");
        }
	}
	
	/**
	 * Release DB resources
	 * @param connection
	 * @param statement
	 * @param rs
	 */
	protected void release(Connection connection, Statement statement, ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} 
			catch (SQLException sqle) {
				LOG.error("Unable to close ResultSet: ", sqle);
			}
		}
		if (statement != null) {
			try {
				statement.close();
			} 
			catch (SQLException sqle) {
				LOG.error("Unable to close Statement: ", sqle);
			}
		}
		if (connection != null) {
			try {
				connection.close();
			} 
			catch (SQLException sqle) {
				LOG.error("Unable to close Connection: ", sqle);
			}
		}
	}
	
	private void setParametersOnPreparedStatement(Statement stmt, Element parametersElement) throws SQLException, XPathException {
		
        if(parametersElement.getNamespaceURI().equals(OracleModule.NAMESPACE_URI) && parametersElement.getLocalName().equals(PARAMETERS_ELEMENT_NAME))
        {
            NodeList paramElements = parametersElement.getElementsByTagNameNS(OracleModule.NAMESPACE_URI, PARAM_ELEMENT_NAME);

            for(int i = 0; i < paramElements.getLength(); i++)
            {
                Element param = ((Element)paramElements.item(i));
                String value = param.getFirstChild().getNodeValue();
                String type = param.getAttributeNS(OracleModule.NAMESPACE_URI, TYPE_ATTRIBUTE_NAME);
                int position = Integer.parseInt(param.getAttributeNS(OracleModule.NAMESPACE_URI, POSITION_ATTRIBUTE_NAME));
                try {
                	int sqlType = SQLUtils.sqlTypeFromString(type);
                	// What if SQL type is date???
                	if(sqlType == Types.DATE) {
                		Date date = xmlDf.parse(value);
                		((PreparedStatement)stmt).setTimestamp(position, new Timestamp(date.getTime()));
                	}
                	else {
                		((PreparedStatement)stmt).setObject(position, value, sqlType);
                	}
                }
                catch (ParseException pex) {
                	throw new XPathException(this, "Unable to parse date from value " + value + ". Expected format is YYYY-MM-DDThh:mm:ss.sss");
                }
                catch (Exception ex) {
                	throw new XPathException(this, "Failed to set stored procedure parameter at position " + position + " as " + type + " with value " + value);
                }
            }
        }
    }

}