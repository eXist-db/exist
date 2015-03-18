/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.util.sanity;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class for sanity checks. Provides static methods ASSERT, THROW_ASSERT
 * which can be used in the code to react to unexpected conditions. {@link #ASSERT(boolean)}
 * logs a stack trace to the log4j log output. {@link #THROW_ASSERT(boolean)}
 * throws an additional runtime exception.
 * 
 * @author wolf
 */
public class SanityCheck {

    private final static Logger LOG = LogManager.getLogger(SanityCheck.class);
    
    public final static void ASSERT(boolean mustBeTrue) {
        if (!mustBeTrue) {
            final AssertFailure failure = new AssertFailure("ASSERT FAILED");
            showTrace(failure);
        }
    }

    public final static void ASSERT(boolean mustBeTrue, String failureMsg) {
        if (!mustBeTrue) {
            final AssertFailure failure = new AssertFailure("ASSERT FAILED: " + failureMsg);
            showTrace(failure);
        }
    }
    
    public final static void THROW_ASSERT(boolean mustBeTrue) {
        if (!mustBeTrue) {
            final AssertFailure failure = new AssertFailure("ASSERT FAILED");
            showTrace(failure);
            throw failure;
        }
    }
    
    public final static void THROW_ASSERT(boolean mustBeTrue, String failureMsg) {
        if (!mustBeTrue) {
            final AssertFailure failure = new AssertFailure("ASSERT FAILED: " + failureMsg);
            showTrace(failure);
            throw failure;
        }
    }
    
    public final static void TRACE(String msg) {
        final AssertFailure failure = new AssertFailure("TRACE: " + msg);
        showTrace(failure);
    }
    
    public final static void PRINT_STACK(int level) {
        final StackTraceElement elements[] = new Exception("Trace").getStackTrace();
        if (level > elements.length)
            {level = elements.length;}
        final StringBuilder buf = new StringBuilder();
        for (int i = 1; i < level; i++) {
            buf.append('\n');
            buf.append(elements[i].toString());
        }
        LOG.debug(buf.toString());
    }
    
    private final static void showTrace(AssertFailure failure) {
        final StringWriter sout = new StringWriter();
        final PrintWriter out = new PrintWriter(sout);
        out.println("Stacktrace:");
        failure.printStackTrace(out);
        LOG.warn(sout.toString());
    }
}