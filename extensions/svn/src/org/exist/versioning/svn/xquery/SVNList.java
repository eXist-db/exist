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

import java.util.List;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.versioning.svn.WorkingCopy;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Reports the directory entry, and possibly children, for url at revision. 
 * 
 * @author <a href="mailto:amir.akhmedov@gmail.com">Amir Akhmedov</a>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class SVNList extends AbstractSVNFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("list", SVNModule.NAMESPACE_URI, SVNModule.PREFIX),
			"Reports the directory entry, and possibly children, for url at revision.",
			new SequenceType[] {
				SVN_URI,
                LOGIN,
                PASSWORD
            },
			new FunctionParameterSequenceType("list", Type.ELEMENT, Cardinality.EXACTLY_ONE, "a sequence containing the list entries"));

    public SVNList(XQueryContext context) {
		super(context, signature);
	}

    private final static QName ENTRIES_ELEMENT = new QName("entries", "", "");
    private final static QName ENTRY_ELEMENT = new QName("entry", "", "");
    private final static AttributesImpl EMPTY_ATTRIBS = new AttributesImpl();

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        String uri = args[0].getStringValue();
        String user = args[1].getStringValue();
        String password = args[2].getStringValue();

		WorkingCopy wc = new WorkingCopy(user, password);
		
		List<SVNDirEntry> entries;
		try {
			entries = wc.list(SVNURL.parseURIEncoded(uri), SVNRevision.HEAD, SVNRevision.HEAD, false, SVNDepth.IMMEDIATES, 1);
			MemTreeBuilder builder = context.getDocumentBuilder();
			int nodeNr = builder.startElement(ENTRIES_ELEMENT, EMPTY_ATTRIBS);
			
			for (SVNDirEntry entry : entries) {
				String path = entry.getRelativePath();
				if(!path.equals("")) {
					AttributesImpl attributes = new AttributesImpl();
					if (entry.getKind() == SVNNodeKind.DIR) {
						attributes.addAttribute("", "type", "", "CDATA", "directory");
		                builder.startElement(ENTRY_ELEMENT, attributes);
		                builder.characters(entry.getName());
		                builder.endElement();
					}else if (entry.getKind() == SVNNodeKind.FILE) {
						attributes.addAttribute("", "type", "", "CDATA", "file");
		                builder.startElement(ENTRY_ELEMENT, attributes);
		                builder.characters(entry.getName());
		                builder.endElement();
					}
				}
			}
			builder.endElement();
			return builder.getDocument().getNode(nodeNr);
		} catch (SVNException e) {
            throw new XPathException(this, e.getMessage(), e);
		}
	}

}
