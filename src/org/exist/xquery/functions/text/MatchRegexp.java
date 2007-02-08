package org.exist.xquery.functions.text;

import java.util.ArrayList;
import java.util.List;

import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.xquery.CachedResult;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.RegexTranslator;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class MatchRegexp extends Function {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("match-all", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"Tries to match each of the regular expression " +
			"strings passed in $b against the keywords contained in " +
			"the fulltext index. The keywords found are then compared to the node set in $a. Every " +
			"node containing ALL of the keywords is copied to the result sequence. By default, a keyword " +
            "is considered to match the pattern only if the entire string matches. To change this behaviour, " +
            "use the 3-argument version of the function and specify flag 's'. With 's' specified, a string matches " +
            "the pattern if any substring matches, i.e. 'explain.*' will match 'unexplained'.",
			new SequenceType[] {
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
				new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE)
            },
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
		),
        new FunctionSignature(
			new QName("match-all", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"Tries to match each of the regular expression " +
			"strings passed in $b against the keywords contained in " +
			"the fulltext index. The keywords found are then compared to the node set in $a. Every " +
			"node containing ALL of the keywords is copied to the result sequence. By default, a keyword " +
            "is considered to match the pattern only if the entire string matches. To change this behaviour, " +
            "use the 3-argument version of the function and specify flag 's'. With 's' specified, a string matches " +
            "the pattern if any substring matches, i.e. 'explain.*' will match 'unexplained'.",
			new SequenceType[] {
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
				new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE),
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
		),
        new FunctionSignature(
			new QName("match-any", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"Tries to match each of the regular expression " +
			"strings passed in $b against the keywords contained in " +
			"the fulltext index. The keywords found are then compared to the node set in $a. Every " +
			"node containing ANY of the keywords is copied to the result sequence. By default, a keyword " +
            "is considered to match the pattern only if the entire string matches. To change this behaviour, " +
            "use the 3-argument version of the function and specify flag 's'. With 's' specified, a string matches " +
            "the pattern if any substring matches, i.e. 'explain.*' will match 'unexplained'.",
			new SequenceType[] {
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
				new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE)
            },
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
		),
        new FunctionSignature(
			new QName("match-any", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"Tries to match each of the regular expression " +
			"strings passed in $b against the keywords contained in " +
			"the fulltext index. The keywords found are then compared to the node set in $a. Every " +
			"node containing ANY of the keywords is copied to the result sequence. By default, a keyword " +
            "is considered to match the pattern only if the entire string matches. To change this behaviour, " +
            "use the 3-argument version of the function and specify flag 's'. With 's' specified, a string matches " +
            "the pattern if any substring matches, i.e. 'explain.*' will match 'unexplained'.",
			new SequenceType[] {
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
				new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE),
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
		)
    };

    public static final String MATCH_ALL_FLAG = "w";

    protected int type = Constants.FULLTEXT_AND;
	protected CachedResult cached = null;

	public MatchRegexp(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		int deps = 0;
		for(int i = 0; i < getArgumentCount(); i++)
			deps = deps | getArgument(i).getDependencies();
		return deps;
	}

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }

		if(getArgumentCount() < 2)
			throw new XPathException(getASTNode(), "function requires at least two arguments");

		if (contextItem != null)
			contextSequence = contextItem.toSequence();

        if (isCalledAs("match-any"))
            type = Constants.FULLTEXT_OR;

        Expression path = getArgument(0);
        Expression termsExpr = getArgument(1);
        Expression flagsExpr = (getArgumentCount() == 3) ? getArgument(2) : null;

        Sequence result;
		if (!Dependency.dependsOn(path, Dependency.CONTEXT_ITEM)) {

			boolean canCache = (termsExpr.getDependencies() & Dependency.CONTEXT_ITEM)
				== Dependency.NO_DEPENDENCY;

			if(	canCache && cached != null && cached.isValid(contextSequence)) {
				return cached.getResult();
			}

			NodeSet nodes =
				path == null
					? contextSequence.toNodeSet()
					: path.eval(contextSequence).toNodeSet();
			List terms = getSearchTerms(termsExpr, contextSequence);
            boolean matchAll = getMatchFlag(flagsExpr, contextSequence);
            result = evalQuery(nodes, terms, matchAll);

			if(canCache && contextSequence instanceof NodeSet)
				cached = new CachedResult((NodeSet)contextSequence, result);

		} else {
			result = new ExtArrayNodeSet();
			for (SequenceIterator i = contextSequence.iterate(); i.hasNext();) {
				Item current = i.nextItem();
				List terms = getSearchTerms(termsExpr, current.toSequence());
				NodeSet nodes =
					path == null
						? contextSequence.toNodeSet()
						: path.eval(current.toSequence()).toNodeSet();
                boolean matchAll = getMatchFlag(flagsExpr, contextSequence);
                Sequence temp = evalQuery(nodes, terms, matchAll);
				result.addAll(temp);
			}
		}

        if (context.getProfiler().isEnabled())
            context.getProfiler().end(this, "", result);

		return result;
	}

    private boolean getMatchFlag(Expression flagsExpr, Sequence contextSequence) throws XPathException {
        boolean matchAll = false;
        if (flagsExpr != null) {
            String flagStr = flagsExpr.eval(contextSequence).getStringValue();
            matchAll = flagStr.equals(MATCH_ALL_FLAG);
        }
        return matchAll;
    }

    public Sequence evalQuery(NodeSet nodes, List terms, boolean matchAll)
		throws XPathException {
		if(terms == null || terms.size() == 0)
			return Sequence.EMPTY_SEQUENCE;	// no search terms
		NodeSet hits[] = new NodeSet[terms.size()];
		for (int k = 0; k < terms.size(); k++) {
			hits[k] =
				context.getBroker().getTextEngine().getNodesContaining(
				    context,
					nodes.getDocumentSet(),
					nodes, NodeSet.ANCESTOR, null,
					(String)terms.get(k), DBBroker.MATCH_REGEXP, matchAll);
		}
		NodeSet result = hits[0];
		if(result != null) {
			for(int k = 1; k < hits.length; k++) {
				if(hits[k] != null)
					result = (type == Constants.FULLTEXT_AND ?
							result.deepIntersection(hits[k]) : result.union(hits[k]));
			}
			return result;
		} else
			return NodeSet.EMPTY_SET;
	}

	protected List getSearchTerms(Expression termsExpr, Sequence contextSequence) throws XPathException {
		List terms = new ArrayList();
		Sequence seq = termsExpr.eval(contextSequence);
        if(seq.hasOne())
            terms.add(translateRegexp(seq.itemAt(0).getStringValue()));
        else {
            for(SequenceIterator it = seq.iterate(); it.hasNext(); ) {
                terms.add(translateRegexp(it.nextItem().getStringValue()));
            }
        }
		return terms;
	}

	protected int getTermDependencies() throws XPathException {
		int deps = 0;
		Expression next;
		for(int i = 1; i < getLength(); i++) {
			next = getArgument(i);
			deps |= next.getDependencies();
		}
		return deps;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#resetState()
	 */
	public void resetState() {
		super.resetState();
		cached = null;
	}

	/**
	 * Translates the regular expression from XPath2 syntax to java regex
	 * syntax.
	 *
	 * @param pattern
	 * @return
	 * @throws org.exist.xquery.XPathException
	 */
	protected String translateRegexp(String pattern) throws XPathException {
		// convert pattern to Java regex syntax
       try {
			pattern = RegexTranslator.translate(pattern, true);
		} catch (RegexTranslator.RegexSyntaxException e) {
			throw new XPathException(getASTNode(), "Conversion from XPath2 to Java regular expression " +
					"syntax failed: " + e.getMessage(), e);
		}
		return pattern;
	}
}
