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

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Sequential (in-memory) evaluator for W3C XQFT 3.0 expressions.
 *
 * Implements the AllMatches model from the spec in simplified form:
 * each FT expression node returns a list of {@link Match} objects,
 * where each Match records which token positions were matched and whether
 * they are inclusions or exclusions (for mild-not / not-in).
 *
 * @see <a href="https://www.w3.org/TR/xpath-full-text-30/#ftcontains">XQFT 3.0 §2</a>
 */
public class FTEvaluator {

    // --- Instance fields (declared before inner classes per Java convention) ---

    private final List<String> tokens;
    /** Tokens with trailing punctuation preserved — used for wildcard matching. */
    private final List<String> rawTokens;
    private final int totalTokens;
    /** Maps each token index to its sentence number (0-based). */
    private final int[] sentenceOf;
    /** Maps each token index to its paragraph number (0-based). */
    private final int[] paragraphOf;

    /**
     * Maps stop word URIs (as they appear in XQuery source) to local file paths.
     * Used by the XQFTTS test runner to map test URIs like
     * "http://bstore1.example.com/StopWordList.xml" to local stop word files.
     * In production use, stop word URIs would typically be file:// paths
     * or relative paths resolved against the static context base URI.
     */
    private Map<String, Path> stopWordURIMap = Collections.emptyMap();

    /**
     * Maps thesaurus URIs to local file paths.
     */
    private Map<String, Path> thesaurusURIMap = Collections.emptyMap();

    /**
     * Cache of loaded thesauri (URI -> FTThesaurus).
     */
    private final Map<String, FTThesaurus> thesaurusCache = new HashMap<>();

    /**
     * Context sequence for evaluating dynamic expressions inside FT positional
     * filters (e.g., window size expressions like {@code count(content/part/chapter) * 4}).
     * Set from FTContainsExpr when the contains-text predicate is evaluated in context.
     */
    private Sequence contextSequence;

    /**
     * Current case mode for the FTWords being evaluated. Set in evaluateFTWords()
     * and checked in wordMatches() for LOWERCASE/UPPERCASE token normalization.
     */
    private FTMatchOptions.CaseMode currentCaseMode;

    // --- Inner classes ---

    /**
     * A single match result: a set of token positions that were matched.
     * Positions are 0-based indices into the token array.
     */
    public static class Match {
        private final SortedSet<Integer> includePositions;
        private final SortedSet<Integer> excludePositions;
        // Tracks positions per operand group for the 'ordered' filter.
        // Each element is the set of positions from one FTAnd operand.
        private final List<SortedSet<Integer>> operandGroups;

        public Match() {
            this.includePositions = new TreeSet<>();
            this.excludePositions = new TreeSet<>();
            this.operandGroups = new ArrayList<>();
        }

        public Match(final int pos) {
            this();
            includePositions.add(pos);
            final SortedSet<Integer> group = new TreeSet<>();
            group.add(pos);
            operandGroups.add(group);
        }

        public Match(final SortedSet<Integer> includes, final SortedSet<Integer> excludes) {
            this.includePositions = new TreeSet<>(includes);
            this.excludePositions = new TreeSet<>(excludes);
            this.operandGroups = new ArrayList<>();
            if (!includes.isEmpty()) {
                operandGroups.add(new TreeSet<>(includes));
            }
        }

        private Match(final SortedSet<Integer> includes, final SortedSet<Integer> excludes,
                       final List<SortedSet<Integer>> groups) {
            this.includePositions = new TreeSet<>(includes);
            this.excludePositions = new TreeSet<>(excludes);
            this.operandGroups = new ArrayList<>(groups);
        }

        public SortedSet<Integer> getIncludePositions() {
            return includePositions;
        }

        public SortedSet<Integer> getExcludePositions() {
            return excludePositions;
        }

        public List<SortedSet<Integer>> getOperandGroups() {
            return operandGroups;
        }

        public SortedSet<Integer> getAllPositions() {
            final SortedSet<Integer> all = new TreeSet<>(includePositions);
            all.addAll(excludePositions);
            return all;
        }

        /**
         * Collapse operand groups into a single group containing all include positions.
         * Used after positional filters so outer filters see this match as a single unit.
         */
        public Match collapseGroups() {
            final List<SortedSet<Integer>> collapsed = new ArrayList<>();
            if (!includePositions.isEmpty()) {
                collapsed.add(new TreeSet<>(includePositions));
            }
            return new Match(includePositions, excludePositions, collapsed);
        }

        /** Combine two matches (e.g. for ftand), preserving operand groups */
        public Match combine(final Match other) {
            final SortedSet<Integer> inc = new TreeSet<>(includePositions);
            inc.addAll(other.includePositions);
            final SortedSet<Integer> exc = new TreeSet<>(excludePositions);
            exc.addAll(other.excludePositions);
            final List<SortedSet<Integer>> groups = new ArrayList<>(operandGroups);
            groups.addAll(other.operandGroups);
            return new Match(inc, exc, groups);
        }
    }

    /** All possible matches for an FT expression */
    public static class AllMatches {
        private final List<Match> matches;

        public AllMatches() {
            this.matches = new ArrayList<>();
        }

        public AllMatches(final List<Match> matches) {
            this.matches = new ArrayList<>(matches);
        }

        public List<Match> getMatches() {
            return matches;
        }

        public void addMatch(final Match match) {
            matches.add(match);
        }

        public boolean hasMatches() {
            return !matches.isEmpty();
        }
    }

    public FTEvaluator(final String text) {
        this(text, (List<Integer>) null);
    }

    public FTEvaluator(final String text, final List<Integer> elementBoundaries) {
        this.tokens = tokenize(text);
        this.rawTokens = tokenizeRaw(text);
        this.totalTokens = tokens.size();
        // Build sentence/paragraph maps, augmented by element boundary info
        final int[] offsets = tokenCharOffsets(text);
        this.sentenceOf = buildSentenceMap(text, offsets, elementBoundaries);
        this.paragraphOf = buildParagraphMap(text, offsets, elementBoundaries);
    }

    public FTEvaluator(final String text, final Map<String, Path> stopWordURIMap) {
        this(text, (List<Integer>) null);
        if (stopWordURIMap != null) {
            this.stopWordURIMap = stopWordURIMap;
        }
    }

    public FTEvaluator(final String text, final Map<String, Path> stopWordURIMap,
                        final Map<String, Path> thesaurusURIMap) {
        this(text, stopWordURIMap);
        if (thesaurusURIMap != null) {
            this.thesaurusURIMap = thesaurusURIMap;
        }
    }

    public FTEvaluator(final String text, final Map<String, Path> stopWordURIMap,
                        final Map<String, Path> thesaurusURIMap,
                        final List<Integer> elementBoundaries) {
        this(text, elementBoundaries);
        if (stopWordURIMap != null) {
            this.stopWordURIMap = stopWordURIMap;
        }
        if (thesaurusURIMap != null) {
            this.thesaurusURIMap = thesaurusURIMap;
        }
    }

    public void setContextSequence(final Sequence contextSequence) {
        this.contextSequence = contextSequence;
    }

    public List<String> getTokens() {
        return Collections.unmodifiableList(tokens);
    }

    /**
     * Tokenize text into words using Unicode word boundaries.
     */
    static List<String> tokenize(final String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> result = new ArrayList<>();
        final BreakIterator wb = BreakIterator.getWordInstance(Locale.ROOT);
        wb.setText(text);
        int start = wb.first();
        for (int end = wb.next(); end != BreakIterator.DONE; start = end, end = wb.next()) {
            final String word = text.substring(start, end);
            // Only include words that contain at least one letter or digit
            if (word.codePoints().anyMatch(Character::isLetterOrDigit)) {
                result.add(word);
            }
        }
        return result;
    }

    /**
     * Tokenize text preserving trailing punctuation on each word token.
     * Used for wildcard matching where patterns may include literal punctuation
     * (e.g., "task?" matches the literal string "task?" with a question mark).
     */
    static List<String> tokenizeRaw(final String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> result = new ArrayList<>();
        final BreakIterator wb = BreakIterator.getWordInstance(Locale.ROOT);
        wb.setText(text);
        int start = wb.first();
        // Collect all segments with their boundaries
        final List<String> segments = new ArrayList<>();
        final List<Boolean> isWord = new ArrayList<>();
        for (int end = wb.next(); end != BreakIterator.DONE; start = end, end = wb.next()) {
            final String seg = text.substring(start, end);
            segments.add(seg);
            isWord.add(seg.codePoints().anyMatch(Character::isLetterOrDigit));
        }
        // Build raw tokens: word + trailing non-whitespace punctuation
        for (int i = 0; i < segments.size(); i++) {
            if (isWord.get(i)) {
                final StringBuilder token = new StringBuilder(segments.get(i));
                // Append immediately following non-whitespace, non-word segments
                while (i + 1 < segments.size() && !isWord.get(i + 1)
                        && !segments.get(i + 1).isBlank()) {
                    i++;
                    token.append(segments.get(i));
                }
                result.add(token.toString());
            }
        }
        return result;
    }

    /**
     * Returns the character offset of each word token in the original text.
     * Token i starts at offsets[i]. Only includes tokens that match the tokenize() output.
     */
    static int[] tokenCharOffsets(final String text) {
        if (text == null || text.isEmpty()) {
            return new int[0];
        }
        final List<Integer> offsets = new ArrayList<>();
        final BreakIterator wb = BreakIterator.getWordInstance(Locale.ROOT);
        wb.setText(text);
        int start = wb.first();
        for (int end = wb.next(); end != BreakIterator.DONE; start = end, end = wb.next()) {
            final String word = text.substring(start, end);
            if (word.codePoints().anyMatch(Character::isLetterOrDigit)) {
                offsets.add(start);
            }
        }
        return offsets.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Build sentence number map using Java's sentence boundary detection,
     * augmented by element boundary offsets from the DOM structure.
     * Element boundaries are treated as sentence breaks even when
     * BreakIterator can't detect them (e.g. "example.It" from concatenated elements).
     */
    private int[] buildSentenceMap(final String text, final int[] offsets,
                                    final List<Integer> elementBoundaries) {
        if (offsets.length == 0 || text == null || text.isEmpty()) {
            return new int[0];
        }
        // Find sentence boundaries from BreakIterator
        final SortedSet<Integer> sentBounds = new TreeSet<>();
        final BreakIterator sb = BreakIterator.getSentenceInstance(Locale.ROOT);
        sb.setText(text);
        for (int boundary = sb.first(); boundary != BreakIterator.DONE; boundary = sb.next()) {
            sentBounds.add(boundary);
        }
        // Add element boundaries as additional sentence breaks
        if (elementBoundaries != null) {
            sentBounds.addAll(elementBoundaries);
        }
        // Convert to sorted list for indexed access
        final List<Integer> sortedBounds = new ArrayList<>(sentBounds);
        // Map each token to its sentence
        final int[] map = new int[offsets.length];
        int sentIdx = 0;
        for (int i = 0; i < offsets.length; i++) {
            while (sentIdx + 1 < sortedBounds.size() && offsets[i] >= sortedBounds.get(sentIdx + 1)) {
                sentIdx++;
            }
            map[i] = sentIdx;
        }
        return map;
    }

    /**
     * Build paragraph number map. Paragraphs are separated by blank lines
     * (two or more consecutive newlines, possibly with whitespace between)
     * OR by element boundaries from the DOM structure.
     */
    private int[] buildParagraphMap(final String text, final int[] offsets,
                                     final List<Integer> elementBoundaries) {
        if (offsets.length == 0 || text == null || text.isEmpty()) {
            return new int[0];
        }
        // Build set of element boundary offsets for quick lookup
        final Set<Integer> elemBounds = elementBoundaries != null
                ? new HashSet<>(elementBoundaries) : Collections.emptySet();
        // Find paragraph boundaries by scanning for double-newline patterns
        // and element boundaries
        final int[] paraAt = new int[text.length()];
        int paraNum = 0;
        boolean prevNewline = false;
        for (int i = 0; i < text.length(); i++) {
            // Element boundary: increment paragraph if we have content before it
            if (elemBounds.contains(i) && i > 0 && paraAt[i - 1] == paraNum) {
                paraNum++;
            }
            final char c = text.charAt(i);
            if (c == '\n') {
                if (prevNewline) {
                    paraNum++;
                    prevNewline = false;
                } else {
                    prevNewline = true;
                }
            } else if (c != '\r' && c != ' ' && c != '\t') {
                prevNewline = false;
            }
            paraAt[i] = paraNum;
        }
        // Map each token to its paragraph
        final int[] map = new int[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            map[i] = paraAt[Math.min(offsets[i], text.length() - 1)];
        }
        return map;
    }

    /**
     * Evaluate the full FTSelection and apply positional filters.
     */
    public boolean evaluate(final FTSelection selection, final FTMatchOptions inheritedOptions)
            throws XPathException {
        AllMatches result = evalExpression(selection.getFTOr(), inheritedOptions);
        // Apply positional filters in sequence. Collapse operand groups
        // between filters so subsequent filters treat results as single units,
        // EXCEPT before an 'ordered' filter — ordered needs to see the
        // original operand groups to check left-to-right ordering.
        final List<Expression> filters = selection.getPosFilters();
        for (int f = 0; f < filters.size(); f++) {
            result = applyPosFilter(result, filters.get(f));
            if (f < filters.size() - 1 && !(filters.get(f + 1) instanceof FTOrder)) {
                result = collapseAllGroups(result);
            }
        }
        return result.hasMatches();
    }

    /**
     * Recursively evaluate an FT expression node.
     */
    AllMatches evalExpression(final Expression expr, final FTMatchOptions options)
            throws XPathException {
        if (expr instanceof FTWords) {
            return evalFTWords((FTWords) expr, options);
        } else if (expr instanceof FTPrimaryWithOptions) {
            return evalFTPrimaryWithOptions((FTPrimaryWithOptions) expr, options);
        } else if (expr instanceof FTOr) {
            return evalFTOr((FTOr) expr, options);
        } else if (expr instanceof FTAnd) {
            return evalFTAnd((FTAnd) expr, options);
        } else if (expr instanceof FTMildNot) {
            return evalFTMildNot((FTMildNot) expr, options);
        } else if (expr instanceof FTUnaryNot) {
            return evalFTUnaryNot((FTUnaryNot) expr, options);
        } else if (expr instanceof FTSelection) {
            // Nested parenthesized FTSelection
            final FTSelection sel = (FTSelection) expr;
            AllMatches result = evalExpression(sel.getFTOr(), options);
            for (final Expression filter : sel.getPosFilters()) {
                result = applyPosFilter(result, filter);
            }
            // After applying inner positional filters, collapse operand groups
            // so outer filters treat this sub-expression as a single unit.
            if (!sel.getPosFilters().isEmpty()) {
                result = collapseAllGroups(result);
            }
            return result;
        }
        throw new XPathException(expr, "Unsupported FT expression type: " + expr.getClass().getSimpleName());
    }

    /**
     * FTWords: the terminal matching node.
     * Evaluates the words value, tokenizes it, and finds matches in the source tokens.
     */
    AllMatches evalFTWords(final FTWords ftWords, final FTMatchOptions options)
            throws XPathException {
        // Evaluate the words value expression to get the search string(s)
        final Sequence wordsSeq = ftWords.getWordsValue().eval(contextSequence, null);
        final List<String> searchStrings = new ArrayList<>();
        for (int i = 0; i < wordsSeq.getItemCount(); i++) {
            final Item item = wordsSeq.itemAt(i);
            // XQFT 3.0 §3.1: FTWords values must be coercible to xs:string*.
            // Nodes are atomized to xs:untypedAtomic (always valid).
            // Atomic types must be xs:string, xs:untypedAtomic, or xs:anyURI.
            // Other atomic types (xs:integer, etc.) raise XPTY0004.
            final int itemType = item.getType();
            if (!Type.subTypeOf(itemType, Type.NODE)
                    && !Type.subTypeOf(itemType, Type.STRING)
                    && !Type.subTypeOf(itemType, Type.ANY_URI)
                    && !Type.subTypeOf(itemType, Type.UNTYPED_ATOMIC)) {
                throw new XPathException(ftWords, ErrorCodes.XPTY0004,
                        "Full-text search value must be of type xs:string, got: "
                                + Type.getTypeName(itemType));
            }
            searchStrings.add(item.getStringValue());
        }

        if (searchStrings.isEmpty()) {
            // XQFT 3.0 §3.1: empty sequence produces no matches.
            return new AllMatches();
        }

        // XQFT 3.0 §4.1: case mode handling (XQFTTS interpretation).
        // - INSENSITIVE (default): compare tokens ignoring case.
        // - SENSITIVE: compare tokens with exact case.
        // - LOWERCASE: source token must be all lowercase; compare case-insensitively.
        // - UPPERCASE: source token must be all uppercase; compare case-insensitively.
        final FTMatchOptions.CaseMode caseMode = options == null ? null : options.getCaseMode();
        this.currentCaseMode = caseMode;
        final boolean caseInsensitive = caseMode == null ||
                caseMode == FTMatchOptions.CaseMode.INSENSITIVE ||
                caseMode == FTMatchOptions.CaseMode.LOWERCASE ||
                caseMode == FTMatchOptions.CaseMode.UPPERCASE;

        // Apply lowercase/uppercase normalization to search strings.
        // Source tokens are normalized in wordMatches() to avoid mutating the shared list.
        if (caseMode == FTMatchOptions.CaseMode.LOWERCASE) {
            searchStrings.replaceAll(s -> s.toLowerCase(Locale.ROOT));
        } else if (caseMode == FTMatchOptions.CaseMode.UPPERCASE) {
            searchStrings.replaceAll(s -> s.toUpperCase(Locale.ROOT));
        }
        final boolean useWildcards = options != null &&
                Boolean.TRUE.equals(options.getWildcards());
        // XQFT 3.0 §4.3: diacritics mode. Default to insensitive.
        final boolean diacriticsInsensitive = options == null ||
                options.getDiacriticsMode() == null ||
                options.getDiacriticsMode() == FTMatchOptions.DiacriticsMode.INSENSITIVE;
        // XQFT 3.0 §4.4: stemming mode. Default to no stemming.
        final boolean useStemming = options != null &&
                Boolean.TRUE.equals(options.getStemming());

        // Collect stop words from options (XQFT 3.0 §4.6)
        final Set<String> stopWords = collectStopWords(options, caseInsensitive, ftWords);

        // XQFT 3.0 §4.5: Thesaurus expansion.
        // For each search string, look up the full string in the thesaurus first
        // (for multi-word terms like "web site components"), then try individual words.
        if (options != null && Boolean.FALSE.equals(options.getNoThesaurus())
                && !options.getThesaurusIDs().isEmpty()) {
            final List<String> expanded = new ArrayList<>(searchStrings);
            for (final String searchStr : searchStrings) {
                for (final FTMatchOptions.ThesaurusID tid : options.getThesaurusIDs()) {
                    // First try the full search string as a thesaurus term
                    final Set<String> fullSynonyms = expandThesaurus(searchStr.trim(), tid, ftWords);
                    for (final String syn : fullSynonyms) {
                        if (!syn.equalsIgnoreCase(searchStr.trim()) && !expanded.contains(syn)) {
                            expanded.add(syn);
                        }
                    }
                    // Also try individual words (for single-word thesaurus entries)
                    for (final String word : tokenize(searchStr)) {
                        final Set<String> wordSynonyms = expandThesaurus(word, tid, ftWords);
                        for (final String syn : wordSynonyms) {
                            if (!syn.equalsIgnoreCase(word) && !expanded.contains(syn)) {
                                expanded.add(syn);
                            }
                        }
                    }
                }
            }
            searchStrings.clear();
            searchStrings.addAll(expanded);
        }

        // Validate wildcard patterns (XQFT 1.0 §A.2: only ., .+, .*, .? are valid)
        if (useWildcards) {
            for (final String searchStr : searchStrings) {
                validateWildcardPattern(searchStr, ftWords);
            }
        }

        final FTWords.AnyallMode mode = ftWords.getMode();
        AllMatches result;
        switch (mode) {
            case ANY:
                result = evalAny(searchStrings, caseInsensitive, useWildcards, diacriticsInsensitive, useStemming, stopWords); break;
            case ANY_WORD:
                result = evalAnyWord(searchStrings, caseInsensitive, useWildcards, diacriticsInsensitive, useStemming, stopWords); break;
            case ALL:
                result = evalAll(searchStrings, caseInsensitive, useWildcards, diacriticsInsensitive, useStemming, stopWords); break;
            case ALL_WORDS:
                result = evalAllWords(searchStrings, caseInsensitive, useWildcards, diacriticsInsensitive, useStemming, stopWords); break;
            case PHRASE:
                result = evalPhrase(searchStrings, caseInsensitive, useWildcards, diacriticsInsensitive, useStemming, stopWords); break;
            default:
                result = evalAny(searchStrings, caseInsensitive, useWildcards, diacriticsInsensitive, useStemming, stopWords); break;
        }

        // Apply FTTimes constraint if present
        final FTTimes ftTimes = ftWords.getFTTimes();
        if (ftTimes != null) {
            result = applyTimes(result, ftTimes);
        }
        return result;
    }

    /**
     * "any" mode: any of the search strings can match (each as a phrase).
     */
    private AllMatches evalAny(final List<String> searchStrings, final boolean caseInsensitive,
                               final boolean useWildcards, final boolean diacriticsInsensitive,
                               final boolean useStemming, final Set<String> stopWords) {
        final AllMatches result = new AllMatches();
        for (final String searchStr : searchStrings) {
            final List<String> searchTokens = useWildcards ? tokenizeWildcard(searchStr) : tokenize(searchStr);
            if (searchTokens.isEmpty()) {
                // XQFT 3.0: empty search string (no tokens) vacuously matches
                result.addMatch(new Match(new TreeSet<>(), new TreeSet<>()));
                continue;
            }
            if (searchTokens.size() == 1) {
                findWordMatches(searchTokens.get(0), caseInsensitive, useWildcards, diacriticsInsensitive, useStemming, stopWords, result);
            } else {
                findPhraseMatches(searchTokens, caseInsensitive, useWildcards, diacriticsInsensitive, useStemming, stopWords, result);
            }
        }
        return result;
    }

    /**
     * "any word" mode: tokenize all search strings into individual words,
     * any single word can match.
     */
    private AllMatches evalAnyWord(final List<String> searchStrings, final boolean caseInsensitive,
                                   final boolean useWildcards, final boolean diacriticsInsensitive,
                                   final boolean useStemming, final Set<String> stopWords) {
        final AllMatches result = new AllMatches();
        for (final String searchStr : searchStrings) {
            final List<String> words = useWildcards ? tokenizeWildcard(searchStr) : tokenize(searchStr);
            for (final String word : words) {
                if (isStopWord(word, stopWords, caseInsensitive)) {
                    continue;
                }
                findWordMatches(word, caseInsensitive, useWildcards, diacriticsInsensitive, useStemming, stopWords, result);
            }
        }
        return result;
    }

    /**
     * "all" mode: all search strings must match (each as a phrase).
     */
    private AllMatches evalAll(final List<String> searchStrings, final boolean caseInsensitive,
                               final boolean useWildcards, final boolean diacriticsInsensitive,
                               final boolean useStemming, final Set<String> stopWords) {
        AllMatches combined = null;
        for (final String searchStr : searchStrings) {
            final List<String> searchTokens = useWildcards ? tokenizeWildcard(searchStr) : tokenize(searchStr);
            if (searchTokens.isEmpty()) {
                continue;
            }
            final AllMatches phraseMatches = new AllMatches();
            findPhraseMatches(searchTokens, caseInsensitive, useWildcards, diacriticsInsensitive, useStemming, stopWords, phraseMatches);
            if (!phraseMatches.hasMatches()) {
                return new AllMatches(); // all must match — one failed
            }
            combined = (combined == null) ? phraseMatches : crossProduct(combined, phraseMatches);
        }
        return combined != null ? combined : new AllMatches();
    }

    /**
     * "all words" mode: tokenize all search strings, every individual word must match.
     */
    private AllMatches evalAllWords(final List<String> searchStrings, final boolean caseInsensitive,
                                    final boolean useWildcards, final boolean diacriticsInsensitive,
                                    final boolean useStemming, final Set<String> stopWords) {
        final List<String> allWords = new ArrayList<>();
        for (final String s : searchStrings) {
            allWords.addAll(useWildcards ? tokenizeWildcard(s) : tokenize(s));
        }
        if (allWords.isEmpty()) {
            return singleEmptyMatch();
        }
        AllMatches combined = null;
        for (final String word : allWords) {
            if (isStopWord(word, stopWords, caseInsensitive)) {
                continue;
            }
            final AllMatches wordMatches = new AllMatches();
            findWordMatches(word, caseInsensitive, useWildcards, diacriticsInsensitive, useStemming, stopWords, wordMatches);
            if (!wordMatches.hasMatches()) {
                return new AllMatches(); // all must match
            }
            combined = (combined == null) ? wordMatches : crossProduct(combined, wordMatches);
        }
        return combined != null ? combined : singleEmptyMatch();
    }

    /**
     * "phrase" mode: all search strings concatenated form one phrase.
     */
    private AllMatches evalPhrase(final List<String> searchStrings, final boolean caseInsensitive,
                                  final boolean useWildcards, final boolean diacriticsInsensitive,
                                  final boolean useStemming, final Set<String> stopWords) {
        final List<String> phraseTokens = new ArrayList<>();
        for (final String s : searchStrings) {
            phraseTokens.addAll(useWildcards ? tokenizeWildcard(s) : tokenize(s));
        }
        if (phraseTokens.isEmpty()) {
            return new AllMatches(); // no tokens, no match
        }
        final AllMatches result = new AllMatches();
        findPhraseMatches(phraseTokens, caseInsensitive, useWildcards, diacriticsInsensitive, useStemming, stopWords, result);
        return result;
    }

    /**
     * Find all positions where a single word matches in the token list.
     */
    private void findWordMatches(final String word, final boolean caseInsensitive,
                                 final boolean useWildcards, final boolean diacriticsInsensitive,
                                 final boolean useStemming, final Set<String> stopWords,
                                 final AllMatches result) {
        if (isStopWord(word, stopWords, caseInsensitive)) {
            // Stop words in search query are treated as automatically matching
            return;
        }
        for (int i = 0; i < totalTokens; i++) {
            final String rawToken = (useWildcards && i < rawTokens.size()) ? rawTokens.get(i) : null;
            if (wordMatches(tokens.get(i), rawToken, word, caseInsensitive, useWildcards, diacriticsInsensitive, useStemming)) {
                result.addMatch(new Match(i));
            }
        }
    }

    /**
     * Find all positions where a phrase (sequence of words) matches consecutively.
     * Stop words in the search phrase are treated as matching any source token.
     */
    private void findPhraseMatches(final List<String> phraseTokens, final boolean caseInsensitive,
                                   final boolean useWildcards, final boolean diacriticsInsensitive,
                                   final boolean useStemming, final Set<String> stopWords,
                                   final AllMatches result) {
        final int phraseLen = phraseTokens.size();
        outer:
        for (int i = 0; i <= totalTokens - phraseLen; i++) {
            for (int j = 0; j < phraseLen; j++) {
                final String searchToken = phraseTokens.get(j);
                // Stop words in search phrases match any source token position
                if (isStopWord(searchToken, stopWords, caseInsensitive)) {
                    continue; // this position is OK
                }
                final int idx = i + j;
                final String rawToken = (useWildcards && idx < rawTokens.size()) ? rawTokens.get(idx) : null;
                if (!wordMatches(tokens.get(idx), rawToken, searchToken, caseInsensitive, useWildcards, diacriticsInsensitive, useStemming)) {
                    continue outer;
                }
            }
            // Found a phrase match at positions i..i+phraseLen-1
            final SortedSet<Integer> positions = new TreeSet<>();
            for (int j = 0; j < phraseLen; j++) {
                positions.add(i + j);
            }
            result.addMatch(new Match(positions, new TreeSet<>()));
        }
    }

    /**
     * Check if a source token matches a search word.
     * @param rawSourceToken token with trailing punctuation preserved (for wildcard matching), or null
     */
    private boolean wordMatches(final String sourceToken, final String rawSourceToken,
                                final String searchWord,
                                final boolean caseInsensitive, final boolean useWildcards,
                                final boolean diacriticsInsensitive, final boolean useStemming) {
        String src = sourceToken;
        String search = searchWord;

        // Apply diacritics normalization if insensitive
        if (diacriticsInsensitive) {
            src = stripDiacritics(src);
            search = stripDiacritics(search);
        }

        if (useWildcards && containsWildcardIndicator(search)) {
            final String regex = wildcardToRegex(search, caseInsensitive);
            // First try matching against the clean token
            if (Pattern.matches(regex, src)) {
                return true;
            }
            // If the pattern contains literal punctuation (via XQFT escape
            // sequences like \. \? etc.), that punctuation is only present in
            // the raw token. Use lookingAt() instead of matches() to handle
            // trailing punctuation (e.g. raw token "next?," for pattern "nex.\?").
            if (search.contains("\\") && rawSourceToken != null) {
                String rawSrc = rawSourceToken;
                if (diacriticsInsensitive) {
                    rawSrc = stripDiacritics(rawSrc);
                }
                return Pattern.compile(regex).matcher(rawSrc).lookingAt();
            }
            return false;
        }

        // When wildcards are enabled but the search token has no wildcard indicator,
        // strip punctuation from the search token so it matches as a normal token.
        // tokenizeWildcard() preserves all characters; normal matching needs clean tokens.
        // XQFTTS: "task?" with no wildcard indicator strips to "task" and matches source "task".
        if (useWildcards) {
            search = search.replaceAll("[^\\p{L}\\p{N}]", "");
            if (search.isEmpty()) {
                return false;
            }
        }

        // Apply stemming: compare stems instead of exact words
        if (useStemming) {
            src = stem(src);
            search = stem(search);
        }

        if (caseInsensitive) {
            // XQFT §4.1 + XQFTTS interpretation: for lowercase/uppercase modes,
            // the source token must already be in the specified case. The search
            // token is normalized in evalFTWords(), and comparison is case-insensitive.
            // This acts as a FILTER: 'using uppercase' only matches tokens that
            // are already uppercase in the source (e.g., "AIDS" matches but "aids" does not).
            if (currentCaseMode == FTMatchOptions.CaseMode.LOWERCASE) {
                if (!sourceToken.equals(sourceToken.toLowerCase(Locale.ROOT))) {
                    return false;
                }
            } else if (currentCaseMode == FTMatchOptions.CaseMode.UPPERCASE) {
                if (!sourceToken.equals(sourceToken.toUpperCase(Locale.ROOT))) {
                    return false;
                }
            }
            return src.equalsIgnoreCase(search);
        }
        return src.equals(search);
    }

    /**
     * Tokenize a wildcard search pattern into tokens.
     * Unlike the normal tokenizer, this preserves wildcard characters (., *, +, ?, \, {, })
     * within tokens. Splits on whitespace boundaries.
     */
    static List<String> tokenizeWildcard(final String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> result = new ArrayList<>();
        for (final String part : pattern.split("\\s+")) {
            if (!part.isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }

    /**
     * Basic English stemmer using suffix stripping.
     * Reduces common English inflections (plurals, verb forms, etc.)
     * to approximate stems for full-text comparison. Based on a simplified
     * version of the Porter stemming algorithm.
     */
    static String stem(final String word) {
        if (word == null || word.length() < 3) {
            return word;
        }
        String s = word.toLowerCase(Locale.ROOT);

        // Step 1: Strip inflectional suffixes (longest match first)
        if (s.endsWith("ational")) {
            s = s.substring(0, s.length() - 7) + "ate";
        } else if (s.endsWith("iveness")) {
            s = s.substring(0, s.length() - 7) + "ive";
        } else if (s.endsWith("fulness")) {
            s = s.substring(0, s.length() - 7) + "ful";
        } else if (s.endsWith("ously")) {
            s = s.substring(0, s.length() - 5) + "ous";
        } else if (s.endsWith("ement")) {
            s = s.substring(0, s.length() - 5);
        } else if (s.endsWith("ness")) {
            s = s.substring(0, s.length() - 4);
        } else if (s.endsWith("ment") && !s.endsWith("mment")) {
            s = s.substring(0, s.length() - 4);
        } else if (s.endsWith("ies")) {
            s = s.substring(0, s.length() - 3) + "i";
        } else if (s.endsWith("ied")) {
            s = s.substring(0, s.length() - 3) + "i";
        } else if (s.endsWith("eed")) {
            // keep as-is (e.g. "feed")
        } else if (s.endsWith("ing")) {
            final String base = s.substring(0, s.length() - 3);
            if (base.length() >= 2) {
                s = undouble(base);
            }
        } else if (s.endsWith("ed")) {
            final String base = s.substring(0, s.length() - 2);
            if (base.length() >= 2) {
                s = undouble(base);
            }
        } else if (s.endsWith("ers")) {
            final String base = s.substring(0, s.length() - 3);
            if (base.length() >= 2) {
                s = undouble(base);
            }
        } else if (s.endsWith("er")) {
            final String base = s.substring(0, s.length() - 2);
            if (base.length() >= 2) {
                s = undouble(base);
            }
        } else if (s.endsWith("es")) {
            final String base = s.substring(0, s.length() - 2);
            if (base.length() >= 3) {
                s = base;
            }
        } else if (s.endsWith("s") && !s.endsWith("ss")) {
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("ly")) {
            final String base = s.substring(0, s.length() - 2);
            if (base.length() >= 3) {
                s = base;
            }
        }

        // Step 2: Remove trailing 'e' if the stem is long enough.
        // This ensures "picture" → "pictur" matches "pictures" → "pictur".
        if (s.length() >= 4 && s.endsWith("e") && !s.endsWith("ee")) {
            s = s.substring(0, s.length() - 1);
        }

        return s;
    }

    /**
     * Undo doubled consonant at end of stem (e.g. "runn" → "run").
     */
    private static String undouble(final String base) {
        if (base.length() >= 3
                && base.charAt(base.length() - 1) == base.charAt(base.length() - 2)
                && !isVowel(base.charAt(base.length() - 1))) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static boolean isVowel(final char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }

    /**
     * Strip diacritical marks from a string using Unicode normalization.
     * NFD decomposes characters, then we remove combining diacritical marks.
     */
    private static String stripDiacritics(final String text) {
        final String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        // Remove combining diacritical marks (Unicode block 0300-036F)
        return normalized.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
    }

    /**
     * Check if a word is in the stop word set.
     */
    private static boolean isStopWord(final String word, final Set<String> stopWords,
                                       final boolean caseInsensitive) {
        if (stopWords.isEmpty()) {
            return false;
        }
        return caseInsensitive ? stopWords.contains(word.toLowerCase(Locale.ROOT)) : stopWords.contains(word);
    }

    /**
     * Collect stop words from FTMatchOptions.
     * XQFT 3.0 §4.6: inline stop words and stop word URIs.
     *
     * <p>Stop words can come from two sources:
     * <ul>
     *   <li>Inline: literal words specified in the query via {@code ("word1", "word2")}</li>
     *   <li>External: loaded from URIs specified via {@code at "URI"}</li>
     * </ul>
     *
     * XQFT 3.0 §4.5: Expand a word using a thesaurus.
     * Loads the thesaurus from the URI (using the thesaurusURIMap for resolution)
     * and returns synonyms matching the relationship and level constraints.
     *
     * @throws XPathException FTST0018 if the thesaurus cannot be loaded
     */
    private Set<String> expandThesaurus(final String word, final FTMatchOptions.ThesaurusID tid,
                                         final Expression context) throws XPathException {
        if (tid.isDefault()) {
            // Default thesaurus: look up "##default" in the URI map
            final Path defaultFile = thesaurusURIMap.get("##default");
            if (defaultFile == null || !Files.exists(defaultFile)) {
                // No default thesaurus configured — return just the word itself
                return Collections.singleton(word);
            }
            FTThesaurus thesaurus = thesaurusCache.get("##default");
            if (thesaurus == null) {
                try {
                    thesaurus = FTThesaurus.load(defaultFile);
                    thesaurusCache.put("##default", thesaurus);
                } catch (final Exception e) {
                    return Collections.singleton(word);
                }
            }
            return thesaurus.expand(word, tid.getRelationship(), tid.getMinLevels(), tid.getMaxLevels());
        }
        final String uri = tid.getUri();
        FTThesaurus thesaurus = thesaurusCache.get(uri);
        if (thesaurus == null) {
            // Resolve URI to file path
            Path file = thesaurusURIMap.get(uri);
            if (file == null) {
                // Try resolving as a direct file path
                try {
                    file = Path.of(new URI(uri));
                } catch (final Exception e) {
                    // Not a valid file URI
                }
            }
            if (file == null || !Files.exists(file)) {
                throw new XPathException(context, ErrorCodes.FTST0018,
                        "Thesaurus not available: " + uri);
            }
            try {
                thesaurus = FTThesaurus.load(file);
                thesaurusCache.put(uri, thesaurus);
            } catch (final Exception e) {
                throw new XPathException(context, ErrorCodes.FTST0018,
                        "Failed to load thesaurus: " + uri + " - " + e.getMessage());
            }
        }
        return thesaurus.expand(word, tid.getRelationship(), tid.getMinLevels(), tid.getMaxLevels());
    }

    /**
     * <p>External stop word files are plain text, one word per line (or whitespace-delimited).
     * BaseX uses the same format. URI resolution uses the {@link #stopWordURIMap} for mapped
     * URIs (e.g., XQFTTS test URIs), falling back to direct file path or URL resolution.
     *
     * <p><b>Limitation:</b> This implementation supports simple whitespace-delimited text files.
     * A future enhancement could integrate with Lucene/Snowball stop word lists for broader
     * language coverage beyond what the basic text file format provides.
     *
     * @throws XPathException FTST0008 if an external stop word URI cannot be loaded
     */
    private Set<String> collectStopWords(final FTMatchOptions options, final boolean caseInsensitive,
                                         final Expression context) throws XPathException {
        if (options == null) {
            return Collections.emptySet();
        }
        if (Boolean.TRUE.equals(options.getNoStopWords())) {
            return Collections.emptySet();
        }
        final Set<String> result = new HashSet<>();

        // XQFT 3.0 §4.6: raise FTST0013 if stop words are requested with a language
        // specification but we don't support language-specific stop word lists.
        // We only have a default (English) stop word list; no per-language lists.
        if (options.getUseDefaultStopWords() && options.getLanguage() != null) {
            final String lang = options.getLanguage().trim().toLowerCase(Locale.ROOT);
            if (!lang.isEmpty() && !lang.equals("en") && !lang.startsWith("en-")) {
                throw new XPathException(context, ErrorCodes.FTST0013,
                        "Stop word list not available for language: " + options.getLanguage());
            }
        }

        // Handle "stop words default" — load from ##default URI mapping
        if (options.getUseDefaultStopWords()) {
            final Path defaultPath = stopWordURIMap.get("##default");
            if (defaultPath != null) {
                loadStopWordsFromPath(defaultPath, result, caseInsensitive, context, "##default");
            }
            // If no ##default mapping, silently use empty set (implementation-defined)
        }

        // Add inline stop words (union)
        final List<String> inlineWords = options.getInlineStopWords();
        if (inlineWords != null) {
            for (final String sw : inlineWords) {
                result.add(caseInsensitive ? sw.toLowerCase(Locale.ROOT) : sw);
            }
        }

        // Load external stop words from URIs (union)
        final List<String> uris = options.getStopWordURIs();
        if (uris != null) {
            for (final String uri : uris) {
                loadStopWordsFromURI(uri, result, caseInsensitive, context);
            }
        }

        // Remove excepted inline stop words
        final List<String> exceptInline = options.getExceptInlineStopWords();
        if (exceptInline != null) {
            for (final String sw : exceptInline) {
                result.remove(caseInsensitive ? sw.toLowerCase(Locale.ROOT) : sw);
            }
        }

        // Remove excepted URI stop words
        final List<String> exceptURIs = options.getExceptStopWordURIs();
        if (exceptURIs != null && !exceptURIs.isEmpty()) {
            final Set<String> exceptWords = new HashSet<>();
            for (final String uri : exceptURIs) {
                loadStopWordsFromURI(uri, exceptWords, caseInsensitive, context);
            }
            result.removeAll(exceptWords);
        }

        return result;
    }

    /**
     * Load stop words from an external URI.
     * Tries the following resolution strategies in order:
     * <ol>
     *   <li>Mapped URI via {@link #stopWordURIMap} (for test runner URI mappings)</li>
     *   <li>Direct file path (if the URI is a valid local file path)</li>
     *   <li>file:// URI scheme</li>
     * </ol>
     *
     * <p>Stop word files are expected to contain whitespace-delimited words.
     * This matches the format used by BaseX and the XQFTTS test suite.
     *
     * <p><b>Note:</b> HTTP URI fetching is not supported. For production use with
     * remote stop word lists, consider pre-loading them or using a URI catalog.
     * A Lucene-based stop word provider could also be plugged in here.
     *
     * @throws XPathException FTST0008 if the stop word file cannot be loaded
     */
    private void loadStopWordsFromURI(final String uri, final Set<String> result,
                                      final boolean caseInsensitive,
                                      final Expression context) throws XPathException {
        // Strategy 1: check the URI map (e.g., XQFTTS test runner mappings)
        final Path mappedPath = stopWordURIMap.get(uri);
        if (mappedPath != null) {
            loadStopWordsFromPath(mappedPath, result, caseInsensitive, context, uri);
            return;
        }

        // Strategy 2: try as a local file path
        try {
            final Path filePath = Path.of(uri);
            if (Files.exists(filePath)) {
                loadStopWordsFromPath(filePath, result, caseInsensitive, context, uri);
                return;
            }
        } catch (final Exception e) {
            // Not a valid file path — try URI parsing
        }

        // Strategy 3: try as a file:// URI
        try {
            final URI parsed = new URI(uri);
            if ("file".equals(parsed.getScheme())) {
                final Path filePath = Path.of(parsed);
                loadStopWordsFromPath(filePath, result, caseInsensitive, context, uri);
                return;
            }
        } catch (final URISyntaxException | IllegalArgumentException e) {
            // Not a valid URI — fall through to error
        }

        // Could not resolve the URI — raise FTST0008
        throw new XPathException(context, ErrorCodes.FTST0008,
                "Cannot load external stop word list: " + uri
                        + ". Only mapped URIs, local file paths, and file:// URIs are supported. "
                        + "HTTP fetching is not implemented.");
    }

    /**
     * Read stop words from a local file path. Words are whitespace-delimited.
     */
    private static void loadStopWordsFromPath(final Path path, final Set<String> result,
                                              final boolean caseInsensitive,
                                              final Expression context,
                                              final String originalURI) throws XPathException {
        if (!Files.exists(path)) {
            throw new XPathException(context, ErrorCodes.FTST0008,
                    "Stop word file not found: " + path + " (from URI: " + originalURI + ")");
        }
        try (final BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                for (final String word : line.trim().split("\\s+")) {
                    if (!word.isEmpty()) {
                        result.add(caseInsensitive ? word.toLowerCase(Locale.ROOT) : word);
                    }
                }
            }
        } catch (final IOException e) {
            throw new XPathException(context, ErrorCodes.FTST0008,
                    "Error reading stop word file: " + path + " (from URI: " + originalURI + "): " + e.getMessage());
        }
    }

    /**
     * Validate a wildcard pattern for XQFT syntax compliance.
     * Raises FTDY0020 if the pattern contains invalid wildcard constructs.
     * Valid: .{n,m} (comma-separated numeric range), .{c,c} (comma-separated char range)
     * Invalid: .{n} (single number), .{n-m} (dash-separated), .{c-c} (dash-separated chars)
     */
    static void validateWildcardPattern(final String pattern, final Expression context) throws XPathException {
        int i = 0;
        while (i < pattern.length()) {
            final char c = pattern.charAt(i);
            if (c == '.') {
                i++;
                if (i < pattern.length()) {
                    final char next = pattern.charAt(i);
                    if (next == '{') {
                        // Extract content between { and }
                        i++; // skip {
                        final StringBuilder content = new StringBuilder();
                        while (i < pattern.length() && pattern.charAt(i) != '}') {
                            content.append(pattern.charAt(i));
                            i++;
                        }
                        if (i < pattern.length()) {
                            i++; // skip }
                        }
                        final String rangeContent = content.toString();
                        // Only .{X,Y} with commas is valid; dashes and single values are invalid
                        if (!rangeContent.contains(",")) {
                            throw new XPathException(context, ErrorCodes.FTDY0020,
                                    "Invalid wildcard pattern: .{" + rangeContent + "} is not valid wildcard syntax");
                        }
                    } else if (next == '*' || next == '+' || next == '?') {
                        i++;
                    }
                    // else just '.', which is fine
                }
            } else if (c == '\\') {
                i += 2; // skip escaped char
            } else {
                i++;
            }
        }
    }

    /**
     * Check if a search token contains an unescaped '.' (the XQFT wildcard indicator).
     * Per XQFT §4.7, only tokens containing '.' use wildcard matching; others are
     * matched as normal tokens even when the wildcard option is enabled.
     */
    static boolean containsWildcardIndicator(final String token) {
        for (int i = 0; i < token.length(); i++) {
            final char c = token.charAt(i);
            if (c == '\\') {
                return true; // escape sequence is wildcard syntax (e.g. \. \? \*)
            } else if (c == '.') {
                return true;
            }
        }
        return false;
    }

    /**
     * Convert XQFT wildcard pattern to Java regex.
     * XQFT wildcards: "." matches any single char, ".+" matches one or more,
     * ".*" matches zero or more, ".{n,m}" etc.
     */
    static String wildcardToRegex(final String pattern, final boolean caseInsensitive) {
        final StringBuilder sb = new StringBuilder();
        if (caseInsensitive) {
            sb.append("(?i)");
        }
        // XQFT wildcard grammar (§4.7):
        // "." matches any single char
        // ".?" zero or one, ".+" one or more, ".*" zero or more
        // ".{n-m}" n to m of any char (note: dash, not comma)
        // ".{a-z}" character range (single char in range)
        int i = 0;
        while (i < pattern.length()) {
            final char c = pattern.charAt(i);
            if (c == '.') {
                i++;
                if (i < pattern.length()) {
                    final char next = pattern.charAt(i);
                    if (next == '*' || next == '+' || next == '?') {
                        sb.append('.');
                        sb.append(next);
                        i++;
                    } else if (next == '{') {
                        // Extract content between { and }
                        i++; // skip {
                        final StringBuilder rangeContent = new StringBuilder();
                        while (i < pattern.length() && pattern.charAt(i) != '}') {
                            rangeContent.append(pattern.charAt(i));
                            i++;
                        }
                        if (i < pattern.length()) {
                            i++; // skip }
                        }
                        final String range = rangeContent.toString();
                        final int dashIdx = range.indexOf('-');
                        if (dashIdx > 0 && dashIdx < range.length() - 1) {
                            final String left = range.substring(0, dashIdx);
                            final String right = range.substring(dashIdx + 1);
                            if (left.chars().allMatch(Character::isDigit) && right.chars().allMatch(Character::isDigit)) {
                                // Numeric range: .{n-m} → .{n,m}
                                sb.append(".{").append(left).append(',').append(right).append('}');
                            } else {
                                // Character range: .{a-z} → [a-z]
                                sb.append('[').append(left).append('-').append(right).append(']');
                            }
                        } else {
                            // Single number: .{n} → .{n}
                            sb.append('.').append('{').append(range).append('}');
                        }
                    } else {
                        // Just "." — match any single char
                        sb.append('.');
                    }
                } else {
                    sb.append('.');
                }
            } else if (c == '\\') {
                // Escaped char — treat next char as literal
                i++;
                if (i < pattern.length()) {
                    sb.append(Pattern.quote(String.valueOf(pattern.charAt(i))));
                    i++;
                }
            } else {
                // Literal character — escape for regex
                sb.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        return sb.toString();
    }

    // === Boolean operators ===

    AllMatches evalFTOr(final FTOr ftOr, final FTMatchOptions options)
            throws XPathException {
        final AllMatches result = new AllMatches();
        for (final Expression operand : ftOr.getOperands()) {
            final AllMatches sub = evalExpression(operand, options);
            result.getMatches().addAll(sub.getMatches());
        }
        return result;
    }

    AllMatches evalFTAnd(final FTAnd ftAnd, final FTMatchOptions options)
            throws XPathException {
        AllMatches combined = null;
        for (final Expression operand : ftAnd.getOperands()) {
            final AllMatches sub = evalExpression(operand, options);
            if (!sub.hasMatches()) {
                return new AllMatches(); // short-circuit: one operand has no matches
            }
            combined = (combined == null) ? sub : crossProduct(combined, sub);
        }
        return combined != null ? combined : singleEmptyMatch();
    }

    AllMatches evalFTMildNot(final FTMildNot ftMildNot, final FTMatchOptions options)
            throws XPathException {
        final List<Expression> operands = ftMildNot.getOperands();
        if (operands.isEmpty()) {
            return new AllMatches();
        }
        AllMatches result = evalExpression(operands.get(0), options);
        for (int i = 1; i < operands.size(); i++) {
            final AllMatches exclude = evalExpression(operands.get(i), options);
            result = applyMildNot(result, exclude);
        }
        return result;
    }

    /**
     * Mild not: remove matches from left where a right match covers ALL
     * include positions of the left match (XQFT 3.0 §4.5.3).
     *
     * A left match is removed only when there exists a right match whose
     * include positions are a superset of the left match's include positions.
     */
    private AllMatches applyMildNot(final AllMatches left, final AllMatches right) {
        if (!right.hasMatches()) {
            return left;
        }
        final AllMatches result = new AllMatches();
        for (final Match lm : left.getMatches()) {
            boolean covered = false;
            for (final Match rm : right.getMatches()) {
                if (rm.getIncludePositions().containsAll(lm.getIncludePositions())) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                result.addMatch(lm);
            }
        }
        return result;
    }

    AllMatches evalFTUnaryNot(final FTUnaryNot ftNot, final FTMatchOptions options)
            throws XPathException {
        final AllMatches inner = evalExpression(ftNot.getOperand(), options);
        if (inner.hasMatches()) {
            return new AllMatches(); // negation: inner matched → overall doesn't match
        }
        return singleEmptyMatch(); // inner didn't match → overall matches
    }

    AllMatches evalFTPrimaryWithOptions(final FTPrimaryWithOptions pwo, final FTMatchOptions inheritedOptions)
            throws XPathException {
        // XQFT 3.0 §4.9: raise FTST0019 if match options conflict
        final FTMatchOptions localOptions = pwo.getMatchOptions();
        if (localOptions != null && localOptions.hasConflict()) {
            throw new XPathException(pwo, ErrorCodes.FTST0019,
                    localOptions.getConflictDescription());
        }

        // Merge match options: local options override inherited ones
        final FTMatchOptions effective = mergeOptions(inheritedOptions, localOptions);

        // XQFT 3.0 §4.8: raise FTST0009 for unsupported languages.
        // We support Latin-script languages (en, de, fr, es, it, pt, nl, etc.)
        // using the default whitespace tokenizer and Snowball stemmer.
        // Non-Latin-script languages (zh, ja, ko, ar, he, th, etc.) require
        // specialized tokenizers we don't have, so we raise FTST0009.
        if (effective != null && effective.getLanguage() != null) {
            final String lang = effective.getLanguage().trim();
            if (!lang.isEmpty()) {
                // Reject invalid BCP 47 tags
                if (!lang.matches("[a-zA-Z]{2,8}(-.*)?")) {
                    throw new XPathException(pwo, ErrorCodes.FTST0009,
                            "Language not supported: " + effective.getLanguage());
                }
                // Reject languages requiring non-Latin tokenizers
                final String primary = lang.contains("-") ? lang.substring(0, lang.indexOf('-')) : lang;
                final String lc = primary.toLowerCase(Locale.ROOT);
                if ("zh".equals(lc) || "ja".equals(lc) || "ko".equals(lc) ||
                        "ar".equals(lc) || "he".equals(lc) || "th".equals(lc) ||
                        "hi".equals(lc) || "bn".equals(lc) || "ta".equals(lc) ||
                        "ka".equals(lc) || "km".equals(lc) || "my".equals(lc)) {
                    throw new XPathException(pwo, ErrorCodes.FTST0009,
                            "Language not supported (no tokenizer for non-Latin script): " + lang);
                }
            }
        }

        // XQFT 3.0 §3.7: Validate weight expression if present.
        // Weight must evaluate to a numeric value in [-1000, 1000]; otherwise FTDY0016.
        // Non-numeric values raise XPTY0004.
        if (pwo.getWeight() != null) {
            final Sequence weightSeq = pwo.getWeight().eval(contextSequence, null);
            if (weightSeq.isEmpty() || !Type.subTypeOfUnion(weightSeq.itemAt(0).getType(), Type.NUMERIC)) {
                throw new XPathException(pwo, ErrorCodes.XPTY0004,
                        "Weight expression must evaluate to a numeric value, got: " +
                                (weightSeq.isEmpty() ? "empty sequence" : Type.getTypeName(weightSeq.itemAt(0).getType())));
            }
            final double w = weightSeq.itemAt(0).toJavaObject(Double.class);
            if (w < -1000.0 || w > 1000.0 || Double.isNaN(w)) {
                throw new XPathException(pwo, ErrorCodes.FTDY0016,
                        "Weight value " + w + " is out of the allowed range [-1000.0, 1000.0]");
            }
        }

        return evalExpression(pwo.getPrimary(), effective);
    }

    // === Positional filters ===

    AllMatches applyPosFilter(final AllMatches input, final Expression filter)
            throws XPathException {
        if (filter instanceof FTOrder) {
            return applyOrdered(input);
        } else if (filter instanceof FTWindow) {
            return applyWindow(input, (FTWindow) filter);
        } else if (filter instanceof FTDistance) {
            return applyDistance(input, (FTDistance) filter);
        } else if (filter instanceof FTContent) {
            return applyContent(input, (FTContent) filter);
        } else if (filter instanceof FTScope) {
            return applyScope(input, (FTScope) filter);
        }
        return input;
    }

    /**
     * "ordered": keep matches where operand groups appear in ascending
     * position order — i.e., max position of group i < min position of group i+1.
     */
    private AllMatches applyOrdered(final AllMatches input) {
        final AllMatches result = new AllMatches();
        for (final Match m : input.getMatches()) {
            if (isOrdered(m)) {
                result.addMatch(m);
            }
        }
        return result;
    }

    private boolean isOrdered(final Match match) {
        final List<SortedSet<Integer>> groups = match.getOperandGroups();
        if (groups.size() <= 1) {
            return true;
        }
        int prevMax = Integer.MIN_VALUE;
        for (final SortedSet<Integer> group : groups) {
            if (group.isEmpty()) {
                continue;
            }
            if (group.first() <= prevMax) {
                return false;
            }
            prevMax = group.last();
        }
        return true;
    }

    /**
     * "window N unit": all matched positions must fit within N consecutive units.
     */
    private AllMatches applyWindow(final AllMatches input, final FTWindow ftWindow)
            throws XPathException {
        final int windowSize = evalIntExpr(ftWindow.getWindowExpr());
        final FTUnit unit = ftWindow.getUnit();
        final AllMatches result = new AllMatches();
        for (final Match m : input.getMatches()) {
            final SortedSet<Integer> positions = m.getIncludePositions();
            if (positions.isEmpty()) {
                result.addMatch(m);
            } else {
                final int span = unitSpan(positions.first(), positions.last(), unit);
                if (span <= windowSize) {
                    result.addMatch(m);
                }
            }
        }
        return result;
    }

    /**
     * "distance range unit": the distance between consecutive match positions
     * must satisfy the range constraint.
     */
    private AllMatches applyDistance(final AllMatches input, final FTDistance ftDistance)
            throws XPathException {
        final FTRange range = ftDistance.getRange();
        final int[] bounds = evalRange(range);
        final int min = bounds[0];
        final int max = bounds[1];
        final FTUnit unit = ftDistance.getUnit();

        final AllMatches result = new AllMatches();
        for (final Match m : input.getMatches()) {
            final List<SortedSet<Integer>> groups = m.getOperandGroups();
            // Single group (e.g. after positional filter collapse): vacuously satisfied
            if (groups.size() <= 1) {
                result.addMatch(m);
                continue;
            }
            // Per XQFT §4.5: distance is measured between consecutive operand groups
            // (StringIncludes), not between individual token positions.
            // Sort groups by their minimum position for consistent ordering.
            final List<SortedSet<Integer>> sorted = new ArrayList<>(groups);
            sorted.sort((a, b) -> {
                if (a.isEmpty()) return -1;
                if (b.isEmpty()) return 1;
                return Integer.compare(a.first(), b.first());
            });
            boolean satisfies = true;
            for (int i = 1; i < sorted.size(); i++) {
                final SortedSet<Integer> prev = sorted.get(i - 1);
                final SortedSet<Integer> curr = sorted.get(i);
                if (prev.isEmpty() || curr.isEmpty()) {
                    continue;
                }
                // Distance = gap between last token of previous group and first token of next group
                final int dist = unitDistance(prev.last(), curr.first(), unit);
                if (dist < min || dist > max) {
                    satisfies = false;
                    break;
                }
            }
            if (satisfies) {
                result.addMatch(m);
            }
        }
        return result;
    }

    /**
     * "at start" / "at end" / "entire content": content-based positional filter.
     */
    private AllMatches applyContent(final AllMatches input, final FTContent ftContent) {
        final AllMatches result = new AllMatches();
        for (final Match m : input.getMatches()) {
            final SortedSet<Integer> positions = m.getIncludePositions();
            if (positions.isEmpty()) {
                continue;
            }
            switch (ftContent.getContentType()) {
                case AT_START:
                    if (positions.first() == 0) {
                        result.addMatch(m);
                    }
                    break;
                case AT_END:
                    if (positions.last() == totalTokens - 1) {
                        result.addMatch(m);
                    }
                    break;
                case ENTIRE_CONTENT:
                    // XQFT 3.0 §3.6.2: entire content requires that the match covers
                    // all token positions from 0 to totalTokens-1.
                    if (positions.first() == 0 && positions.last() == totalTokens - 1
                            && positions.size() == totalTokens) {
                        result.addMatch(m);
                    }
                    break;
                default:
                    break;
            }
        }
        return result;
    }

    /**
     * FTScope: "same sentence", "same paragraph", "different sentence", "different paragraph".
     *
     * For "same": all positions in all StringIncludes must be in the same unit.
     * For "different": requires >= 2 StringIncludes; each single-unit StringInclude
     * must be in a distinct unit (multi-unit StringIncludes that span unit boundaries
     * are never rejected).
     */
    private AllMatches applyScope(final AllMatches input, final FTScope ftScope) {
        final AllMatches result = new AllMatches();
        final boolean isSentence = ftScope.getBigUnit() == FTScope.BigUnit.SENTENCE;
        final int[] unitMap = isSentence ? sentenceOf : paragraphOf;
        final boolean isSame = ftScope.getScopeType() == FTScope.ScopeType.SAME;

        for (final Match m : input.getMatches()) {
            final List<SortedSet<Integer>> groups = m.getOperandGroups();
            if (groups.isEmpty()) {
                continue;
            }
            if (isSame) {
                // All positions from all groups must be in the same unit
                int commonUnit = -1;
                boolean allSame = true;
                for (final SortedSet<Integer> group : groups) {
                    for (final int pos : group) {
                        final int u = pos < unitMap.length ? unitMap[pos] : 0;
                        if (commonUnit < 0) {
                            commonUnit = u;
                        } else if (u != commonUnit) {
                            allSame = false;
                            break;
                        }
                    }
                    if (!allSame) {
                        break;
                    }
                }
                if (allSame) {
                    result.addMatch(m);
                }
            } else {
                // "different": require >= 2 groups.
                // For each group, determine the unit of its first and last positions.
                // If a group spans a single unit, that unit must not already be seen.
                // Groups spanning multiple units (first unit != last unit) are always accepted.
                int count = 0;
                boolean allDifferent = true;
                final Set<Integer> usedUnits = new HashSet<>();
                for (final SortedSet<Integer> group : groups) {
                    if (group.isEmpty()) {
                        continue;
                    }
                    count++;
                    final int startUnit = group.first() < unitMap.length ? unitMap[group.first()] : 0;
                    final int endUnit = group.last() < unitMap.length ? unitMap[group.last()] : 0;
                    if (startUnit == endUnit && !usedUnits.add(startUnit)) {
                        // Single-unit group already seen: not all different
                        allDifferent = false;
                        break;
                    }
                    // Multi-unit group (spans boundary): mark start unit but never reject
                    usedUnits.add(startUnit);
                }
                if (allDifferent && count > 1) {
                    result.addMatch(m);
                }
            }
        }
        return result;
    }

    /**
     * Compute the span between two token positions in the given unit.
     * For WORDS: last - first + 1.
     * For SENTENCES/PARAGRAPHS: unit(last) - unit(first) + 1.
     */
    private int unitSpan(final int first, final int last, final FTUnit unit) {
        if (unit == FTUnit.WORDS) {
            return last - first + 1;
        }
        final int[] unitMap = (unit == FTUnit.SENTENCES) ? sentenceOf : paragraphOf;
        final int u1 = first < unitMap.length ? unitMap[first] : 0;
        final int u2 = last < unitMap.length ? unitMap[last] : 0;
        return Math.abs(u2 - u1) + 1;
    }

    /**
     * Compute the distance (gap) between two token positions in the given unit.
     * Per XQFT 3.0 §4.5, distance counts the number of intervening units between
     * two positions — i.e. the gap. "distance exactly 0 sentences" means adjacent
     * sentences (no sentence between them), analogous to "distance 0 words" meaning
     * adjacent words. Formula: abs(unitOf(pos2) - unitOf(pos1)) - 1.
     */
    private int unitDistance(final int pos1, final int pos2, final FTUnit unit) {
        if (unit == FTUnit.WORDS) {
            return pos2 - pos1 - 1;
        }
        final int[] unitMap = (unit == FTUnit.SENTENCES) ? sentenceOf : paragraphOf;
        final int u1 = pos1 < unitMap.length ? unitMap[pos1] : 0;
        final int u2 = pos2 < unitMap.length ? unitMap[pos2] : 0;
        return Math.abs(u2 - u1) - 1;
    }

    /**
     * Apply FTTimes constraint: the number of matches must satisfy the range.
     * "occurs exactly N times" means exactly N distinct matches.
     */
    private AllMatches applyTimes(final AllMatches input, final FTTimes ftTimes)
            throws XPathException {
        final FTRange range = ftTimes.getRange();
        final int[] bounds = evalRange(range);
        final int min = bounds[0];
        final int max = bounds[1];
        final int matchCount = input.getMatches().size();

        if (matchCount >= min && matchCount <= max) {
            // If the count satisfies the range but AllMatches is empty (0 matches),
            // return a single empty match to signal "constraint satisfied".
            // Per XQFT 3.0 §4.8: 0 occurrences satisfies "at most N times".
            if (matchCount == 0) {
                return singleEmptyMatch();
            }
            return input;
        }
        return new AllMatches(); // constraint not satisfied
    }

    // === Helpers ===

    /**
     * Cross product of two AllMatches: combine each match from left with
     * each match from right.
     */
    private AllMatches crossProduct(final AllMatches left, final AllMatches right) {
        final AllMatches result = new AllMatches();
        for (final Match lm : left.getMatches()) {
            for (final Match rm : right.getMatches()) {
                result.addMatch(lm.combine(rm));
            }
        }
        return result;
    }

    /**
     * Collapse operand groups in all matches to single groups.
     * Used after positional filters in nested FTSelection so outer filters
     * treat the result as a single unit.
     */
    private AllMatches collapseAllGroups(final AllMatches input) {
        final AllMatches result = new AllMatches();
        for (final Match m : input.getMatches()) {
            result.addMatch(m.collapseGroups());
        }
        return result;
    }

    private AllMatches singleEmptyMatch() {
        final AllMatches am = new AllMatches();
        am.addMatch(new Match());
        return am;
    }

    private int evalIntExpr(final Expression expr) throws XPathException {
        final Sequence seq = expr.eval(contextSequence, null);
        if (seq.isEmpty()) {
            throw new XPathException(expr, ErrorCodes.XPTY0004,
                    "Full-text range/window/distance expression must evaluate to a single integer");
        }
        final Item item = seq.itemAt(0);
        final int type = item.getType();
        // Per XQFT 3.0: must be a non-negative integer
        if (type != Type.INTEGER && type != Type.INT && type != Type.SHORT
                && type != Type.LONG && type != Type.BYTE
                && type != Type.UNSIGNED_INT && type != Type.UNSIGNED_SHORT
                && type != Type.UNSIGNED_LONG && type != Type.UNSIGNED_BYTE
                && type != Type.NON_NEGATIVE_INTEGER && type != Type.POSITIVE_INTEGER
                && type != Type.NON_POSITIVE_INTEGER && type != Type.NEGATIVE_INTEGER) {
            throw new XPathException(expr, ErrorCodes.XPTY0004,
                    "Full-text range/window/distance expression must evaluate to an integer, got: "
                            + Type.getTypeName(type));
        }
        return item.toJavaObject(int.class);
    }

    private int[] evalRange(final FTRange range) throws XPathException {
        switch (range.getMode()) {
            case EXACTLY: {
                final int n = evalIntExpr(range.getExpr1());
                return new int[]{n, n};
            }
            case AT_LEAST: {
                final int n = evalIntExpr(range.getExpr1());
                return new int[]{n, Integer.MAX_VALUE};
            }
            case AT_MOST: {
                final int n = evalIntExpr(range.getExpr1());
                return new int[]{0, n};
            }
            case FROM_TO: {
                final int from = evalIntExpr(range.getExpr1());
                final int to = evalIntExpr(range.getExpr2());
                return new int[]{from, to};
            }
            default:
                return new int[]{0, Integer.MAX_VALUE};
        }
    }

    /**
     * Merge inherited options with local overrides.
     */
    static FTMatchOptions mergeOptions(final FTMatchOptions inherited, final FTMatchOptions local) {
        if (local == null) {
            return inherited;
        }
        if (inherited == null) {
            return local;
        }
        // Local overrides inherited
        final FTMatchOptions merged = new FTMatchOptions();
        merged.setCaseMode(local.getCaseMode() != null ? local.getCaseMode() : inherited.getCaseMode());
        merged.setDiacriticsMode(local.getDiacriticsMode() != null ? local.getDiacriticsMode() : inherited.getDiacriticsMode());
        merged.setStemming(local.getStemming() != null ? local.getStemming() : inherited.getStemming());
        merged.setWildcards(local.getWildcards() != null ? local.getWildcards() : inherited.getWildcards());
        merged.setLanguage(local.getLanguage() != null ? local.getLanguage() : inherited.getLanguage());
        merged.setNoThesaurus(local.getNoThesaurus() != null ? local.getNoThesaurus() : inherited.getNoThesaurus());
        merged.setNoStopWords(local.getNoStopWords() != null ? local.getNoStopWords() : inherited.getNoStopWords());
        // Merge stop word lists (local overrides if non-empty)
        if (!local.getInlineStopWords().isEmpty()) {
            merged.getInlineStopWords().addAll(local.getInlineStopWords());
        } else if (inherited.getInlineStopWords() != null) {
            merged.getInlineStopWords().addAll(inherited.getInlineStopWords());
        }
        if (!local.getStopWordURIs().isEmpty()) {
            merged.getStopWordURIs().addAll(local.getStopWordURIs());
        } else if (inherited.getStopWordURIs() != null) {
            merged.getStopWordURIs().addAll(inherited.getStopWordURIs());
        }
        // Merge thesaurus IDs (local overrides if non-empty)
        if (!local.getThesaurusIDs().isEmpty()) {
            merged.getThesaurusIDs().addAll(local.getThesaurusIDs());
        } else if (!inherited.getThesaurusIDs().isEmpty()) {
            merged.getThesaurusIDs().addAll(inherited.getThesaurusIDs());
        }
        return merged;
    }
}
