/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.xquery.util;


import java.util.Iterator;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.xquery.Variable;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;

/** A place holder for static utility functions related to HTTP. 
 * @author jmv */
public class HTTPUtils {
    private final static Logger LOG = LogManager.getLogger(XQuery.class);

    /** Feature "Guess last modification time for an XQuery result"; 
     *  the HTTP header Last-Modified is filled with most recent time stamp among all 
     *  XQuery documents appearing in the actual response.
     *  Note however, that the actual response can be influenced, through tests in the query,
     *  by documents more recent.
	 *
	 * @param result the XQuery result to inspect
	 * @param context current context
	 */
    public static void addLastModifiedHeader(Sequence result,
			XQueryContext context) {
		try {
			final DocumentSet documentSet = result.getDocumentSet();
			long mostRecentDocumentTime = 0;
			for (final Iterator<DocumentImpl> i = documentSet.getDocumentIterator(); i.hasNext(); ) {
				final DocumentImpl doc = i.next();
				if (doc != null) {
					mostRecentDocumentTime = Math.max(doc.getMetadata().getLastModified(),
							mostRecentDocumentTime);
//					LOG.debug("getFileName: " + doc.getFileName() + ", "
//							+ doc.getLastModified());
				}
			}
			LOG.debug("mostRecentDocumentTime: " + mostRecentDocumentTime);

			if (mostRecentDocumentTime > 0) {

				final Optional<ResponseWrapper> maybeResponse = Optional.ofNullable(context.getHttpContext())
						.map(XQueryContext.HttpContext::getResponse);

				if (maybeResponse.isPresent()) {
					// have to take in account that if the header has allready been explicitely set
					// by the XQuery script, we should not modify it .
					final ResponseWrapper responseWrapper = maybeResponse.get();
					if (responseWrapper.getDateHeader("Last-Modified") == 0) {
						responseWrapper.setDateHeader("Last-Modified", mostRecentDocumentTime);
					}
				}
			}
		} catch (final Exception e) {
			LOG.debug(e.getMessage(), e);
		}
	}
    
    public static String printStackTraceHTML(Throwable e) {
        final StringBuilder buf = new StringBuilder();
        final StackTraceElement[] trace = e.getStackTrace();
        buf.append("<table id=\"javatrace\">");
        buf.append("<caption>Java Stack Trace:</caption>");
        buf.append("<tr><th>Class Name</th><th>Method Name</th><th>File Name</th><th>Line</th></tr>");
        for (int i = 0; i < trace.length && i < 20; i++) {
            buf.append("<tr>");
            buf.append("<td class=\"class\">").append(trace[i].getClassName()).append("</td>");
            buf.append("<td class=\"method\">").append(trace[i].getMethodName()).append("</td>");
            buf.append("<td class=\"file\">").append(trace[i].getFileName() == null ? "Unknown" : trace[i].getFileName()).append("</td>");
            buf.append("<td class=\"line\">");
            buf.append(trace[i].getLineNumber() < 0 ? "Unavailable" : Integer.toString(trace[i].getLineNumber()));
            buf.append("</td>");
            buf.append("</tr>");
        }
        buf.append("</table>");
        return buf.toString();
    }
}
