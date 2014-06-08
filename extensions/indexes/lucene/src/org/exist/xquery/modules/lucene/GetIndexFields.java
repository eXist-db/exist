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
package org.exist.xquery.modules.lucene;

import java.util.List;

import org.exist.Database;
import org.exist.dom.QName;
import org.exist.indexing.IndexController;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.indexing.lucene.LuceneUtil;
import org.exist.storage.DBBroker;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

public class GetIndexFields extends BasicFunction {

    public final static FunctionSignature signatures[] = { 
        new FunctionSignature(new QName("get-index-fields", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX), 
        "The list of encoded QNames, which defined for lucene index", 
        null, 
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "Lucene fields' name")) 
    };

    public GetIndexFields(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        
        DBBroker broker = context.getBroker();
        Database db = broker.getDatabase();
        
        IndexController indexController = broker.getIndexController();
        
        // Get the lucene worker
        LuceneIndexWorker indexWorker = 
            (LuceneIndexWorker) indexController.getWorkerByIndexId(LuceneIndex.ID);
        
        List<QName> qnames = indexWorker.getDefinedIndexes(null);
        
        final ValueSequence resultSeq = new ValueSequence();
        
        for (QName qname : qnames) {
            final String field = LuceneUtil.encodeQName(qname, db.getSymbols());
            
            resultSeq.add(new StringValue(field));
        }
        
        return resultSeq;
    }

}
