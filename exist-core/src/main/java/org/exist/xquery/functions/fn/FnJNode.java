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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;
import org.exist.xquery.value.jnode.JNode;

/**
 * Implements XQuery 4.0 JNode functions:
 * <ul>
 *   <li>{@code fn:jtree($value)} — construct a JNode tree from a map or array</li>
 *   <li>{@code fn:jkey($node)} — return the member key of a JNode</li>
 *   <li>{@code fn:jvalue($node)} — return the typed value of a JNode</li>
 *   <li>{@code fn:jposition($node)} — return the position of a JNode among siblings</li>
 * </ul>
 *
 * @see <a href="https://qt4cg.org/specifications/xpath-functions-40/Overview.html#func-jtree">fn:jtree</a>
 * @see <a href="https://qt4cg.org/specifications/xpath-functions-40/Overview.html#func-jkey">fn:jkey</a>
 * @see <a href="https://qt4cg.org/specifications/xpath-functions-40/Overview.html#func-jvalue">fn:jvalue</a>
 * @see <a href="https://qt4cg.org/specifications/xpath-functions-40/Overview.html#func-jposition">fn:jposition</a>
 */
public class FnJNode extends BasicFunction {

    // --- fn:jtree ---

    public static final FunctionSignature FN_JTREE = new FunctionSignature(
            new QName("jtree", Function.BUILTIN_FUNCTION_NS),
            "Constructs a JSON node tree from a map or array. The resulting JNode " +
            "tree can be navigated with XPath axes and matched with node kind tests.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.EXACTLY_ONE,
                            "A map or array to convert to a JNode tree")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.EXACTLY_ONE,
                    "A JNode tree root")
    );

    // --- fn:jkey ---

    public static final FunctionSignature FN_JKEY = new FunctionSignature(
            new QName("jkey", Function.BUILTIN_FUNCTION_NS),
            "Returns the key of a JSON member node. For array items and root nodes, " +
            "returns the empty sequence.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("node", Type.ITEM, Cardinality.EXACTLY_ONE,
                            "A JNode")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                    "The member key, or empty sequence")
    );

    // --- fn:jvalue ---

    public static final FunctionSignature FN_JVALUE = new FunctionSignature(
            new QName("jvalue", Function.BUILTIN_FUNCTION_NS),
            "Returns the typed value of a JSON node. For object nodes, returns the " +
            "underlying map. For array nodes, returns the underlying array. For leaf " +
            "nodes, returns the atomic value.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("node", Type.ITEM, Cardinality.EXACTLY_ONE,
                            "A JNode")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                    "The typed value")
    );

    // --- fn:jposition ---

    public static final FunctionSignature FN_JPOSITION = new FunctionSignature(
            new QName("jposition", Function.BUILTIN_FUNCTION_NS),
            "Returns the 1-based position of a JSON node among its siblings. " +
            "For root nodes, returns 0.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("node", Type.ITEM, Cardinality.EXACTLY_ONE,
                            "A JNode")
            },
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE,
                    "The position")
    );

    // --- fn:jchildren ---

    public static final FunctionSignature FN_JCHILDREN = new FunctionSignature(
            new QName("jchildren", Function.BUILTIN_FUNCTION_NS),
            "Returns the child JNodes of a JSON node. For object nodes, returns " +
            "one child per member. For array nodes, returns one child per item. " +
            "For leaf nodes, returns the empty sequence.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("node", Type.ITEM, Cardinality.EXACTLY_ONE,
                            "A JNode")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                    "Child JNodes")
    );

    // --- fn:jparent ---

    public static final FunctionSignature FN_JPARENT = new FunctionSignature(
            new QName("jparent", Function.BUILTIN_FUNCTION_NS),
            "Returns the parent JNode, or the empty sequence for root nodes.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("node", Type.ITEM, Cardinality.EXACTLY_ONE,
                            "A JNode")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE,
                    "Parent JNode or empty")
    );

    public FnJNode(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence)
            throws XPathException {

        final String fname = getSignature().getName().getLocalPart();

        switch (fname) {
            case "jtree":
                return evalJtree(args[0]);
            case "jkey":
                return evalJkey(args[0]);
            case "jvalue":
                return evalJvalue(args[0]);
            case "jposition":
                return evalJposition(args[0]);
            case "jchildren":
                return evalJchildren(args[0]);
            case "jparent":
                return evalJparent(args[0]);
            default:
                throw new XPathException(this, ErrorCodes.XPST0017,
                        "Unknown JNode function: fn:" + fname);
        }
    }

    private Sequence evalJtree(final Sequence arg) throws XPathException {
        final Item item = arg.itemAt(0);
        if (item instanceof AbstractMapType || item instanceof ArrayType) {
            return new JNode((Sequence) item).toSequence();
        }
        // For atomic values, wrap as a leaf JNode
        return new JNode((Sequence) item).toSequence();
    }

    private Sequence evalJkey(final Sequence arg) throws XPathException {
        final Item item = arg.itemAt(0);
        if (!(item instanceof JNode)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jkey expects a JNode, got " + Type.getTypeName(item.getType()));
        }
        final JNode jnode = (JNode) item;
        final AtomicValue key = jnode.getKey();
        return key != null ? key : Sequence.EMPTY_SEQUENCE;
    }

    private Sequence evalJvalue(final Sequence arg) throws XPathException {
        final Item item = arg.itemAt(0);
        if (!(item instanceof JNode)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jvalue expects a JNode, got " + Type.getTypeName(item.getType()));
        }
        final JNode jnode = (JNode) item;
        return jnode.getValue();
    }

    private Sequence evalJchildren(final Sequence arg) throws XPathException {
        final Item item = arg.itemAt(0);
        if (!(item instanceof JNode)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jchildren expects a JNode, got " + Type.getTypeName(item.getType()));
        }
        final JNode jnode = (JNode) item;
        final java.util.List<JNode> children = jnode.getChildren();
        if (children.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final ValueSequence result = new ValueSequence(children.size());
        for (final JNode child : children) {
            result.add(child);
        }
        return result;
    }

    private Sequence evalJparent(final Sequence arg) throws XPathException {
        final Item item = arg.itemAt(0);
        if (!(item instanceof JNode)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jparent expects a JNode, got " + Type.getTypeName(item.getType()));
        }
        final JNode jnode = (JNode) item;
        final JNode parent = jnode.getParent();
        return parent != null ? parent.toSequence() : Sequence.EMPTY_SEQUENCE;
    }

    private Sequence evalJposition(final Sequence arg) throws XPathException {
        final Item item = arg.itemAt(0);
        if (!(item instanceof JNode)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jposition expects a JNode, got " + Type.getTypeName(item.getType()));
        }
        final JNode jnode = (JNode) item;
        return new IntegerValue(this, jnode.getPosition());
    }
}
