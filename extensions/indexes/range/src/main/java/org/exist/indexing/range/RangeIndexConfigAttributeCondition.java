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
package org.exist.indexing.range;

import org.exist.dom.QName;
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.*;
import org.exist.xquery.modules.range.RangeQueryRewriter;
import org.exist.indexing.range.RangeIndex.Operator;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.apache.commons.lang3.StringUtils;

import javax.xml.XMLConstants;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

/**
 * A condition that can be defined for complex range config elements
 * that compares an attribute.
 *
 * @author Marcel Schaeben
 */
public class RangeIndexConfigAttributeCondition extends RangeIndexConfigCondition {

    private final String attributeName;
    private final QName attribute;
    private final Operator operator;

    private final boolean commutative;
    private final boolean commutativeNegate;


    private final Predicate<String> indexPredicate;
    private final Predicate<AtomicValue> queryPredicate;

    public RangeIndexConfigAttributeCondition(final Element elem, final NodePath parentPath) throws DatabaseConfigurationException {
        if (parentPath.getLastComponent().getNameType() == ElementValue.ATTRIBUTE) {
            throw new DatabaseConfigurationException("Range index module: Attribute condition cannot be defined for an attribute:" + parentPath);
        }

        if (!elem.hasAttribute("attribute")) {
            throw new DatabaseConfigurationException("Range index module: No attribute qname in condition");
        }

        attributeName = elem.getAttribute("attribute");
        if (attributeName.isEmpty()) {
            throw new DatabaseConfigurationException("Range index module: Attribute qname is empty");
        }

        try {
            attribute = new QName(QName.extractLocalName(attributeName), XMLConstants.NULL_NS_URI, QName.extractPrefix(attributeName), ElementValue.ATTRIBUTE);
        } catch (final QName.IllegalQNameException e) {
            throw new DatabaseConfigurationException("Range index module error: " + e.getMessage(), e);
        }

        final String value = elem.getAttribute("value");

        operator = getOperator(elem);
        commutative = (operator == Operator.EQ || operator == Operator.NE);
        commutativeNegate = (operator == Operator.GT || operator == Operator.LT || operator == Operator.GE || operator == Operator.LE);

        boolean numericComparison = elem.hasAttribute("numeric") && elem.getAttribute("numeric").equalsIgnoreCase("yes");
        if (numericComparison) {
            final Double num = getNumericValue(value);
            indexPredicate = getNumericMatcher(operator, num);
            queryPredicate = getNumericTester(num);
        } else {
            boolean caseSensitive = elem.hasAttribute("case") && !elem.getAttribute("case").equalsIgnoreCase("no");
            indexPredicate = caseSensitive ? getCaseMatcher(operator, value) : getMatcher(operator, value);
            queryPredicate = getTester(value, caseSensitive);
        }
    }

    private static Double getNumericValue(final String value) throws DatabaseConfigurationException {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new DatabaseConfigurationException("Range index module: Numeric attribute condition specified, but required value cannot be parsed as number: " + value);
        }
    }

    /**
     * parse operator (default to 'eq' if missing)
     *
     * @param elem configuration element
     * @return parsed operator
     * @throws DatabaseConfigurationException Operator not set or incompatible
     */
    private static Operator getOperator(final Element elem) throws DatabaseConfigurationException {
        if (!elem.hasAttribute("operator")) {
            return Operator.EQ;
        }
        final String operatorName = elem.getAttribute("operator");
        final Operator operator = Operator.getByName(operatorName.toLowerCase());
        if (operator == null) {
            throw new DatabaseConfigurationException("Range index module: Invalid operator specified in range index condition: " + operatorName + ".");
        }

        return operator;
    }

    private Predicate<String> getNumericMatcher(final Operator operator, final Double value) throws DatabaseConfigurationException {
        return switch (operator) {
            case EQ -> v -> value.equals(toDouble(v));
            case NE -> v -> !value.equals(toDouble(v));
            case GT -> v -> Double.compare(toDouble(v), value) > 0;
            case LT -> v -> Double.compare(toDouble(v), value) < 0;
            case GE -> v -> Double.compare(toDouble(v), value) >= 0;
            case LE -> v -> Double.compare(toDouble(v), value) <= 0;
            default -> throw new DatabaseConfigurationException("Range index module: Numeric comparison not applicable for operator: " + operator.name());
        };
    }

    private static Predicate<String> getCaseMatcher(final Operator operator, final String value) throws DatabaseConfigurationException {
        return switch (operator) {
            case EQ -> v -> v.equals(value);
            case NE -> v -> !v.equals(value);
            case GT -> v -> v.compareTo(value) > 0;
            case LT -> v -> v.compareTo(value) < 0;
            case GE -> v -> v.compareTo(value) >= 0;
            case LE -> v -> v.compareTo(value) <= 0;
            case ENDS_WITH -> v -> v.endsWith(value);
            case STARTS_WITH -> v -> v.startsWith(value);
            case CONTAINS -> v -> v.contains(value);
            case MATCH -> {
                final Pattern pattern = getPattern(value, 0);
                yield v -> pattern.matcher(v).matches();
            }
        };
    }

    private static Predicate<String> getMatcher(final Operator operator, final String value) throws DatabaseConfigurationException {
        return switch (operator) {
            case EQ -> v -> v.equalsIgnoreCase(value);
            case NE -> v -> !v.equalsIgnoreCase(value);
            case GT -> v -> v.compareToIgnoreCase(value) > 0;
            case LT -> v -> v.compareToIgnoreCase(value) < 0;
            case GE -> v -> v.compareToIgnoreCase(value) >= 0;
            case LE -> v -> v.compareToIgnoreCase(value) <= 0;
            case ENDS_WITH -> v -> StringUtils.endsWithIgnoreCase(v, value);
            case STARTS_WITH -> v -> StringUtils.startsWithIgnoreCase(v, value);
            case CONTAINS -> v -> StringUtils.containsIgnoreCase(v, value);
            case MATCH -> {
                final Pattern pattern = getPattern(value, CASE_INSENSITIVE);
                yield v -> pattern.matcher(v).matches();
            }
        };
    }

    private static Pattern getPattern(final String value, final int flags) throws DatabaseConfigurationException {
        try {
            return Pattern.compile(value, flags);
        } catch (PatternSyntaxException e) {
            RangeIndex.LOG.error(e);
            throw new DatabaseConfigurationException("Range index module: Invalid regular expression in condition: " + value);
        }
    }

    static Predicate<AtomicValue> throwingPredicateWrapper(
            ThrowingPredicate<AtomicValue, XPathException> throwingPredicate) {

        return v -> {
            try {
                return throwingPredicate.test(v);
            } catch (XPathException e) {
                RangeIndex.LOG.error("Value conversion error when testing predicate for condition, value: {}", v);
                RangeIndex.LOG.error(e);
                return false;
            }
        };
    }

    private static Predicate<AtomicValue> getNumericTester (final Double value) {
        return throwingPredicateWrapper(v -> v instanceof NumericValue && v.toJavaObject(Double.class).equals(value));
    }

    private static Predicate<AtomicValue> getTester (final String value, final boolean caseSensitive) {
        if (caseSensitive) {
            return throwingPredicateWrapper(v -> v instanceof StringValue && v.getStringValue().equals(value));
        }
        return throwingPredicateWrapper(v -> v instanceof StringValue && v.getStringValue().equalsIgnoreCase(value));
    }

    /**
     * Unwrap an expression
     * <p>
     * A PathExpr can be wrapped in a DynamicCardinalityCheck, which is why this has to happen in series.
     * </p>
     *
     * @param expr to unwrap
     * @return unwrapped expression
     */
    private static Expression unwrapSubExpression(Expression expr) {

        if (expr instanceof Atomize atomize) {
            expr = atomize.getExpression();
        }

        if (expr instanceof DynamicCardinalityCheck cardinalityCheck && expr.getSubExpressionCount() == 1) {
            expr = cardinalityCheck.getSubExpression(0);
        }

        if (expr instanceof PathExpr pathExpr && expr.getSubExpressionCount() == 1) {
            expr = pathExpr.getSubExpression(0);
        }

        return expr;
    }

    private static LocationStep findLocationStep(final Expression expr) {
        if (expr instanceof LocationStep step) {
            return step;
        }

        return null;
    }

    private static AtomicValue findAtomicValue(final Expression expr) {
        if (expr instanceof AtomicValue atomic) {
            return atomic;
        }

        if (expr instanceof LiteralValue literal) {
            return literal.getValue();
        }

        if (!(expr instanceof VariableReference || expr instanceof Function)) {
            return null;
        }

        try {
            final ContextItemDeclaration cid = expr.getContext().getContextItemDeclartion();
            final Sequence result;
            if (cid == null) {
                result = expr.eval(null, null);
            } else {
                final Sequence contextSequence = cid.eval(null, null);
                result = expr.eval(contextSequence, null);
            }

            if (result instanceof AtomicValue atomic) {
                return atomic;
            }
        } catch (XPathException e) {
            RangeIndex.LOG.error(e);
        }
        return null;
    }

    private static Operator invertOrdinalOperator(final Operator operator) {
        return switch (operator) {
            case LE -> Operator.GE;
            case GE -> Operator.LE;
            case LT -> Operator.GT;
            case GT -> Operator.LT;
            default -> null;
        };
    }

    @Override
    public boolean matches(final Node node) {
        return node.getNodeType() == Node.ELEMENT_NODE && indexPredicate.test(((Element) node).getAttribute(attributeName));
    }

    private Double toDouble(final String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            RangeIndex.LOG.debug("Non-numeric value encountered for numeric condition on @'{}': {}", attributeName, value);
            return (double) 0;
        }
    }

    private boolean match(final InternalFunctionCall functionCall) {
        if (!operator.equals(Operator.MATCH)) {
            return false; // nothing to do, everything else but matches is not handled here
        }
        final Function func = functionCall.getFunction();
        if (!func.isCalledAs("matches")) {
            return false; // only calls to fn:matches() are handled here
        }
        final Expression lhe = unwrapSubExpression(func.getArgument(0));
        final Expression rhe = unwrapSubExpression(func.getArgument(1));
        final LocationStep testStep = findLocationStep(lhe);
        final AtomicValue testValue = findAtomicValue(rhe);

        return canTest(testStep, testValue) && queryPredicate.test(testValue);
    }

    private boolean compare(final GeneralComparison generalComparison) {
        final Expression lhe = generalComparison.getLeft();
        final Expression rhe = generalComparison.getRight();

        // find the attribute name and value pair from the predicate to check against
        // first assume attribute is on the left and value is on the right
        Operator currentOperator = RangeQueryRewriter.getOperator(generalComparison);
        Operator invertedOperator = invertOrdinalOperator(currentOperator);

        if (!operator.equals(currentOperator) && !operator.equals(invertedOperator)) {
            return false; // needless to do more as the operator cannot match
        }

        LocationStep testStep = findLocationStep(lhe);
        AtomicValue testValue = findAtomicValue(rhe);

        // the equality operators are commutative so if attribute/value pair has not been found,
        // check the other way around
        if (testStep == null && testValue == null && (commutative || commutativeNegate)) {
            testStep = findLocationStep(rhe);
            testValue = findAtomicValue(lhe);
            // for LT, GT, GE and LE the operation has to be inverted
            if (commutativeNegate) {
                currentOperator = invertedOperator;
            }
        }

        return operator.equals(currentOperator) && canTest(testStep, testValue) && queryPredicate.test(testValue);
    }

    private boolean canTest (final LocationStep step, final AtomicValue value) {
        if (step == null || value == null) {
            return false;
        }
        final QName qname = step.getTest().getName();
        return qname.getNameType() == ElementValue.ATTRIBUTE && qname.equals(attribute);
    }

    @Override
    public boolean find(final org.exist.xquery.Predicate predicate) {
        final Expression inner = getInnerExpression(predicate);
        if (inner instanceof InternalFunctionCall functionCall) {
            return match(functionCall);
        }
        if (inner instanceof final GeneralComparison generalComparison) {
            return compare(generalComparison);
        }
        // predicate expression cannot be parsed as condition
        return false;
    }

    @FunctionalInterface
    public interface ThrowingPredicate<T, E extends Exception> {
        boolean test(T t) throws E;
    }
}
