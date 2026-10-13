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
package org.exist.xquery.ft;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * W3C XQFT 3.0 thesaurus support.
 *
 * Loads thesaurus data from XML files using the XQFTTS thesaurus schema
 * and provides term expansion based on relationship type and level constraints.
 *
 * @see <a href="https://www.w3.org/TR/xpath-full-text-30/#ftthesaurusoption">XQFT 3.0 &sect;3.4.3</a>
 */
public class FTThesaurus {

    private static final String THESAURUS_NS = "http://www.w3.org/2007/xqftts/thesaurus";
    private final Map<String, List<Synonym>> entries = new HashMap<>();

    /**
     * A single thesaurus entry: a term with its synonyms at various levels.
     */
    private static class Synonym {
        final String term;
        final String relationship;
        final List<Synonym> children;

        Synonym(final String term, final String relationship, final List<Synonym> children) {
            this.term = term;
            this.relationship = relationship;
            this.children = children;
        }
    }

    /**
     * Load thesaurus from an XML file.
     */
    public static FTThesaurus load(final Path file) throws Exception {
        final FTThesaurus thes = new FTThesaurus();
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        final DocumentBuilder db = dbf.newDocumentBuilder();
        try (final InputStream is = Files.newInputStream(file)) {
            final Document doc = db.parse(is);
            final NodeList entryNodes = doc.getDocumentElement().getElementsByTagNameNS(THESAURUS_NS, "entry");
            for (int i = 0; i < entryNodes.getLength(); i++) {
                final Element entryEl = (Element) entryNodes.item(i);
                final String term = getDirectChildText(entryEl, "term");
                if (term == null) {
                    continue;
                }
                final List<Synonym> synonyms = parseSynonyms(entryEl);
                thes.entries.computeIfAbsent(term.toLowerCase(), k -> new ArrayList<>()).addAll(synonyms);
            }
        }
        return thes;
    }

    private static List<Synonym> parseSynonyms(final Element parent) {
        final List<Synonym> result = new ArrayList<>();
        final NodeList synNodes = parent.getChildNodes();
        for (int i = 0; i < synNodes.getLength(); i++) {
            if (!(synNodes.item(i) instanceof Element)) {
                continue;
            }
            final Element el = (Element) synNodes.item(i);
            if (!"synonym".equals(el.getLocalName())) {
                continue;
            }
            final String term = getDirectChildText(el, "term");
            final String rel = getDirectChildText(el, "relationship");
            if (term == null) {
                continue;
            }
            final List<Synonym> children = parseSynonyms(el);
            result.add(new Synonym(term, rel != null ? rel : "", children));
        }
        return result;
    }

    private static String getDirectChildText(final Element parent, final String localName) {
        final NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                final Element child = (Element) children.item(i);
                if (localName.equals(child.getLocalName())) {
                    return child.getTextContent().trim();
                }
            }
        }
        return null;
    }

    /**
     * Expand a term using this thesaurus.
     *
     * @param term the search term to expand
     * @param relationship if non-null, only follow synonyms with this relationship
     * @param minLevels minimum depth (0 = include direct synonyms)
     * @param maxLevels maximum depth (Integer.MAX_VALUE = unlimited)
     * @return set of expanded terms (always includes the original term)
     */
    public Set<String> expand(final String term, final String relationship,
                              final int minLevels, final int maxLevels) {
        final Set<String> result = new LinkedHashSet<>();
        result.add(term.toLowerCase());
        final List<Synonym> syns = entries.get(term.toLowerCase());
        if (syns != null) {
            for (final Synonym syn : syns) {
                collectSynonyms(syn, relationship, 1, minLevels, maxLevels, result, new HashSet<>());
            }
        }
        return result;
    }

    private void collectSynonyms(final Synonym syn, final String relationship,
                                  final int currentLevel, final int minLevels, final int maxLevels,
                                  final Set<String> result, final Set<String> visited) {
        if (currentLevel > maxLevels) {
            return;
        }
        if (relationship != null && !relationship.isEmpty()
                && !relationship.equalsIgnoreCase(syn.relationship)) {
            return;
        }
        final String lc = syn.term.toLowerCase();
        if (!visited.add(lc)) {
            return;
        }
        if (currentLevel >= minLevels) {
            result.add(lc);
        }
        // Recurse into nested synonyms (sub-levels)
        for (final Synonym child : syn.children) {
            collectSynonyms(child, relationship, currentLevel + 1, minLevels, maxLevels, result, visited);
        }
        // Also look up this synonym term in the main entries for transitive expansion
        final List<Synonym> transitive = entries.get(lc);
        if (transitive != null) {
            for (final Synonym ts : transitive) {
                collectSynonyms(ts, relationship, currentLevel + 1, minLevels, maxLevels, result, visited);
            }
        }
    }
}
