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
import org.exist.util.io.Resource;
import org.exist.versioning.svn.WorkingCopy;
import org.exist.versioning.svn.wc.ISVNInfoHandler;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Collects information on local path(s). Like 'svn info (-R)' command.
 * 
 * @author <a href="mailto:amir.akhmedov@gmail.com">Amir Akhmedov</a>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class SVNInfo extends AbstractSVNFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("info", SVNModule.NAMESPACE_URI, SVNModule.PREFIX), "Collects information on local path(s). Like 'svn info (-R)' command.",
			new SequenceType[] {
				DB_PATH
            },
            new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE, ""));

	public SVNInfo(XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        
		WorkingCopy wc = new WorkingCopy("", "");
        
        String uri = args[0].getStringValue();
       
        Resource wcDir = new Resource(uri);
    	try {
    		
            MemTreeBuilder builder = context.getDocumentBuilder();
    		
            AttributesImpl attribs = new AttributesImpl();
            attribs.addAttribute("", "uri", "uri", "CDATA", uri);
            //attribs.addAttribute("", "start", "start", "CDATA", Long.toString(startRevision));

            int nodeNr = builder.startElement(INFO_ELEMENT, attribs);
            
            wc.showInfo(wcDir, SVNRevision.WORKING, true, new InfoHandler(builder));
            
            builder.endElement();
		    return builder.getDocument().getNode(nodeNr);
		    
		} catch (SVNException svne) {
			throw new XPathException(this,
					"error while collecting info for the location '"
                    + uri + "'", svne);
		}
	}

    private static class InfoHandler implements ISVNInfoHandler {
    	
    	MemTreeBuilder builder;
    	
    	public InfoHandler(MemTreeBuilder builder) {
    		this.builder = builder;
		}

		@Override
		public void handleInfo(org.exist.versioning.svn.wc.SVNInfo info) throws SVNException {
            AttributesImpl attribs = new AttributesImpl();
            attribs.addAttribute("", "local-path", "local-path", "CDATA", info.getFile().getPath());
            attribs.addAttribute("", "URL", "URL", "CDATA", info.getURL().toString());

            
	        if (info.isRemote() && info.getRepositoryRootURL() != null) {
	            attribs.addAttribute("", "Root-URL", "Root-URL", "CDATA", info.getRepositoryRootURL().toString());
	        }
	        if(info.getRepositoryUUID() != null){
	            attribs.addAttribute("", "Repository-UUID", "Repository-UUID", "CDATA", info.getRepositoryUUID());
	        }
            attribs.addAttribute("", "Revision", "Revision", "CDATA", String.valueOf( info.getRevision().getNumber() ) );
            attribs.addAttribute("", "Node-Kind", "Node-Kind", "CDATA", info.getKind().toString() );
	        if(!info.isRemote()){
	            attribs.addAttribute("", "Schedule", "Schedule", "CDATA", (info.getSchedule() != null ? info.getSchedule() : "normal") );
	        }
            attribs.addAttribute("", "Last-Changed-Author", "Last-Changed-Author", "CDATA", info.getAuthor() );
            attribs.addAttribute("", "Last-Changed-Revision", "Last-Changed-Revision", "CDATA", String.valueOf( info.getCommittedRevision().getNumber() ) );
            attribs.addAttribute("", "Last-Changed-Date", "Last-Changed-Date", "CDATA", info.getCommittedDate().toString() );
	        if (info.getPropTime() != null) {
	            attribs.addAttribute("", "Properties-Last-Updated", "Properties-Last-Updated", "CDATA", info.getPropTime().toString() );
	        }
	        if (info.getKind() == SVNNodeKind.FILE && info.getChecksum() != null) {
	            if (info.getTextTime() != null) {
		            attribs.addAttribute("", "Text-Last-Updated", "Text-Last-Updated", "CDATA", info.getTextTime().toString() );
	            }
	            attribs.addAttribute("", "Checksum", "Checksum", "CDATA", info.getChecksum() );
	        }
	        if (info.getLock() != null) {
	            if (info.getLock().getID() != null) {
		            attribs.addAttribute("", "Lock-Token", "Lock-Token", "CDATA", info.getLock().getID() );
	            }
	            attribs.addAttribute("", "Lock-Owner", "Lock-Owner", "CDATA", info.getLock().getOwner() );
	            attribs.addAttribute("", "Lock-Created", "Lock-Created", "CDATA", info.getLock().getCreationDate().toString() );
	            if (info.getLock().getExpirationDate() != null) {
		            attribs.addAttribute("", "Lock-Expires", "Lock-Expires", "CDATA", info.getLock().getExpirationDate().toString() );
	            }
	            if (info.getLock().getComment() != null) {
		            attribs.addAttribute("", "Lock-Comment", "Lock-Comment", "CDATA", info.getLock().getComment() );
	            }
	        }
			builder.startElement(INFO_ELEMENT, attribs);
			builder.endElement();
 		}
    }

    private final static QName INFO_ELEMENT = new QName("info", "", "");
}