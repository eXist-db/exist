/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.sql;

import java.sql.Types;

import org.exist.xquery.value.Type;

/**
 * Utility class for converting to/from SQL types and escaping XML text and attributes.
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author <a href="mailto:robert.walpole@metoffice.gov.uk">Robert Walpole</a>
 * @version 1.0
 * @serial 2010-07-23
 */
public final class SQLUtils {

    public static int sqlTypeFromString(String sqlType) {
        sqlType = sqlType.toUpperCase();

        return switch (sqlType) {
            case "ARRAY" -> (Types.ARRAY);
            case "BIGINT" -> (Types.BIGINT);
            case "BINARY" -> (Types.BINARY);
            case "BIT" -> (Types.BIT);
            case "BLOB" -> (Types.BLOB);
            case "BOOLEAN" -> (Types.BOOLEAN);
            case "CHAR" -> (Types.CHAR);
            case "CLOB" -> (Types.CLOB);
            case "DECIMAL" -> (Types.DECIMAL);
            case "DOUBLE" -> (Types.DOUBLE);
            case "FLOAT" -> (Types.FLOAT);
            case "LONGVARCHAR" -> (Types.LONGVARCHAR);
            case "NUMERIC" -> (Types.NUMERIC);
            case "SMALLINT" -> (Types.SMALLINT);
            case "TINYINT" -> (Types.TINYINT);
            case "INTEGER" -> (Types.INTEGER);
            case "VARCHAR" -> (Types.VARCHAR);
            case "SQLXML" -> Types.SQLXML;
            case "TIMESTAMP" -> Types.TIMESTAMP;
            default -> (Types.VARCHAR); //default
        };
    }

    /**
     * Converts a SQL data type to an XML data type.
     *
     * @param sqlType The SQL data type as specified by JDBC
     * @return The XML Type as specified by eXist
     */
    public static int sqlTypeToXMLType(int sqlType) {
        return switch (sqlType) {
            case Types.ARRAY -> (Type.NODE);
            case Types.BIGINT -> (Type.INT);
            case Types.BINARY -> (Type.BASE64_BINARY);
            case Types.BIT -> (Type.INT);
            case Types.BLOB -> (Type.BASE64_BINARY);
            case Types.BOOLEAN -> (Type.BOOLEAN);
            case Types.CHAR -> (Type.STRING);
            case Types.CLOB -> (Type.STRING);
            case Types.DECIMAL -> (Type.DECIMAL);
            case Types.DOUBLE -> (Type.DOUBLE);
            case Types.FLOAT -> (Type.FLOAT);
            case Types.LONGVARCHAR -> (Type.STRING);
            case Types.NUMERIC -> (Type.NUMERIC);
            case Types.SMALLINT -> (Type.INT);
            case Types.TINYINT -> (Type.INT);
            case Types.INTEGER -> (Type.INTEGER);
            case Types.VARCHAR -> (Type.STRING);
            case Types.SQLXML -> (Type.NODE);
            case Types.TIMESTAMP -> Type.DATE_TIME;
            default -> (Type.ANY_TYPE);
        };
    }

    public static String escapeXmlText(String text) {
        String work = null;

        if (text != null) {
            work = text.replaceAll("\\&", "\\&amp;");
            work = work.replaceAll("<", "\\&lt;");
            work = work.replaceAll(">", "\\&gt;");
        }

        return (work);
    }

    public static String escapeXmlAttr(String attr) {
        String work = null;

        if (attr != null) {
            work = escapeXmlText(attr);
            work = work.replaceAll("'", "\\&apos;");
            work = work.replaceAll("\"", "\\&quot;");
        }

        return (work);
    }
}
