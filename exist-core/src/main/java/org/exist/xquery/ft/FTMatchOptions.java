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

import org.exist.xquery.util.ExpressionDumper;

import java.util.ArrayList;
import java.util.List;

/**
 * W3C XQFT 3.0 — FTMatchOptions.
 *
 * Collects match options specified via "using" clauses on an FTPrimaryWithOptions.
 * Each option overrides the inherited default from the static context.
 */
public class FTMatchOptions {

    public enum CaseMode { SENSITIVE, INSENSITIVE, LOWERCASE, UPPERCASE }
    public enum DiacriticsMode { SENSITIVE, INSENSITIVE }

    private CaseMode caseMode;
    private DiacriticsMode diacriticsMode;
    private Boolean stemming;       // null = not specified
    private Boolean wildcards;      // null = not specified
    private String language;        // BCP 47 tag, null = not specified
    private Boolean noThesaurus;
    private final List<String> thesaurusURIs = new ArrayList<>();
    private final List<ThesaurusID> thesaurusIDs = new ArrayList<>();
    private Boolean noStopWords;
    private boolean useDefaultStopWords;
    private final List<String> stopWordURIs = new ArrayList<>();
    private final List<String> inlineStopWords = new ArrayList<>();
    private final List<String> exceptStopWordURIs = new ArrayList<>();
    private final List<String> exceptInlineStopWords = new ArrayList<>();
    private boolean hasConflict;
    private String conflictDescription;

    /**
     * A thesaurus reference with URI, optional relationship, and optional level range.
     */
    public static class ThesaurusID {
        private final String uri;         // null for "default"
        private final String relationship; // null = any
        private final int minLevels;       // 0 = no minimum
        private final int maxLevels;       // Integer.MAX_VALUE = no maximum

        public ThesaurusID(final String uri, final String relationship,
                           final int minLevels, final int maxLevels) {
            this.uri = uri;
            this.relationship = relationship;
            this.minLevels = minLevels;
            this.maxLevels = maxLevels;
        }

        public String getUri() { return uri; }
        public String getRelationship() { return relationship; }
        public int getMinLevels() { return minLevels; }
        public int getMaxLevels() { return maxLevels; }
        public boolean isDefault() { return uri == null; }
    }

    public boolean hasConflict() { return hasConflict; }
    public String getConflictDescription() { return conflictDescription; }

    public CaseMode getCaseMode() { return caseMode; }
    public void setCaseMode(final CaseMode caseMode) {
        if (this.caseMode != null && this.caseMode != caseMode) {
            hasConflict = true;
            conflictDescription = "Conflicting case options: " + this.caseMode + " and " + caseMode;
        }
        this.caseMode = caseMode;
    }

    public DiacriticsMode getDiacriticsMode() { return diacriticsMode; }
    public void setDiacriticsMode(final DiacriticsMode diacriticsMode) {
        if (this.diacriticsMode != null && this.diacriticsMode != diacriticsMode) {
            hasConflict = true;
            conflictDescription = "Conflicting diacritics options: " + this.diacriticsMode + " and " + diacriticsMode;
        }
        this.diacriticsMode = diacriticsMode;
    }

    public Boolean getStemming() { return stemming; }
    public void setStemming(final Boolean stemming) {
        if (this.stemming != null && !this.stemming.equals(stemming)) {
            hasConflict = true;
            conflictDescription = "Conflicting stemming options";
        }
        this.stemming = stemming;
    }

    public Boolean getWildcards() { return wildcards; }
    public void setWildcards(final Boolean wildcards) {
        if (this.wildcards != null && !this.wildcards.equals(wildcards)) {
            hasConflict = true;
            conflictDescription = "Conflicting wildcard options";
        }
        this.wildcards = wildcards;
    }

    public String getLanguage() { return language; }
    public void setLanguage(final String language) { this.language = language; }

    public Boolean getNoThesaurus() { return noThesaurus; }
    public void setNoThesaurus(final Boolean noThesaurus) { this.noThesaurus = noThesaurus; }
    public List<String> getThesaurusURIs() { return thesaurusURIs; }
    public List<ThesaurusID> getThesaurusIDs() { return thesaurusIDs; }

    public Boolean getNoStopWords() { return noStopWords; }
    public void setNoStopWords(final Boolean noStopWords) { this.noStopWords = noStopWords; }
    public boolean getUseDefaultStopWords() { return useDefaultStopWords; }
    public void setUseDefaultStopWords(final boolean useDefaultStopWords) { this.useDefaultStopWords = useDefaultStopWords; }
    public List<String> getStopWordURIs() { return stopWordURIs; }
    public List<String> getInlineStopWords() { return inlineStopWords; }
    public List<String> getExceptStopWordURIs() { return exceptStopWordURIs; }
    public List<String> getExceptInlineStopWords() { return exceptInlineStopWords; }

    public void dump(final ExpressionDumper dumper) {
        if (caseMode != null) {
            dumper.display(" using case ").display(caseMode.name().toLowerCase());
        }
        if (diacriticsMode != null) {
            dumper.display(" using diacritics ").display(diacriticsMode.name().toLowerCase());
        }
        if (stemming != null) {
            dumper.display(stemming ? " using stemming" : " using no stemming");
        }
        if (wildcards != null) {
            dumper.display(wildcards ? " using wildcards" : " using no wildcards");
        }
        if (language != null) {
            dumper.display(" using language \"").display(language).display("\"");
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (caseMode != null) {
            sb.append(" using case ").append(caseMode.name().toLowerCase());
        }
        if (diacriticsMode != null) {
            sb.append(" using diacritics ").append(diacriticsMode.name().toLowerCase());
        }
        if (stemming != null) {
            sb.append(stemming ? " using stemming" : " using no stemming");
        }
        if (wildcards != null) {
            sb.append(wildcards ? " using wildcards" : " using no wildcards");
        }
        if (language != null) {
            sb.append(" using language \"").append(language).append("\"");
        }
        return sb.toString();
    }
}
