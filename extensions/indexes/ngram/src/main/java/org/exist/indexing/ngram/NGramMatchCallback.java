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
package org.exist.indexing.ngram;

import org.exist.dom.persistent.NodeProxy;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.XPathException;
import org.xml.sax.SAXException;

/**
 * Callback interface used by the NGram {@link org.exist.indexing.MatchListener} to report matching
 * text sequences. Pass to
 * {@link NGramIndexWorker#getMatchListener(org.exist.storage.DBBroker, org.exist.dom.persistent.NodeProxy, NGramMatchCallback)}
 * to get informed of matches.
 */
public interface NGramMatchCallback {

    /**
     * Called by the NGram {@link org.exist.indexing.MatchListener} whenever it encounters
     * a match object while traversing the node tree.
     *
     * @param receiver the receiver to which the MatchListener is currently writing.
     * @param matchingText the matching text sequence
     * @param node the text node containing the match
     *
     * @throws XPathException if a query error occurs
     * @throws SAXException if a parse error occurs
     */
    public void match(Receiver receiver, String matchingText, NodeProxy node) throws XPathException, SAXException;
}
