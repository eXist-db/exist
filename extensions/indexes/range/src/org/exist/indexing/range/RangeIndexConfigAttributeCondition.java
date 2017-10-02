/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
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
 *
 *  $Id$
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

import javax.xml.XMLConstants;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

/**
 *
 * A condition that can be defined for complex range config elements
 * that compares an attribute.
 *
 * @author Marcel Schaeben
 */
public class RangeIndexConfigAttributeCondition extends RangeIndexConfigCondition{

    private final String attributeName;
    private final QName attribute;
    private final String value;
    private final Operator operator;
    private boolean caseSensitive = true;
    private boolean numericComparison = false;
    private Double numericValue = null;
    private String lowercaseValue = null;
    private Pattern pattern = null;

    public RangeIndexConfigAttributeCondition(Element elem, NodePath parentPath) throws DatabaseConfigurationException {

        if (parentPath.getLastComponent().getNameType() == ElementValue.ATTRIBUTE) {
            throw new DatabaseConfigurationException("Range index module: Attribute condition cannot be defined for an attribute:" + parentPath.toString());
        }

        this.attributeName = elem.getAttribute("attribute");
        if (this.attributeName == null || this.attributeName.length() == 0) {
            throw new DatabaseConfigurationException("Range index module: Empty or no attribute qname in condition");
        }

        try {
            this.attribute = new QName(QName.extractLocalName(this.attributeName), XMLConstants.NULL_NS_URI, QName.extractPrefix(this.attributeName), ElementValue.ATTRIBUTE);
        } catch (final QName.IllegalQNameException e) {
            throw new DatabaseConfigurationException("Rand index module error: " + e.getMessage(), e);
        }
        this.value = elem.getAttribute("value");


        // parse operator (default to 'eq' if missing)
        if (elem.hasAttribute("operator")) {
            final String operatorName = elem.getAttribute("operator");
            this.operator = Operator.getByName(operatorName.toLowerCase());
            if (this.operator == null) {
                throw new DatabaseConfigurationException("Range index module: Invalid operator specified in range index condition: " + operatorName + ".");
            }
        } else {
            this.operator = Operator.EQ;
        }


        final String caseString = elem.getAttribute("case");
        final String numericString = elem.getAttribute("numeric");

        this.caseSensitive = (caseString != null && !caseString.equalsIgnoreCase("no"));
        this.numericComparison = (numericString != null && numericString.equalsIgnoreCase("yes"));

        // try to create a pattern matcher for a 'matches' condition
        if (this.operator == Operator.MATCH) {
            final int flags = this.caseSensitive ? 0 : CASE_INSENSITIVE;
            try {
                this.pattern = Pattern.compile(this.value, flags);
            } catch (PatternSyntaxException e) {
                RangeIndex.LOG.error(e);
                throw new DatabaseConfigurationException("Range index module: Invalid regular expression in condition: " + this.value);
            }
        }

        // try to parse the number value if numeric comparison is specified
        // store a reference to numeric value to avoid having to parse each time
        if (this.numericComparison) {

            switch(this.operator) {
                case MATCH:
                case STARTS_WITH:
                case ENDS_WITH:
                case CONTAINS:
                    throw new DatabaseConfigurationException("Range index module: Numeric comparison not applicable for operator: " + this.operator.name());
            }

            try {
                this.numericValue = Double.parseDouble(this.value);
            } catch (NumberFormatException e)  {
                throw new DatabaseConfigurationException("Range index module: Numeric attribute condition specified, but required value cannot be parsed as number: " + this.value);
            }
        }

    }

    // lazily evaluate lowercase value to convert once when needed
    private String getLowercaseValue() {
        if (this.lowercaseValue == null) {
            if (this.value != null) {
                this.lowercaseValue = this.value.toLowerCase();
            }
        }

        return this.lowercaseValue;
    }


    @Override
    public boolean matches(Node node) {

        if (node.getNodeType() == Node.ELEMENT_NODE && matchValue(((Element)node).getAttribute(attributeName))) {
            return true;
        }

        return false;
    }

    private boolean matchValue(String testValue) {

        switch (operator) {
            case EQ:
            case NE:
                boolean matches;
                if (this.numericComparison) {
                    double testDouble = toDouble(testValue);
                    matches = this.numericValue.equals(testDouble);
                } else if (!this.caseSensitive) {
                    matches = this.value.equalsIgnoreCase(testValue);
                } else {
                    matches = this.value.equals(testValue);
                }
                return this.operator == Operator.EQ ? matches : !matches;

            case GT:
            case LT:
            case GE:
            case LE:
                int result;
                if (this.numericComparison) {
                    final double testDouble = toDouble(testValue);
                    result = Double.compare(testDouble, this.numericValue);
                } else if (!this.caseSensitive) {
                    result = testValue.toLowerCase().compareTo(this.getLowercaseValue());
                } else {
                    result = testValue.compareTo(this.value);
                }

                return matchOrdinal(this.operator, result);

            case ENDS_WITH:
                return this.caseSensitive ? testValue.endsWith(this.value) : testValue.toLowerCase().endsWith(this.getLowercaseValue());
            case STARTS_WITH:
                return this.caseSensitive ? testValue.startsWith(this.value) : testValue.toLowerCase().startsWith(this.getLowercaseValue());
            case CONTAINS:
                return this.caseSensitive ? testValue.contains(this.value) : testValue.toLowerCase().contains(this.getLowercaseValue());

            case MATCH:
                final Matcher matcher = this.pattern.matcher(testValue);
                return matcher.matches();
        }

        return false;

    }

    private boolean matchOrdinal(Operator operator, int result) {

        switch(operator) {
            case GT:
                return result > 0;
            case LT:
                return result < 0;
            case GE:
                return result >= 0;
            case LE:
                return result <= 0;
        }

        return false;
    }

    private Double toDouble(String value) {

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e)  {
            RangeIndex.LOG.debug("Non-numeric value encountered for numeric condition on @'" + this.attributeName + "': " + value);
            return new Double(0);
        }
    }


    @Override
    public boolean find(Predicate predicate) {

        final Expression inner = this.getInnerExpression(predicate);
        Operator operator;
        Expression lhe;
        Expression rhe ;

        // get the type of the expression inside the predicate and determine right and left hand arguments
        if (inner instanceof GeneralComparison) {

            final GeneralComparison comparison = (GeneralComparison) inner;

            operator = RangeQueryRewriter.getOperator(inner);
            lhe = comparison.getLeft();
            rhe = comparison.getRight();

        } else if (inner instanceof InternalFunctionCall) {
            // calls to matches() will not have been rewritten to a comparison, so check for function call

            final Function func = ((InternalFunctionCall) inner).getFunction();
            if (func.isCalledAs("matches")) {
                operator = Operator.MATCH;
                lhe = func.getArgument(0);
                rhe = func.getArgument(1);

                lhe = unwrapSubExpression(lhe);
                rhe = unwrapSubExpression(rhe);
            } else {

                // predicate expression cannot be parsed as condition
                return false;
            }
        } else {

            // predicate expression cannot be parsed as condition
            return false;
        }



        // find the attribute name and value pair from the predicate to check against

        // first assume attribute is on the left and value is on the right
        LocationStep testStep = findLocationStep(lhe);
        AtomicValue testValue = findAtomicValue(rhe);


        switch (this.operator) {
            case EQ:
            case NE:

                // the equality operators are commutative so if attribute/value pair has not been found,
                // check the other way around
                if (testStep == null && testValue == null) {
                    testStep = findLocationStep(rhe);
                    testValue = findAtomicValue(lhe);
                }

            case GT:
            case LT:
            case GE:
            case LE:

                // for ordinal comparisons, attribute and value can also be the other way around in the predicate
                // but the operator has to be inverted
                if (testStep == null && testValue == null) {
                    testStep = findLocationStep(rhe);
                    testValue = findAtomicValue(lhe);
                    operator = invertOrdinalOperator(operator);
                }

        }

        if (testStep != null && testValue != null) {
            final QName qname = testStep.getTest().getName();
            Comparable foundValue;
            Comparable requiredValue;
            boolean valueTypeMatches;

            try {
                if (this.numericComparison) {
                    valueTypeMatches = testValue instanceof NumericValue;
                    requiredValue = this.numericValue;
                    foundValue = testValue.toJavaObject(Double.class);
                }  else {
                    valueTypeMatches = testValue instanceof StringValue;

                    if (this.caseSensitive) {
                        requiredValue = this.getLowercaseValue();
                        foundValue = testValue.getStringValue().toLowerCase();
                    } else {
                        requiredValue = this.value;
                        foundValue = testValue.getStringValue();
                    }
                }

                if (qname.getNameType() == ElementValue.ATTRIBUTE
                        && operator.equals(this.operator)
                        && qname.equals(this.attribute)
                        && valueTypeMatches
                        && foundValue.equals(requiredValue)) {

                    return true;
                }


            } catch (XPathException e) {
                RangeIndex.LOG.error("Value conversion error when testing predicate for condition, value: " + testValue.toString());
                RangeIndex.LOG.error(e);
            }

        }

        return false;
    }

    private Expression unwrapSubExpression(Expression expr) {

        if (expr instanceof Atomize) {
            expr = ((Atomize) expr).getExpression();
        }

        if (expr instanceof DynamicCardinalityCheck) {
            if (expr.getSubExpressionCount() == 1) {
                expr = expr.getSubExpression(0);
            }
        }

        if (expr instanceof PathExpr) {
            if (expr.getSubExpressionCount() == 1) {
                expr = expr.getSubExpression(0);
            }
        }

        return expr;
    }

    private LocationStep findLocationStep(Expression expr) {

        if (expr instanceof LocationStep) {
            return (LocationStep) expr;
        }

        return null;
    }

    private AtomicValue findAtomicValue(Expression expr) {

        if (expr instanceof AtomicValue) {
            return (AtomicValue) expr;
        }
        else if (expr instanceof LiteralValue) {
            return ((LiteralValue) expr).getValue();

        } else if (expr instanceof VariableReference || expr instanceof Function) {
            try {
                final Sequence contextSequence;
                final ContextItemDeclaration cid = expr.getContext().getContextItemDeclartion();
                if(cid != null) {
                    contextSequence = cid.eval(null);
                } else {
                    contextSequence = null;
                }
                final Sequence result = expr.eval(contextSequence);
                if (result instanceof AtomicValue) {
                    return (AtomicValue) result;
                }
            } catch (XPathException e) {
                RangeIndex.LOG.error(e);
            }
        }

        return null;
    }

    private Operator invertOrdinalOperator(Operator operator) {
        switch(operator) {
            case LE: return Operator.GE;
            case GE: return Operator.LE;
            case LT: return Operator.GT;
            case GT: return Operator.LT;
        }

        return null;
    }
}
