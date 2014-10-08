/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.versioning.svn.xquery;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Iterator;
import java.util.Map;

public class SVNLog extends AbstractSVNFunction {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("log", SVNModule.NAMESPACE_URI, SVNModule.PREFIX),
			"Retrieves the log entries from a subversion repository." +
					"\n\nThe return is formatted as follows:\n" +
                    "<log uri=\"\" start=\"\">\n" +
                    "    <entry rev=\"\" author=\"\" date=\"\">\n" +
                    "        <message></message>\n" +
                    "        <paths>\n" +
                    "            <path revtype=\"M\"></path>\n" +
                    "        </paths>\n" +
                    "    </entry>\n" +
                    "</log>\n\n" +
                    "Revtype values are 'A' (item added), 'D' (item deleted), 'M' (item modified), or 'R' (item replaced).",
			new SequenceType[] {
				SVN_URI,
				LOGIN,
				PASSWORD,
                new FunctionParameterSequenceType("start-revision", Type.INTEGER, Cardinality.ZERO_OR_ONE, "The subversion revision to start from.  If empty, then start from the beginning."),
                new FunctionParameterSequenceType("end-revision", Type.INTEGER, Cardinality.ZERO_OR_ONE, "The subversion revision to end with.  If empty, then end with the HEAD revision")
            },
			new FunctionParameterSequenceType("log", Type.ELEMENT, Cardinality.EXACTLY_ONE, "a sequence containing the log entries"));

    private final static QName LOG_ELEMENT = new QName("log", "", "");
    private final static QName ENTRY_ELEMENT = new QName("entry", "", "");
    private final static QName MESSAGE_ELEMENT = new QName("message", "", "");
    private final static QName PATHS_ELEMENT = new QName("paths", "", "");
    private final static QName PATH_ELEMENT = new QName("path", "", "");
    private final static AttributesImpl EMPTY_ATTRIBS = new AttributesImpl();

    public SVNLog(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        String uri = args[0].getStringValue();
        try {
            SVNRepository repo =
                    SVNRepositoryFactory.create(SVNURL.parseURIDecoded(uri));
            ISVNAuthenticationManager authManager =
                    SVNWCUtil.createDefaultAuthenticationManager(args[1].getStringValue(), args[2].getStringValue());
            repo.setAuthenticationManager(authManager);

            long startRevision = 0;
            long endRevision = -1; // = HEAD
            if (!args[3].isEmpty())
                startRevision = ((IntegerValue)args[3].itemAt(0)).getLong();
            if (!args[4].isEmpty())
                endRevision = ((IntegerValue)args[4].itemAt(0)).getLong();
            MemTreeBuilder builder = context.getDocumentBuilder();
            AttributesImpl attribs = new AttributesImpl();
            attribs.addAttribute("", "uri", "uri", "CDATA", uri);
            attribs.addAttribute("", "start", "start", "CDATA", Long.toString(startRevision));
            int nodeNr = builder.startElement(LOG_ELEMENT, attribs);
            LogHandler handler = new LogHandler(builder);
            repo.log(new String[0], startRevision, endRevision, true, false, handler);
            builder.endElement();
		    return builder.getDocument().getNode(nodeNr);
        } catch (SVNException e) {
            throw new XPathException(this, e.getMessage(), e);
        }
    }

    private static class LogHandler implements ISVNLogEntryHandler {

        MemTreeBuilder builder;

        private LogHandler(MemTreeBuilder builder) {
            this.builder = builder;
        }

        public void handleLogEntry(SVNLogEntry entry) throws SVNException {
            AttributesImpl attribs = new AttributesImpl();
            attribs.addAttribute("", "rev", "rev", "CDATA", Long.toString(entry.getRevision()));
            attribs.addAttribute("", "author", "author", "CDATA", entry.getAuthor());
            String date = null;
            try {
                date = new DateTimeValue(entry.getDate()).getStringValue();
            } catch (XPathException e) {
            }
            attribs.addAttribute("", "date", "date", "CDATA", date);
            builder.startElement(ENTRY_ELEMENT, attribs);
            builder.startElement(MESSAGE_ELEMENT, EMPTY_ATTRIBS);
            builder.characters(entry.getMessage());
            builder.endElement();
            builder.startElement(PATHS_ELEMENT, EMPTY_ATTRIBS);
            Map paths = entry.getChangedPaths();
            Iterator iterator = paths.entrySet().iterator();
            while (iterator.hasNext() ) {
                Map.Entry e = (Map.Entry) iterator.next();
                SVNLogEntryPath svnLogEntryPath = (SVNLogEntryPath) e.getValue();
                AttributesImpl pathAttribs = new AttributesImpl();
                pathAttribs.addAttribute("", "revtype", "revtype", "CDATA", String.valueOf(svnLogEntryPath.getType()));
//                pathAttribs.addAttribute("", "copypath", "copypath", "CDATA", svnLogEntryPath.getCopyPath());
//                pathAttribs.addAttribute("", "copyrev", "copyrev", "CDATA", String.valueOf(svnLogEntryPath.getCopyRevision()));
                builder.startElement(PATH_ELEMENT, pathAttribs);
                builder.characters(e.getKey().toString());
                builder.endElement();
            }
            builder.endElement();
            builder.endElement();
        }
    }
}
