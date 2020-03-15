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
package org.exist.xquery.modules.expathrepo;

import org.exist.dom.QName;
import org.exist.xquery.ErrorCodes.ErrorCode;

/**
 *
 * @author aretter
 */


public class EXPathErrorCode extends ErrorCode {
    
    /**
     * EXPATH specific errors [EXP][DY|SE|ST][nnnn]
     * 
     * EXP = EXPath
     * DY = Dynamic
     * DY = Dynamic
     * SE = Serialization
     * ST = Static
     * nnnn = number
     */
    public final static ErrorCode EXPDY001 = new EXPathErrorCode("EXPATH001", "Package not found.");
    public final static ErrorCode EXPDY002 = new EXPathErrorCode("EXPATH002", "Bad collection URI.");
    public final static ErrorCode EXPDY003 = new EXPathErrorCode("EXPATH003", "Permission denied.");
    public final static ErrorCode EXPDY004 = new EXPathErrorCode("EXPATH004", "Error in descriptor found.");
    public final static ErrorCode EXPDY005 = new EXPathErrorCode("EXPATH005", "Invalid repo URI");
    public final static ErrorCode EXPDY006 = new EXPathErrorCode("EXPATH006", "Failed to connect to public repo");
    // other error thrown from expath library
    public final static ErrorCode EXPDY007 = new EXPathErrorCode("EXPATH00", null);
    
    public final static String EXPATH_ERROR_NS = "http://expath.org/ns/error";
    public final static String EXPATH_ERROR_PREFIX = "experr";
    
    private EXPathErrorCode(String code, String description) {
        super(new QName(code, EXPATH_ERROR_NS, EXPATH_ERROR_PREFIX), description);
    }
}