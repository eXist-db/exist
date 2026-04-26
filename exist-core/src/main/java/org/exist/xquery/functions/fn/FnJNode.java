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
 *   <li>{@code fn:jkey($node?)} — return the member key/index of a JNode</li>
 *   <li>{@code fn:jvalue($node?)} — return the typed value of a JNode</li>
 *   <li>{@code fn:jposition($node?)} — return the 1-based position among siblings</li>
 *   <li>{@code fn:jchildren($node?)} — return child JNodes</li>
 *   <li>{@code fn:jparent($node?)} — return parent JNode</li>
 * </ul>
 *
 * @see <a href="https://qt4cg.org/specifications/xpath-functions-40/Overview.html#func-jtree">fn:jtree</a>
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
            new FunctionReturnSequenceType(Type.JSON_NODE, Cardinality.EXACTLY_ONE,
                    "A JNode tree root")
    );

    // --- fn:jkey ---

    public static final FunctionSignature FN_JKEY_0 = new FunctionSignature(
            new QName("jkey", Function.BUILTIN_FUNCTION_NS),
            "Returns the selector of the context JSON node — key for member, index for array item.",
            new SequenceType[0],
            new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE,
                    "The member key/index, or empty for root nodes")
    );

    public static final FunctionSignature FN_JKEY = new FunctionSignature(
            new QName("jkey", Function.BUILTIN_FUNCTION_NS),
            "Returns the selector of a JSON node — key for member, index for array item. " +
            "For root nodes, returns the empty sequence.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("node", Type.ITEM, Cardinality.ZERO_OR_ONE,
                            "A JNode")
            },
            new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE,
                    "The selector, or empty sequence")
    );

    // --- fn:jvalue ---

    public static final FunctionSignature FN_JVALUE_0 = new FunctionSignature(
            new QName("jvalue", Function.BUILTIN_FUNCTION_NS),
            "Returns the typed value of the context JSON node.",
            new SequenceType[0],
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                    "The typed value")
    );

    public static final FunctionSignature FN_JVALUE = new FunctionSignature(
            new QName("jvalue", Function.BUILTIN_FUNCTION_NS),
            "Returns the typed value of a JSON node. For object nodes, returns the " +
            "underlying map. For array nodes, returns the underlying array. For leaf " +
            "nodes, returns the atomic value.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("node", Type.ITEM, Cardinality.ZERO_OR_ONE,
                            "A JNode")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                    "The typed value")
    );

    // --- fn:jposition ---

    public static final FunctionSignature FN_JPOSITION_0 = new FunctionSignature(
            new QName("jposition", Function.BUILTIN_FUNCTION_NS),
            "Returns the 1-based position of the context JSON node among its siblings.",
            new SequenceType[0],
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE,
                    "The position, or empty for root nodes")
    );

    public static final FunctionSignature FN_JPOSITION = new FunctionSignature(
            new QName("jposition", Function.BUILTIN_FUNCTION_NS),
            "Returns the 1-based position of a JSON node among its siblings. " +
            "For root nodes, returns the empty sequence.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("node", Type.ITEM, Cardinality.ZERO_OR_ONE,
                            "A JNode")
            },
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE,
                    "The position, or empty for root nodes")
    );

    // --- fn:jchildren ---

    public static final FunctionSignature FN_JCHILDREN_0 = new FunctionSignature(
            new QName("jchildren", Function.BUILTIN_FUNCTION_NS),
            "Returns child JNodes of the context JSON node.",
            new SequenceType[0],
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                    "Child JNodes")
    );

    public static final FunctionSignature FN_JCHILDREN = new FunctionSignature(
            new QName("jchildren", Function.BUILTIN_FUNCTION_NS),
            "Returns the child JNodes of a JSON node. For object nodes, returns " +
            "one child per member. For array nodes, returns one child per item. " +
            "For leaf nodes, returns the empty sequence.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("node", Type.ITEM, Cardinality.ZERO_OR_ONE,
                            "A JNode")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                    "Child JNodes")
    );

    // --- fn:jparent ---

    public static final FunctionSignature FN_JPARENT_0 = new FunctionSignature(
            new QName("jparent", Function.BUILTIN_FUNCTION_NS),
            "Returns the parent of the context JSON node.",
            new SequenceType[0],
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE,
                    "Parent JNode or empty")
    );

    public static final FunctionSignature FN_JPARENT = new FunctionSignature(
            new QName("jparent", Function.BUILTIN_FUNCTION_NS),
            "Returns the parent JNode, or the empty sequence for root nodes.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("node", Type.ITEM, Cardinality.ZERO_OR_ONE,
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

        // 0-arg variants use context item; resolve to a 1-element sequence (or empty)
        final Sequence input;
        if (args.length == 0) {
            if (contextSequence == null || contextSequence.isEmpty()) {
                if ("jtree".equals(fname)) {
                    throw new XPathException(this, ErrorCodes.XPDY0002,
                            "fn:" + fname + ": context item is absent");
                }
                throw new XPathException(this, ErrorCodes.XPDY0002,
                        "fn:" + fname + ": context item is absent");
            }
            // Use the context item (single item)
            input = contextSequence.itemAt(0).toSequence();
        } else {
            input = args[0];
        }

        switch (fname) {
            case "jtree":
                return evalJtree(input);
            case "jkey":
                return evalJkey(input);
            case "jvalue":
                return evalJvalue(input);
            case "jposition":
                return evalJposition(input);
            case "jchildren":
                return evalJchildren(input);
            case "jparent":
                return evalJparent(input);
            default:
                throw new XPathException(this, ErrorCodes.XPST0017,
                        "Unknown JNode function: fn:" + fname);
        }
    }

    private Sequence evalJtree(final Sequence arg) throws XPathException {
        if (arg.isEmpty()) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jtree: argument must be a map or array, got empty sequence");
        }
        if (arg.getItemCount() != 1) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jtree: expected a single map or array, got " + arg.getItemCount() + " items");
        }
        final Item item = arg.itemAt(0);
        // If already a JNode, unwrap to its underlying value (idempotent)
        if (item instanceof JNode) {
            final Sequence underlying = ((JNode) item).getValue();
            if (underlying instanceof AbstractMapType || underlying instanceof ArrayType) {
                return new JNode(underlying).toSequence();
            }
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jtree: JNode value is not a map or array");
        }
        if (item instanceof AbstractMapType || item instanceof ArrayType) {
            return new JNode((Sequence) item).toSequence();
        }
        // Atomic / non-JSON-container input → type error
        throw new XPathException(this, ErrorCodes.XPTY0004,
                "fn:jtree: argument must be a map or array, got " + Type.getTypeName(item.getType()));
    }

    private Sequence evalJkey(final Sequence arg) throws XPathException {
        if (arg.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        if (arg.getItemCount() > 1) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jkey: expected at most one item, got " + arg.getItemCount());
        }
        final Item item = arg.itemAt(0);
        if (!(item instanceof JNode)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jkey: argument must be a JNode, got " + Type.getTypeName(item.getType()));
        }
        final JNode jnode = (JNode) item;
        if (jnode.isRoot()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        // For map members: return the key; for array items: return the position
        final AtomicValue key = jnode.getKey();
        if (key != null) {
            return key;
        }
        // Array item: selector is the position (xs:integer)
        return new IntegerValue(this, jnode.getPosition());
    }

    private Sequence evalJvalue(final Sequence arg) throws XPathException {
        if (arg.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        if (arg.getItemCount() > 1) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jvalue: expected at most one item, got " + arg.getItemCount());
        }
        final Item item = arg.itemAt(0);
        if (item instanceof JNode) {
            return ((JNode) item).getValue();
        }
        // Real XML/persistent nodes are not JSON nodes → error
        if (Type.subTypeOf(item.getType(), Type.NODE)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jvalue: argument must be a JNode, got " + Type.getTypeName(item.getType()));
        }
        // Atomic / map / array passthrough — acts as identity for values produced
        // during JSON path navigation that have already been atomized.
        return item.toSequence();
    }

    private Sequence evalJposition(final Sequence arg) throws XPathException {
        if (arg.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        if (arg.getItemCount() > 1) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jposition: expected at most one item, got " + arg.getItemCount());
        }
        final Item item = arg.itemAt(0);
        if (!(item instanceof JNode)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jposition: argument must be a JNode, got " + Type.getTypeName(item.getType()));
        }
        final JNode jnode = (JNode) item;
        if (jnode.isRoot()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        return new IntegerValue(this, jnode.getPosition());
    }

    private Sequence evalJchildren(final Sequence arg) throws XPathException {
        if (arg.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final Item item = arg.itemAt(0);
        if (!(item instanceof JNode)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jchildren: argument must be a JNode, got " + Type.getTypeName(item.getType()));
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
        if (arg.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final Item item = arg.itemAt(0);
        if (!(item instanceof JNode)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:jparent: argument must be a JNode, got " + Type.getTypeName(item.getType()));
        }
        final JNode jnode = (JNode) item;
        final JNode parent = jnode.getParent();
        return parent != null ? parent.toSequence() : Sequence.EMPTY_SEQUENCE;
    }
}
