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
package org.exist.xquery.functions.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.storage.statistics.IndexStatistics;
import org.exist.storage.statistics.IndexStatisticsWorker;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class UpdateStatistics extends BasicFunction {

	protected final static Logger logger = LogManager.getLogger(UpdateStatistics.class);

    public final static FunctionSignature signature = new FunctionSignature(
        new QName("update-statistics", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
        "This function is part of the unfinished index statistics module, which is not " +
        "yet usable in a normal eXist setup. update-statistics rebuilds index statistics " +
        "for the entire database.",
        null,
        new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE));

    public UpdateStatistics(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final IndexStatisticsWorker index = (IndexStatisticsWorker)
            context.getBroker().getIndexController().getWorkerByIndexId(IndexStatistics.ID);
        if (index != null) {
            try (final Txn transaction = context.getBroker().continueOrBeginTransaction()) {
                index.updateIndex(context.getBroker(), transaction);
                transaction.commit();
            } catch (final TransactionException e) {
                throw new XPathException(this, e.getMessage(), e);
            }
        } else {
        	logger.error("The module may not be enabled!");
        }
        return Sequence.EMPTY_SEQUENCE;
    }
}
