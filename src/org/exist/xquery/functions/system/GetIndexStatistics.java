/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
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
 * $Id$
 */
package org.exist.xquery.functions.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.storage.statistics.IndexStatistics;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

public class GetIndexStatistics extends BasicFunction {

    protected final static Logger logger = LogManager.getLogger(GetIndexStatistics.class);

    public final static FunctionSignature signature = new FunctionSignature(
        new QName("get-index-statistics", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
        "Internal function",
        null,
        new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the resource containing the index statistics"));

    public GetIndexStatistics(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final IndexStatistics index = (IndexStatistics) context.getBroker().getBrokerPool().
                getIndexManager().getIndexById(IndexStatistics.ID);
        if (index == null) {
            // module may not be enabled
            return Sequence.EMPTY_SEQUENCE;
        }

        final SAXAdapter adapter = new SAXAdapter(context);
        try {
            adapter.startDocument();
            index.toSAX(adapter);
            adapter.endDocument();
        } catch (final SAXException e) {
            throw new XPathException(this, "Error caught while retrieving statistics: " + e.getMessage(), e);
        }
        final DocumentImpl doc = (DocumentImpl) adapter.getDocument();
        return (NodeImpl) doc.getFirstChild();
    }
}
