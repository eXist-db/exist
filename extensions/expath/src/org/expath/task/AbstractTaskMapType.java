/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.expath.task;

import com.evolvedbinary.j8fu.function.BiFunctionE;
import com.evolvedbinary.j8fu.function.FunctionE;
import com.evolvedbinary.j8fu.function.TriFunctionE;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.persistent.NodeHandle;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.exist.xquery.FunctionDSL.*;
import static org.expath.task.TaskModule.functionSignature;

public abstract class AbstractTaskMapType extends AbstractMapType {

    enum Key {
        apply("apply"),
        bind("bind"),
        then("then"),
        liftM0("liftM0"),
        liftM1("liftM1"),
        fmap("fmap"),
        sequence("sequence"),
        async("async"),
        catches("catches"),
        catches2("catches2"),
        CATCH("catch"),
        RUN_UNSAFE("RUN-UNSAFE");

        private final String keyString;

        Key(final String keyString) {
            this.keyString = keyString;
        }

        public String getKeyString() {
            return keyString;
        }

        public static Key fromKeyString(final String keyString) {
            for (final Key key : Key.values()) {
                if (key.getKeyString().equals(keyString)) {
                    return key;
                }
            }
            throw new IllegalArgumentException("Unknown keyString: " + keyString);
        }
    }

    private static final Set<String> ALL_KEYS = Stream.of(Key.values()).map(Key::getKeyString).collect(Collectors.toSet());
    private static final ValueSequence SEQ_KEYS = new ValueSequence();
    static {
        for (final Key key : Key.values()) {
            SEQ_KEYS.add(new StringValue(key.getKeyString()));
        }
    }
    private static final int SIZE = Key.values().length;


    public AbstractTaskMapType(final XQueryContext context) {
        super(context);
    }

    @Override
    public Sequence get(final AtomicValue key) {
        switch (Key.fromKeyString(key.toString())) {
            case apply:
                return apply();

            case bind:
                return bind();

            case then:
                return then();

            case async:
                return async();

            case RUN_UNSAFE:
                return runUnsafe();

            default:
                throw new UnsupportedOperationException("get(" + key.toString() + ") Not yet implemented!");
        }
    }

    @Override
    public Iterator<Map.Entry<AtomicValue, Sequence>> iterator() {
        throw new UnsupportedOperationException("iterator() Not yet implemented!");
    }

    @Override
    public AtomicValue getKey() {
        throw new UnsupportedOperationException("getKey() Not yet implemented!");
    }

    @Override
    public Sequence getValue() {
        throw new UnsupportedOperationException("getValue() Not yet implemented!");
    }

    @Override
    public AbstractMapType put(final AtomicValue key, final Sequence value) {
        // Task Type is immutable!
        return this;
    }

    @Override
    public boolean contains(final AtomicValue key) {
        return ALL_KEYS.contains(key.toString());
    }

    @Override
    public Sequence keys() {
        return SEQ_KEYS;
    }

    @Override
    public AbstractMapType remove(final AtomicValue key) {
        // Task Type is immutable!
        return this;
    }

    @Override
    public int getKeyType() {
        return Type.STRING;
    }

    @Override
    public int size() {
        return SIZE;
    }

    private FunctionReference apply() {
        final UserDefinedFunction xdmTaskApplyFunction = new UserDefinedFunction(context, functionSignature(
                "task-apply",
                "applies a task",
                returnsMany(Type.ITEM, "the results of the task"),
                param("realworld", Type.ITEM, "The real world")
        )) {
            @Override
            public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
                final Item realWorld = getCurrentArguments()[0].itemAt(0);
                return apply((RealWorld)realWorld);
            }
        };

        return new FunctionReference(new FunctionCall(context, xdmTaskApplyFunction));
    };

    private FunctionReference bind() {
        final UserDefinedFunction xdmTaskBindFunction = new JavaFunctionAsXdmFunction(context, functionSignature(
                "task-bind",
                "binds a task",
                returns(Type.MAP, "the resultant task"),
                param("binder", Type.FUNCTION_REFERENCE, "The binder function")
        )) {
            @Override
            public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
                final FunctionReference xdmBinderFunction = (FunctionReference)getCurrentArguments()[0].itemAt(0);
                return bind(context, value -> (TaskType)xdmBinderFunction.evalFunction(contextSequence, contextItem, new Sequence[]{value}));
            }
        };

        return new FunctionReference(new FunctionCall(context, xdmTaskBindFunction));
    }

    private FunctionReference then() {
        final UserDefinedFunction xdmTaskBindFunction = new JavaFunctionAsXdmFunction(context, functionSignature(
                "task-then",
                "then next task",
                returns(Type.MAP, "the resultant task"),
                param("next", Type.MAP, "The next task")
        )) {
            @Override
            public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
                final TaskType next = (TaskType)getCurrentArguments()[0].itemAt(0);
                return then(context, next);
            }
        };

        return new FunctionReference(new FunctionCall(context, xdmTaskBindFunction));
    }

    private FunctionReference async() {
        final UserDefinedFunction xdmTaskBindFunction = new JavaFunctionAsXdmFunction(context, functionSignature(
                "task-async",
                "asynchronously schedule a task",
                returns(Type.MAP, "the asynchronous task")
        )) {
            @Override
            public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
                return async(context);
            }
        };

        return new FunctionReference(new FunctionCall(context, xdmTaskBindFunction));
    }

    private FunctionReference runUnsafe() {
        final UserDefinedFunction xdmTaskRunUnsafeFunction = new JavaFunctionAsXdmFunction(context, functionSignature(
                "task-RUN-UNSAFE",
                "run the task UNSAFE",
                returnsOptMany(Type.ITEM, "the result of the execution of the task")
        )) {
            @Override
            public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
                return runUnsafe(FAKE_REAL_WORLD);
            }
        };

        return new FunctionReference(new FunctionCall(context, xdmTaskRunUnsafeFunction));
    }

    protected abstract Sequence apply(final RealWorld readWorld) throws XPathException;
    protected abstract TaskType bind(final XQueryContext context, final FunctionE<Sequence, TaskType, XPathException> binder);
    protected abstract TaskType then(final XQueryContext context, final TaskType next);
    protected abstract TaskType async(final XQueryContext context);
    protected abstract Sequence runUnsafe(final RealWorld readWorld) throws XPathException;

    private static final RealWorld FAKE_REAL_WORLD = new FakeRealWorld();
    private static class FakeRealWorld implements RealWorld {
        @Override
        public int getType() {
            return Type.ITEM;
        }

        @Override
        public String getStringValue() throws XPathException {
            return "fake-real-world";
        }

        @Override
        public Sequence toSequence() {
            return new ValueSequence(this);
        }

        @Override
        public void destroy(XQueryContext context, Sequence contextSequence) {

        }

        @Override
        public AtomicValue convertTo(final int requiredType) throws XPathException {
            return null;
        }

        @Override
        public AtomicValue atomize() throws XPathException {
            return null;
        }

        @Override
        public void toSAX(DBBroker broker, ContentHandler handler, Properties properties) throws SAXException {

        }

        @Override
        public void copyTo(DBBroker broker, DocumentBuilderReceiver receiver) throws SAXException {

        }

        @Override
        public int conversionPreference(Class<?> javaClass) {
            return 0;
        }

        @Override
        public <T> T toJavaObject(Class<T> target) throws XPathException {
            return null;
        }

        @Override
        public void nodeMoved(NodeId oldNodeId, NodeHandle newNode) {

        }
    }


    private static abstract class JavaFunctionAsXdmFunction extends UserDefinedFunction {
        public JavaFunctionAsXdmFunction(final XQueryContext context, final FunctionSignature signature) {
            super(context, signature);
        }

        @Override
        public void accept(final ExpressionVisitor visitor) {
            if (visited) {
                return;
            }
            visited = true;
        }
    };
}
