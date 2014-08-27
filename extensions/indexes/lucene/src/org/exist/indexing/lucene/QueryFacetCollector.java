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
package org.exist.indexing.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.util.FixedBitSet;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentSet;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class QueryFacetCollector extends Collector {

    protected final FacetsCollector fc = new FacetsCollector();

    protected Scorer scorer;

    protected AtomicReaderContext context;
    protected AtomicReader reader;
    protected NumericDocValues docIdValues;

    protected final DocumentSet docs;

    protected int totalHits;

    protected DefaultDocumentSet docbits;

    protected QueryFacetCollector( DocumentSet docs ) {
        
        this.docs = docs;

        docbits = new DefaultDocumentSet(1031);
    }

    @Override
    public void setScorer(Scorer scorer) throws IOException {

        fc.setScorer( scorer );

        this.scorer = scorer;
    }

    @Override
    public void setNextReader(AtomicReaderContext atomicReaderContext) throws IOException {

        fc.setNextReader( atomicReaderContext );
        
    	reader = atomicReaderContext.reader();
        docIdValues = reader.getNumericDocValues(LuceneUtil.FIELD_DOC_ID);

        context = atomicReaderContext;
    }
    
    protected abstract SearchCallback<?> getCallback();

    @Override
    public boolean acceptsDocsOutOfOrder() {
        return false;
    }

    @Override
    public void collect(int doc) throws IOException {
        fc.collect( doc );
    }

    public Facets facets(TaxonomyReader taxoReader, FacetsConfig config) throws IOException {
        return new FastTaxonomyFacetCounts(taxoReader, config, fc);
    }
}

