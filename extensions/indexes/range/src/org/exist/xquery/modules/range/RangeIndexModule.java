/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.modules.range;

import org.exist.dom.QName;
import org.exist.indexing.range.RangeIndex;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionDef;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RangeIndexModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/range";
    public final static String PREFIX = "range";
    public final static String RELEASED_IN_VERSION = "eXist-2.2";

    public final static FunctionDef[] functions = {
        new FunctionDef(Lookup.signatures[0], Lookup.class),
        new FunctionDef(Lookup.signatures[1], Lookup.class),
        new FunctionDef(Lookup.signatures[2], Lookup.class),
        new FunctionDef(Lookup.signatures[3], Lookup.class),
        new FunctionDef(Lookup.signatures[4], Lookup.class),
        new FunctionDef(Lookup.signatures[5], Lookup.class),
        new FunctionDef(Lookup.signatures[6], Lookup.class),
        new FunctionDef(Lookup.signatures[7], Lookup.class),
        new FunctionDef(Lookup.signatures[8], Lookup.class),
        new FunctionDef(Lookup.signatures[9], Lookup.class),
        new FunctionDef(FieldLookup.signatures[0], FieldLookup.class),
        new FunctionDef(FieldLookup.signatures[1], FieldLookup.class),
        new FunctionDef(FieldLookup.signatures[2], FieldLookup.class),
        new FunctionDef(FieldLookup.signatures[3], FieldLookup.class),
        new FunctionDef(FieldLookup.signatures[4], FieldLookup.class),
        new FunctionDef(FieldLookup.signatures[5], FieldLookup.class),
        new FunctionDef(FieldLookup.signatures[6], FieldLookup.class),
        new FunctionDef(FieldLookup.signatures[7], FieldLookup.class),
        new FunctionDef(FieldLookup.signatures[8], FieldLookup.class),
        new FunctionDef(FieldLookup.signatures[9], FieldLookup.class),
        new FunctionDef(FieldLookup.signatures[10], FieldLookup.class),
        new FunctionDef(Optimize.signature, Optimize.class),
        new FunctionDef(IndexKeys.signatures[0], IndexKeys.class),
        new FunctionDef(IndexKeys.signatures[1], IndexKeys.class)
    };

    public final static Map<String, RangeIndex.Operator> OPERATOR_MAP = new HashMap<String, RangeIndex.Operator>();
    static {
        OPERATOR_MAP.put("eq", RangeIndex.Operator.EQ);
        OPERATOR_MAP.put("lt", RangeIndex.Operator.LT);
        OPERATOR_MAP.put("gt", RangeIndex.Operator.GT);
        OPERATOR_MAP.put("ge", RangeIndex.Operator.GE);
        OPERATOR_MAP.put("le", RangeIndex.Operator.LE);
        OPERATOR_MAP.put("ne", RangeIndex.Operator.NE);
        OPERATOR_MAP.put("starts-with", RangeIndex.Operator.STARTS_WITH);
        OPERATOR_MAP.put("ends-with", RangeIndex.Operator.ENDS_WITH);
        OPERATOR_MAP.put("contains", RangeIndex.Operator.CONTAINS);
        OPERATOR_MAP.put("matches", RangeIndex.Operator.MATCH);

    }

    protected final static class RangeIndexErrorCode extends ErrorCodes.ErrorCode {

        public RangeIndexErrorCode(String code, String description) {
            super(new QName(code, NAMESPACE_URI, PREFIX), description);
        }

    }

    public final static ErrorCodes.ErrorCode EXXQDYFT0001 = new RangeIndexErrorCode("EXXQDYFT0001", "Collation not " +
            "supported");

    public RangeIndexModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters, false);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "Functions to access the range index.";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
