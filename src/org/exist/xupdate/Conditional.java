/*
*  eXist Open Source Native XML Database
*  Copyright (C) 2001-04 Wolfgang M. Meier (wolfgang@exist-db.org) 
*  and others (see http://exist-db.org)
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
package org.exist.xupdate;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.exist.EXistException;
import org.exist.dom.DocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.util.LockException;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

/**
 * @author wolf
 */
public class Conditional extends Modification {

	private List modifications = new ArrayList(5);
	
	/**
	 * @param broker
	 * @param docs
	 * @param selectStmt
	 * @param namespaces
	 */
	public Conditional(DBBroker broker, DocumentSet docs, String selectStmt,
			Map namespaces) {
		super(broker, docs, selectStmt, namespaces);
	}

	public void addModification(Modification mod) {
		modifications.add(mod);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xupdate.Modification#process()
	 */
	public long process() throws PermissionDeniedException, LockException,
			EXistException, XPathException {
		LOG.debug("Processing xupdate:if ...");
		XQuery xquery = broker.getXQueryService();
		XQueryContext context = xquery.newContext();
		context.setBackwardsCompatibility(true);
		context.setStaticallyKnownDocuments(docs);
		declareNamespaces(context);
		CompiledXQuery compiled = xquery.compile(context, new StringReader(selectStmt));
		Sequence seq = xquery.execute(compiled, null);
		if(seq.effectiveBooleanValue()) {
			long mods = 0;
			for (int i = 0; i < modifications.size(); i++) {
				mods += ((Modification)modifications.get(i)).process();
				broker.flush();
			}
			LOG.debug(mods + " modifications processed.");
			return mods;
		} else
			return 0;
	}

	/* (non-Javadoc)
	 * @see org.exist.xupdate.Modification#getName()
	 */
	public String getName() {
		return "if";
	}

}
