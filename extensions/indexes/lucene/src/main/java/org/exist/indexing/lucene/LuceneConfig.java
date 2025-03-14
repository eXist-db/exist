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
package org.exist.indexing.lucene;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.exist.dom.QName;
import org.exist.indexing.lucene.analyzers.NoDiacriticsStandardAnalyzer;
import org.exist.storage.NodePath;
import org.exist.storage.NodePath2;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LuceneConfig {

    public final static LuceneConfig DEFAULT_CONFIG = new LuceneConfig();

	private final static Logger LOG = LogManager.getLogger(LuceneConfig.class);
	
    private final static String CONFIG_ROOT = "lucene";
    private final static String INDEX_ELEMENT = "text";
    private final static String ANALYZER_ELEMENT = "analyzer";
    private final static String PARSER_ELEMENT = "parser";
    protected final static String FIELD_TYPE_ELEMENT = "fieldType";
    private static final String INLINE_ELEMENT = "inline";
    private static final String IGNORE_ELEMENT = "ignore";
    private final static String BOOST_ATTRIB = "boost";
    private static final String DIACRITICS = "diacritics";
    private static final String MODULE_ELEMENT = "module";
    private static final String ATTR_MODULE_URI = "uri";
    private static final String ATTR_MODULE_PREFIX = "prefix";
    private static final String ATTR_MODULE_AT = "at";

    private Map<QName, LuceneIndexConfig> paths = new TreeMap<>();
    private List<LuceneIndexConfig> wildcardPaths = new ArrayList<>();
    private Map<String, LuceneIndexConfig> namedIndexes = new TreeMap<>();
    
    private Map<String, FieldType> fieldTypes = new HashMap<>();
    
    private Set<QName> inlineNodes = null;
    private Set<QName> ignoreNodes = null;

    private final PathIterator iterator = new PathIterator();
    
    private float boost = -1;

    private AnalyzerConfig analyzers = new AnalyzerConfig();

    private String queryParser = null;

    private List<ModuleImport> imports = null;

    protected FacetsConfig facetsConfig = new FacetsConfig();

    public LuceneConfig() {
    }

    public LuceneConfig(NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        parseConfig(configNodes, namespaces);
    }

    /**
     * Copy constructor. LuceneConfig is only configured once by database instance,
     * so to avoid concurrency issues when using e.g. iterator, we create a copy.
     * 
     * @param other the other LuceneConfig
     */
    public LuceneConfig(LuceneConfig other) {
    	this.paths = other.paths;
    	this.wildcardPaths = other.wildcardPaths;
    	this.namedIndexes = other.namedIndexes;
    	this.fieldTypes = other.fieldTypes;
    	this.inlineNodes = other.inlineNodes;
    	this.ignoreNodes = other.ignoreNodes;
    	this.boost = other.boost;
    	this.analyzers = other.analyzers;
    	this.facetsConfig = other.facetsConfig;
    }

    /**
     * Determines probabilistically if there might be a configured index
     * for the QName. False positive matches are possible,
     * but false negatives are not.
     *
     * @param qname the element/attribute to look for an index configuration for.
     *
     * @return true indicates that there might be a config, false indicates
     *     that there definitely is not a config.
     */
    public boolean hasConfig(final QName qname) {
        if (paths.containsKey(qname)) {
            return true;
        } else {
            final NodePath2 path = new NodePath2();
            path.addComponent(qname);
            for (final LuceneIndexConfig config : wildcardPaths) {
                if (config.match(path)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public boolean matches(final NodePath path) {
        LuceneIndexConfig idxConf = paths.get(path.getLastComponent());
        while (idxConf != null) {
            if (idxConf.match(path)) {
                return true;
            }
            idxConf = idxConf.getNext();
        }
        for (final LuceneIndexConfig config : wildcardPaths) {
            if (config.match(path)) {
                return true;
            }
        }
        return false;
    }

    public Iterator<LuceneIndexConfig> getConfig(NodePath path) {
        iterator.reset(path);
        return iterator;
    }

    protected LuceneIndexConfig getWildcardConfig(NodePath path) {
        LuceneIndexConfig config;
        for (LuceneIndexConfig wildcardPath : wildcardPaths) {
            config = wildcardPath;
            if (config.match(path))
                return config;
        }
        return null;
    }

    protected boolean hasFieldsOrFacets() {
        for (LuceneIndexConfig config : paths.values()) {
            if (config.hasFieldsOrFacets()) {
                return true;
            }
        }
        for (LuceneIndexConfig config: wildcardPaths) {
            if (config.hasFieldsOrFacets()) {
                return true;
            }
        }
        return false;
    }

    public Analyzer getAnalyzer(QName qname) {
        LuceneIndexConfig idxConf = paths.get(qname);
        while (idxConf != null) {
            if (!idxConf.isNamed() && idxConf.getNodePathPattern().match(qname))
                break;
            idxConf = idxConf.getNext();
        }
        if (idxConf != null) {
            final Analyzer analyzer = idxConf.getAnalyzer();
            if (analyzer != null) {
                return analyzer;
            }
        }
        return analyzers.getDefaultAnalyzer();
    }

    public Analyzer getAnalyzer(NodePath nodePath) {
        if (nodePath.length() == 0)
            throw new RuntimeException();
        LuceneIndexConfig idxConf = paths.get(nodePath.getLastComponent());
        while (idxConf != null) {
            if (!idxConf.isNamed() && idxConf.match(nodePath))
                break;
            idxConf = idxConf.getNext();
        }
        if (idxConf == null) {
            for (LuceneIndexConfig config : wildcardPaths) {
                if (config.match(nodePath))
                    return config.getAnalyzer();
            }
        }
        if (idxConf != null) {
            String id = idxConf.getAnalyzerId();
            if (id != null)
                return analyzers.getAnalyzerById(idxConf.getAnalyzerId());
        }
        return analyzers.getDefaultAnalyzer();
    }


    public Analyzer getAnalyzer(String field) {
        LuceneIndexConfig config = namedIndexes.get(field);
        String id = config != null ? config.getAnalyzerId() : null;
        if (id == null)
            return analyzers.getDefaultAnalyzer();

        final String indexSuffix = ":index";
        if (id.endsWith(indexSuffix)) {
            // Substitute <analyzer-id>:index with <analyzer-id>:query
            String qid = id.substring(0, id.length() - indexSuffix.length()) + ":query";
            Analyzer queryAnalyzer = analyzers.getAnalyzerById(qid);
            if (queryAnalyzer != null)
                return queryAnalyzer;

            LOG.warn(String.format("Failed to substitute %s with %s analyzer", id, qid));
        }
        return analyzers.getAnalyzerById(config.getAnalyzerId());
    }

    /**
     * Gets the Analyzer (defined in this LuceneConfig) with the specified id.
     *
     * @param analyzerId the id of the analyzer
     *
     * @return the analyzer, or null if no match was found.
     */
    public Analyzer getAnalyzerById(String analyzerId) {
        return analyzers.getAnalyzerById(analyzerId);
    }

    /**
     * Try to instantiate the configured Lucene query parser. Lucene's parsers
     * do not all have a common base class, so we need to wrap around the implementation
     * details.
     *
     * @param field the default field to query
     * @param analyzer analyzer to use for query parsing
     * @return a query wrapper
     */
    public QueryParserWrapper getQueryParser(String field, Analyzer analyzer) {
        QueryParserWrapper parser = null;
        if (queryParser != null) {
            try {
                Class<?> clazz = Class.forName(queryParser);
                if (QueryParserBase.class.isAssignableFrom(clazz)) {
                    parser = new ClassicQueryParserWrapper(queryParser, field, analyzer);
                } else {
                    parser = QueryParserWrapper.create(queryParser, field, analyzer);
                }
            } catch (ClassNotFoundException e) {
                LOG.warn("Failed to instantiate lucene query parser class: {}", queryParser, e);
            }
        }
        if (parser == null) {
            // use default parser
            parser = new ClassicQueryParserWrapper(field, analyzer);
        }
        return parser;
    }

    public boolean isInlineNode(QName qname) {
        return inlineNodes != null && inlineNodes.contains(qname);
    }

    public boolean isIgnoredNode(QName qname) {
        return ignoreNodes != null && ignoreNodes.contains(qname);
    }

    public float getBoost() {
        return boost;
    }
    
    public FieldType getFieldType(String name){
        return fieldTypes.get(name);
    }

    protected Optional<List<ModuleImport>> getImports() {
        return Optional.ofNullable(imports);
    }

    /**
     * Parse a configuration entry. The main configuration entries for this index
     * are the &lt;text&gt; elements. They may be enclosed by a &lt;lucene&gt; element.
     *
     * @param configNodes the configuration
     * @param namespaces the namespaces
     *
     * @throws DatabaseConfigurationException if the configuration can't be parsed
     */
    protected void parseConfig(final NodeList configNodes, final Map<String, String> namespaces) throws DatabaseConfigurationException {
        Node node;
        for(int i = 0; i < configNodes.getLength(); i++) {
            node = configNodes.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE) {
                try {
                    final String localName = node.getLocalName();
                    if (null != localName) {
                        switch (localName) {
                            case CONFIG_ROOT: {
                                Element elem = (Element) node;
                                if (elem.hasAttribute(BOOST_ATTRIB)) {
                                    String value = elem.getAttribute(BOOST_ATTRIB);
                                    try {
                                        boost = Float.parseFloat(value);
                                    } catch (NumberFormatException e) {
                                        throw new DatabaseConfigurationException("Invalid value for 'boost' attribute in "
                                                + "lucene index config: float expected, got " + value);
                                    }
                                }
                                if (elem.hasAttribute(DIACRITICS)) {
                                    String value = elem.getAttribute(DIACRITICS);
                                    if ("no".equalsIgnoreCase(value)) {
                                        analyzers.setDefaultAnalyzer(new NoDiacriticsStandardAnalyzer(LuceneIndex.LUCENE_VERSION_IN_USE));
                                    }
                                }
                                parseConfig(node.getChildNodes(), namespaces);
                                break;
                            }
                            case ANALYZER_ELEMENT:
                                analyzers.addAnalyzer((Element) node);
                                break;
                            case PARSER_ELEMENT:
                                queryParser = ((Element) node).getAttribute("class");
                                break;
                            case MODULE_ELEMENT:
                                if (imports == null) {
                                    imports = new ArrayList<>(3);
                                }
                                imports.add(new ModuleImport((Element)node));
                                break;
                            case FIELD_TYPE_ELEMENT:
                                FieldType type = new FieldType((Element) node, analyzers);
                                fieldTypes.put(type.getId(), type);
                                break;
                            case INDEX_ELEMENT: {
                                // found an index definition
                                Element elem = (Element) node;
                                LuceneIndexConfig config = new LuceneIndexConfig(this, elem, namespaces, analyzers, fieldTypes);
                                // if it is a named index, add it to the namedIndexes map
                                if (config.getName() != null) {
                                    namedIndexes.put(config.getName(), config);
                                }       // register index either by QName or path
                                if (config.getNodePathPattern().hasWildcard()) {
                                    wildcardPaths.add(config);
                                } else {
                                    LuceneIndexConfig idxConf = paths.get(config.getNodePathPattern().getLastComponent());
                                    if (idxConf == null) {
                                        paths.put(config.getNodePathPattern().getLastComponent(), config);
                                    } else {
                                        idxConf.add(config);
                                    }
                                }
                                break;
                            }
                            case INLINE_ELEMENT: {
                                Element elem = (Element) node;
                                QName qname = LuceneIndexConfig.parseQName(elem, namespaces);
                                if (inlineNodes == null) {
                                    inlineNodes = new HashSet<>(8);
                                }
                                inlineNodes.add(qname);
                                break;
                            }
                            case IGNORE_ELEMENT: {
                                Element elem = (Element) node;
                                QName qname = LuceneIndexConfig.parseQName(elem, namespaces);
                                if (ignoreNodes == null) {
                                    ignoreNodes = new HashSet<>(8);
                                }
                                ignoreNodes.add(qname);
                                break;
                            }
                        }
                    }
                    
                } catch (DatabaseConfigurationException e) {
                    LOG.warn("Invalid lucene configuration element: {}", e.getMessage());
				}
            }
        }
    }

    private class PathIterator implements Iterator<LuceneIndexConfig> {

        private LuceneIndexConfig nextConfig;
        private NodePath path;
        private boolean atLast = false;

        protected void reset(NodePath path) {
            this.atLast = false;
            this.path = path;
            nextConfig = paths.get(path.getLastComponent());
            if (nextConfig == null) {
                nextConfig = getWildcardConfig(path);
                atLast = true;
            }
        }

        @Override
        public boolean hasNext() {
            return (nextConfig != null);
        }

        @Override
        public LuceneIndexConfig next() {
            if (nextConfig == null)
                return null;

            LuceneIndexConfig currentConfig = nextConfig;
            nextConfig = nextConfig.getNext();
            if (nextConfig == null && !atLast) {
                nextConfig = getWildcardConfig(path);
                atLast = true;
            }
            return currentConfig;
        }

        @Override
        public void remove() {
            //Nothing to do
        }

    }

    static class ModuleImport {

        protected String uri;
        protected String prefix;
        protected String at;

        ModuleImport(Element config) throws DatabaseConfigurationException {
            this.uri = config.getAttribute(ATTR_MODULE_URI);
            this.prefix = config.getAttribute(ATTR_MODULE_PREFIX);
            this.at = config.getAttribute(ATTR_MODULE_AT);

            if (this.prefix == null || this.prefix.isEmpty()) {
                throw new DatabaseConfigurationException("Attribute prefix for <module> required");
            }
            if (this.uri == null || this.uri.isEmpty()) {
                throw new DatabaseConfigurationException("Attribute uri for <module> required");
            }
        }
    }
}
