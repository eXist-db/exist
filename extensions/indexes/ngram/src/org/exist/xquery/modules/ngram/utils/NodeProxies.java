package org.exist.xquery.modules.ngram.utils;

import org.exist.dom.Match;
import org.exist.dom.NodeProxy;

public final class NodeProxies {

    private NodeProxies() {
    }

    /**
     * Filters the full-text matches on the supplied node, leaving only those for which the predicate is true.
     *
     * @param node
     *            the node whose full-text matches are to be filtered
     * @param predicate
     *            the predicate based on which the full-text matches are filtered: If the predicate returns true the
     *            match stays, if not the match is removed.
     */
    public static void filterMatches(final NodeProxy node, final F<Match, Boolean> predicate) {
        Match m = node.getMatches();
        node.setMatches(null);
        while (m != null) {
            if (predicate.f(m).booleanValue())
                node.addMatch(m);
            m = m.getNextMatch();
        }
    }

    /**
     * Applies a supplied function to all matches with the supplied expression id on the supplied NodeProxy and returns
     * the NodeProxy with the modified matches if at least one match with the supplied expression id was not transformed
     * to null or null otherwise.
     *
     * @param node
     *            the NodeProxy to modify
     * @param f
     *            the function to apply to all matches with the supplied expression id
     * @param ownExpressionId
     *            the expression id of the matches to be transformed
     * @return the modified node if at least one match with the supplied expression id was not transformed to null or
     *         null otherwise
     */
    public static NodeProxy fmapOwnMatches(final NodeProxy node, final F<Match, Match> f, int ownExpressionId) {
        Match m = node.getMatches();
        node.setMatches(null);
        boolean ownMatch = false;

        while (m != null) {
            if (m.getContextId() != ownExpressionId) {
                node.addMatch(m);
            } else {
                Match nm = f.f(m);
                if (nm != null) {
                    node.addMatch(nm);
                    ownMatch = true;
                }
            }
            m = m.getNextMatch();
        }

        if (ownMatch)
            return node;
        else
            return null;
    }

}
