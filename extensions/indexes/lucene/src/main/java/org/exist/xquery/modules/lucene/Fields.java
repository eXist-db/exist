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
package org.exist.xquery.modules.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.exist.collections.Collection;
import org.exist.dom.QName;
import org.exist.indexing.lucene.AbstractFieldConfig;
import org.exist.indexing.lucene.LuceneConfig;
import org.exist.indexing.lucene.LuceneFacetConfig;
import org.exist.indexing.lucene.LuceneFieldConfig;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexConfig;
import org.exist.indexing.lucene.LuceneVectorFieldConfig;
import org.exist.indexing.lucene.analyzers.MetaAnalyzer;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/**
 * {@code ft:fields($scope)} — introspects the Lucene index configuration in scope and returns the
 * configured fields and facets, one {@code map} per entry. It is the schema-discovery companion to
 * {@code ft:query-scope}/{@code ft:search-scope}: where they search, this describes what is searchable.
 *
 * <p>Each result map describes a configured field, facet, or vector field:</p>
 * <pre>
 * map { "collection": xs:string,            (: the collection whose config this entry resolved from :)
 *       "field": xs:string,                 (: the field name / facet dimension / vector field name :)
 *       "element": xs:string,               (: the element the index is defined on (or named-index name) :)
 *       "kind": "field" | "facet" | "vector",
 *       "analyzer": xs:string,              (: effective analyzer (fields only) :)
 *       "type": xs:string,                  (: declared XDM type, e.g. "xs:string" (fields only) :)
 *       "returnable": xs:boolean,           (: whether the field value is stored (fields only) :)
 *       "dimension": xs:integer,            (: vector dimension (vector fields only) :)
 *       "similarity": xs:string,            (: "cosine" | "euclidean" | "dot_product" (vector fields only) :)
 *       "model": xs:string }                (: embedding model id, when the vector field embeds text :)
 * </pre>
 *
 * <p>It reflects the <em>resolved</em> configuration for {@code $scope} (handling collection inheritance,
 * analyzer-id resolution, and merged qname/wildcard/named indexes), so callers do not have to parse
 * {@code collection.xconf} themselves. It <b>aggregates across every collection in scope</b> the way
 * {@code ft:search-scope} aggregates documents: a scope spanning several producer collections returns
 * the union of their configured fields/facets (one record per occurrence; the caller dedups). The
 * {@code collection} key reports which collection each entry resolved from, so a field defined in a
 * parent collection and overridden in a sub-collection is distinguishable by its differing paths.</p>
 *
 * <p>Visibility is <b>gated at collection-read granularity</b>: the scope is resolved through
 * permission-checked collection access, so a caller only discovers configurations for collections it may
 * read (sub-collections it cannot read are skipped). Within a readable collection the full configured
 * field set is returned; any finer, per-caller field-visibility policy (e.g. exposing some fields only to
 * certain groups) is the application layer's job, as eXist has no field-level permission primitive.</p>
 */
public class Fields extends BasicFunction {

    private static final FunctionParameterSequenceType FS_PARAM_SCOPE =
            new FunctionParameterSequenceType("scope", Type.STRING, Cardinality.ZERO_OR_MORE,
                    "Collection (or document) URIs whose Lucene index configuration to describe. "
                            + "Collection URIs are resolved recursively.");
    private static final FunctionReturnSequenceType FS_RETURN =
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_MORE,
                    "One map per configured field, facet, or vector field in scope: "
                            + "{ collection, field, element, kind, analyzer, type, returnable } for fields/facets, "
                            + "plus { dimension, similarity, model } for vector fields.");

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("fields", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                    "Returns the configured Lucene fields, facets, and vector fields in scope, one map per entry "
                            + "(collection, field name, element, kind, analyzer, type, returnable; vector fields also "
                            + "carry dimension, similarity, and model). Only configurations for collections the caller "
                            + "may read are returned; any finer per-caller field-visibility policy is the "
                            + "application's responsibility.",
                    new SequenceType[]{FS_PARAM_SCOPE},
                    FS_RETURN)
    };

    public Fields(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        // Resolve the scope to its collection set only (not its documents): configuration introspection
        // needs the collection hierarchy, so the cost is bounded by the number of collections rather
        // than documents. The resolution is permission-checked at collection-read granularity, so a
        // caller only discovers configurations for collections it may read.
        final Set<XmldbURI> collections = LuceneScope.resolveScopeCollections(this, args[0]);
        if (collections.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // Aggregate across every collection in scope. A $scope can span collections with different (or
        // no) configs — and LuceneIndexWorker.getLuceneConfig() returns only the first one it finds, so
        // it would silently drop the rest. Union each distinct collection config so ft:fields($scope)
        // discovers the full field set, the way ft:search-scope aggregates documents across the scope.
        // LuceneConfig identity dedup means an inherited config is reported once, attributed to the
        // collection it is defined on (parents are visited before children), while a sub-collection that
        // overrides the config carries a distinct LuceneConfig and is reported under its own path.
        final DBBroker broker = context.getBroker();
        final ValueSequence result = new ValueSequence();
        final Set<LuceneConfig> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final XmldbURI collectionUri : collections) {
            try (final Collection collection = broker.openCollection(collectionUri, LockMode.READ_LOCK)) {
                if (collection == null) {
                    continue;
                }
                final IndexSpec indexSpec = collection.getIndexConfiguration(broker);
                if (indexSpec == null) {
                    continue;
                }
                final LuceneConfig config = (LuceneConfig) indexSpec.getCustomIndexSpec(LuceneIndex.ID);
                if (config != null && seen.add(config)) {
                    appendFields(result, config, collectionUri);
                }
            } catch (final PermissionDeniedException e) {
                // resolveScopeCollections already permission-checked the scope; skip on any late change
            }
        }
        return result;
    }

    private void appendFields(final ValueSequence result, final LuceneConfig config, final XmldbURI collectionUri)
            throws XPathException {
        for (final LuceneIndexConfig head : config.getAllIndexConfigurations()) {
            for (LuceneIndexConfig ic = head; ic != null; ic = ic.getNext()) {
                final String element = ic.isNamed() ? ic.getName() : qnameString(ic.getQName());
                for (final AbstractFieldConfig fc : ic.getFacetsAndFields()) {
                    result.add(describe(config, fc, ic, element, collectionUri));
                }
            }
        }
    }

    private MapType describe(final LuceneConfig config, final AbstractFieldConfig fc, final LuceneIndexConfig ic,
                             final String element, final XmldbURI collectionUri) throws XPathException {
        final MapType map = new MapType(this, context);
        map.add(new StringValue(this, "collection"), new StringValue(this, collectionUri.getCollectionPath()));
        map.add(new StringValue(this, "element"), new StringValue(this, element));
        if (fc instanceof LuceneFieldConfig field) {
            map.add(new StringValue(this, "field"), new StringValue(this, field.getName()));
            map.add(new StringValue(this, "kind"), new StringValue(this, "field"));
            map.add(new StringValue(this, "analyzer"), new StringValue(this, effectiveAnalyzer(config, field, ic)));
            map.add(new StringValue(this, "type"), new StringValue(this, Type.getTypeName(field.getType())));
            map.add(new StringValue(this, "returnable"), new BooleanValue(this, field.isStore()));
        } else if (fc instanceof LuceneFacetConfig facet) {
            map.add(new StringValue(this, "field"), new StringValue(this, facet.getDimension()));
            map.add(new StringValue(this, "kind"), new StringValue(this, "facet"));
        } else if (fc instanceof LuceneVectorFieldConfig vector) {
            map.add(new StringValue(this, "field"), new StringValue(this, vector.getName()));
            map.add(new StringValue(this, "kind"), new StringValue(this, "vector"));
            map.add(new StringValue(this, "dimension"), new IntegerValue(this, vector.getDimension()));
            map.add(new StringValue(this, "similarity"),
                    new StringValue(this, vector.getSimilarity().name().toLowerCase(Locale.ROOT)));
            final String model = vector.getModelId();
            if (model != null) {
                map.add(new StringValue(this, "model"), new StringValue(this, model));
            }
        } else {
            // any other configured index entry (e.g. an element index with no named field): make it
            // self-distinguishing so every map carries field + kind, keyed by the element name.
            map.add(new StringValue(this, "field"), new StringValue(this, element));
            map.add(new StringValue(this, "kind"), new StringValue(this, "index"));
        }
        return map;
    }

    /**
     * The resolved analyzer class for a field on a given element: the field's own analyzer if set,
     * otherwise the element's index analyzer, otherwise the config's default analyzer. Always the
     * concrete class (resolving an {@code analyzer-id} reference and the default), never the raw id.
     */
    private static String effectiveAnalyzer(final LuceneConfig config, final LuceneFieldConfig field,
                                            final LuceneIndexConfig ic) {
        Analyzer analyzer = field.getAnalyzer();
        if (analyzer == null) {
            analyzer = ic.getAnalyzer();
        }
        if (analyzer == null) {
            analyzer = config.getAnalyzer((QName) null);
        }
        // eXist wraps the configured analyzers in a per-field MetaAnalyzer; unwrap it to the concrete
        // analyzer actually used for this field (the default, or a per-field/analyzer-id override).
        if (analyzer instanceof MetaAnalyzer meta) {
            analyzer = meta.getConfiguredAnalyzer(field.getName());
        }
        return analyzer != null ? analyzer.getClass().getName() : "(default)";
    }

    private static String qnameString(final QName qname) {
        return qname == null ? "" : qname.getStringValue();
    }
}
