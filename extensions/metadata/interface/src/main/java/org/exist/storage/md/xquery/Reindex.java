/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
 */
package org.exist.storage.md.xquery;

import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import static org.exist.storage.md.MDStorageManager.NAMESPACE_URI;
import static org.exist.storage.md.MDStorageManager.PREFIX;
import static org.exist.storage.lock.Lock.READ_LOCK;

/**
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author Casey Jordan <casey.jordan@jorsek.com>
 */
public class Reindex extends BasicFunction {

	private static final QName NAME = new QName("reindex", NAMESPACE_URI, PREFIX);
	private static final String DESCRIPTION = "Reindex collection or document.";
    private static final SequenceType RETURN = new SequenceType(Type.EMPTY, Cardinality.ZERO);

    public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			NAME,
			DESCRIPTION,
			new SequenceType[] {
				 new FunctionParameterSequenceType("resource-url", Type.STRING, Cardinality.ONE_OR_MORE, "The resource's urls.")
			},
			RETURN
		)
	};

	/**
	 * @param context
	 */
	public Reindex(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        DBBroker broker = getContext().getBroker();

        try (Txn txn = broker.beginTx()) {
            Item next;
            for (final SequenceIterator i = args[0].unorderedIterator(); i.hasNext(); ) {
                next = i.nextItem();

                XmldbURI uri = XmldbURI.create(next.getStringValue());

                DocumentImpl doc = broker.getXMLResource(uri, READ_LOCK);

                try {

                    broker.reindexXMLResource(txn, doc);

                } finally {
                    if (doc != null) {
                        doc.getUpdateLock().release(READ_LOCK);
                    }
                }

            }

            txn.success();
        } catch (Exception e) {
            throw new XPathException(this, e);
        }

        try {
            broker.getDatabase().getIndexManager().sync();
        } catch (DBException e) {
            throw new XPathException(this, e);
        }

        return Sequence.EMPTY_SEQUENCE;
	}
}
