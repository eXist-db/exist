package org.exist.xquery.modules.sql;

import java.sql.Types;

import org.exist.xquery.value.Type;

/**
 * Utility class for converting to/from SQL types and escaping XML text and attributes.
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author <a href="mailto:robert.walpole@metoffice.gov.uk">Robert Walpole</a>
 * @serial 2010-07-23
 * @version 1.0
 * 
 */
public final class SQLUtils {
	
	public static int sqlTypeFromString( String sqlType )
    {
        sqlType = sqlType.toUpperCase();

        switch (sqlType) {
            case "ARRAY":
                return (Types.ARRAY);
            case "BIGINT":
                return (Types.BIGINT);
            case "BINARY":
                return (Types.BINARY);
            case "BIT":
                return (Types.BIT);
            case "BLOB":
                return (Types.BLOB);
            case "BOOLEAN":
                return (Types.BOOLEAN);
            case "CHAR":
                return (Types.CHAR);
            case "CLOB":
                return (Types.CLOB);
            case "DECIMAL":
                return (Types.DECIMAL);
            case "DOUBLE":
                return (Types.DOUBLE);
            case "FLOAT":
                return (Types.FLOAT);
            case "LONGVARCHAR":
                return (Types.LONGVARCHAR);
            case "NUMERIC":
                return (Types.NUMERIC);
            case "SMALLINT":
                return (Types.SMALLINT);
            case "TINYINT":
                return (Types.TINYINT);
            case "INTEGER":
                return (Types.INTEGER);
            case "VARCHAR":
                return (Types.VARCHAR);
            case "SQLXML":
                return Types.SQLXML;
            case "TIMESTAMP":
                return Types.TIMESTAMP;
            default:
                return (Types.VARCHAR); //default
        }
    }

    /**
     * Converts a SQL data type to an XML data type.
     *
     * @param   sqlType  The SQL data type as specified by JDBC
     *
     * @return  The XML Type as specified by eXist
     */
    public static int sqlTypeToXMLType( int sqlType )
    {
        switch (sqlType) {

            case Types.ARRAY: {
                return (Type.NODE);
            }

            case Types.BIGINT: {
                return (Type.INT);
            }

            case Types.BINARY: {
                return (Type.BASE64_BINARY);
            }

            case Types.BIT: {
                return (Type.INT);
            }

            case Types.BLOB: {
                return (Type.BASE64_BINARY);
            }

            case Types.BOOLEAN: {
                return (Type.BOOLEAN);
            }

            case Types.CHAR: {
                return (Type.STRING);
            }

            case Types.CLOB: {
                return (Type.STRING);
            }

            case Types.DECIMAL: {
                return (Type.DECIMAL);
            }

            case Types.DOUBLE: {
                return (Type.DOUBLE);
            }

            case Types.FLOAT: {
                return (Type.FLOAT);
            }

            case Types.LONGVARCHAR: {
                return (Type.STRING);
            }

            case Types.NUMERIC: {
                return (Type.NUMBER);
            }

            case Types.SMALLINT: {
                return (Type.INT);
            }

            case Types.TINYINT: {
                return (Type.INT);
            }

            case Types.INTEGER: {
                return (Type.INTEGER);
            }

            case Types.VARCHAR: {
                return (Type.STRING);
            }

            case Types.SQLXML: {
                return (Type.NODE);
            }

            case Types.TIMESTAMP: {
                return Type.DATE_TIME;
            }

            default: {
                return (Type.ANY_TYPE);
            }
        }
    }

    public static String escapeXmlText( String text )
    {
        String work = null;

        if( text != null ) {
            work = text.replaceAll( "\\&", "\\&amp;" );
            work = work.replaceAll( "<", "\\&lt;" );
            work = work.replaceAll( ">", "\\&gt;" );
        }

        return( work );
    }

    public static String escapeXmlAttr( String attr )
    {
        String work = null;

        if( attr != null ) {
            work = escapeXmlText( attr );
            work = work.replaceAll( "'", "\\&apos;" );
            work = work.replaceAll( "\"", "\\&quot;" );
        }

        return( work );
    }

}