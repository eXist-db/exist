/*
Copyright (c) 2015, Adam Retter, Evolved Binary Ltd
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the copyright holder nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.xpdl;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * eXist Bridge for an XPDL Extension Module
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class XpdlExtensionModuleBridge extends AbstractInternalModule {

    private final String namespaceUri;
    private final String defaultPrefix;
    private final String description;

    private XpdlExtensionModuleBridge(final String namespaceUri, final String defaultPrefix, final String description, final FunctionDef[] functions, final Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
        this.namespaceUri = namespaceUri;
        this.defaultPrefix = defaultPrefix;
        this.description = description;
    }

    public static XpdlExtensionModuleBridge from(final xpdl.extension.Module module, final Map<String, List<? extends Object>> parameters) throws InstantiationException, IllegalAccessException {
        final List<FunctionDef> functions = new ArrayList<>();

        for (final _List.ListIterator<Class> it = module.functions().iterator(); it.hasNext(); ) {
            final Class<xpdl.extension.xpath.Function> fnClass = it.next();
            functions.addAll(Arrays.asList(fnFrom(fnClass)));
        }

        final String moduleNs = functions.get(0).getSignature().getName().getNamespaceURI();
        final String moduleDefaultPrefix = functions.get(0).getSignature().getName().getPrefix();

        return new XpdlExtensionModuleBridge(moduleNs, moduleDefaultPrefix, module.description(), functions.toArray(new FunctionDef[functions.size()]), parameters);
    }

    /**
     * Returns one or more function defs for a named
     * function based on the number of parameter lists of that function
     */
    private static FunctionDef[] fnFrom(final Class<xpdl.extension.xpath.Function> fnClass) throws IllegalAccessException, InstantiationException {
        final xpdl.extension.xpath.Function fn = fnClass.newInstance();
        final SequenceType[][] parameterLists = parameterListsFrom(fn.signature().paramLists);

        final FunctionDef[] functionDefs = new FunctionDef[parameterLists.length];

        //TODO Option 1 generate bridge class at runtime!
        //TODO Option 2 have the xpdl class implement eXist's interface at runtime!
        //TODO Option 3 Implement the xpdl interfaces in eXists own XDM type model

        //TODO(AR) at the moment we use one BasicFunction proxy class for many parameter lists of the same function, we could use multiple proxies if it is easier?!

        //here is option 1
        final Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(BasicFunction.class);
        enhancer.setCallbackTypes(new Class[]{MethodInterceptor.class});
        final Class fnBridgeClazz = enhancer.createClass();

        Enhancer.registerCallbacks(fnBridgeClazz, new Callback[] {
                (MethodInterceptor) (obj, method, args, proxy) -> {
                    if(method.getName().equals("eval") && method.getParameterTypes()[0].equals(Sequence[].class) && method.getParameterTypes()[1].equals(Sequence.class)) {

                        //eval the XPDL extension function
                        try {
                            return fromResult(fn.eval(toArguments((Sequence[]) args[0]), null));
                        } catch(final XpdlExtensionException e) {
                            final Throwable t = e.getCause();
                            if(t instanceof XPathException) {
                                throw t;
                            } else {
                                throw new XPathException(e);
                            }
                        }

                    } else {
                        return proxy.invokeSuper(obj, args);
                    }
                }
        });

        int i = 0;
        for(final SequenceType[] parameterList : parameterLists) {
            final FunctionSignature signature = new FunctionSignature(
                    nameFrom(fn.signature().name),
                    parameterList,
                    returnTypeFrom(fn.signature().returnType));

            functionDefs[i++] = new FunctionDef(signature, fnBridgeClazz);
        }

        return functionDefs;
    }

    private static Sequence fromResult(final xpdl.xdm.Sequence seq) throws XPathException {
        final Sequence sequence = new ValueSequence();
        final xpdl.support.Iterator<xpdl.xdm.Item> it = seq.iterator();
        while(it.hasNext()) {
            sequence.add(fromType(it.next()));
        }
        return sequence;
    }

    private static haxe.root.Array<xpdl.extension.xpath.Argument> toArguments(final Sequence[] args) {
        final haxe.root.Array<xpdl.extension.xpath.Argument> arguments = new  haxe.root.Array<>();
        for(final Sequence arg : args) {
            arguments.push(toArgument(arg));
        }
        return arguments;
    }

    /**
     * @throws org.exist.xpdl.XpdlExtensionException
     */
    private static xpdl.extension.xpath.Argument toArgument(final Sequence arg) {
        return new xpdl.extension.xpath.Argument() {
            @Override
            public xpdl.xdm.Sequence getArgument() {
                return new xpdl.xdm.Sequence() {
                    @Override
                    public xpdl.support.Iterator<xpdl.xdm.Item> iterator() {
                        try {
                            final SequenceIterator it = arg.iterate();
                            return new xpdl.support.Iterator<xpdl.xdm.Item>() {
                                @Override
                                public boolean hasNext() {
                                    return it.hasNext();
                                }

                                @Override
                                public xpdl.xdm.Item next() {
                                    return toType(it.nextItem());
                                }
                            };
                        } catch(final XPathException e) {
                            throw new XpdlExtensionException(e);
                        }
                    }
                };
            }
        };
    }

    private static Item fromType(final xpdl.xdm.Item item) {
        if(xpdl.xdm.String.class.equals(item.getClass())) {
            return new StringValue(((xpdl.xdm.String)item).haxe());
        } else if(xpdl.xdm.Boolean.class.equals(item.getClass())) {
            return new BooleanValue(((xpdl.xdm.Boolean)item).haxe());
        } else {
            return new UntypedAtomicValue(item.stringValue().haxe());
        }
    }

    /**
     * @throws org.exist.xpdl.XpdlExtensionException
     */
    private static xpdl.xdm.Item toType(final Item item) {
        try {
            switch (item.getType()) {

                case Type.STRING:
                    return new xpdl.xdm.String(item.getStringValue());

                case Type.BOOLEAN:
                    return new xpdl.xdm.Boolean(item.toJavaObject(boolean.class));

                case Type.ITEM:
                default:
                    return new xpdl.xdm.Item() {
                        @Override
                        public xpdl.xdm.String stringValue() {
                            try {
                                return new xpdl.xdm.String(item.getStringValue());
                            } catch (final XPathException e) {
                                throw new XpdlExtensionException(e);
                            }
                        }
                    };
            }
        } catch(final XPathException e) {
            throw new XpdlExtensionException(e);
        }
    }

    private static QName nameFrom(final xpdl.extension.xpath.QName name) {
        return new QName(name.localPart, name.namespaceUri, name.prefix);
    }

    //TODO produces multiple sequenceTypes!
    private static SequenceType[][] parameterListsFrom(final haxe.root.Array<haxe.root.Array<xpdl.extension.xpath.Param>> paramLists) {

        final SequenceType[][] parameterLists = new SequenceType[paramLists.length][];

        int i = 0;
        for(final haxe.root.Array<xpdl.extension.xpath.Param> paramList : paramLists.__a) {
            final SequenceType[] parameterList = new SequenceType[paramList.length];
            int j = 0;
            for(final xpdl.extension.xpath.Param param : paramList.__a) {
                parameterList[j++] = new FunctionParameterSequenceType(
                        param.name.localPart,
                        typeFrom(param.type),
                        cardinalityFrom(param.type),
                        param.description
                );
            }
            parameterLists[i++] = parameterList;
        }

        return parameterLists;
    }

    //haxe.ds.Option
    private final static int SOME = 0;
    private final static int NONE = 1;

    private final static int typeFrom(final xpdl.extension.xpath.SequenceType type) {
        if(type.type.index == NONE) {
            return Type.EMPTY;
        } else {
            final xpdl.extension.xpath.ItemOccurrence itemOccurrence = (xpdl.extension.xpath.ItemOccurrence)type.type.getParams().__a[0];
            if(xpdl.xdm.String.class == itemOccurrence.itemType) {
                return Type.STRING;
            } else if(xpdl.xdm.Boolean.class == itemOccurrence.itemType) {
                return Type.BOOLEAN;
            } else {
                return Type.ITEM;
            }
        }
    }

    private final static int cardinalityFrom(final xpdl.extension.xpath.SequenceType type) {
        if(type.type.index == NONE) {
            return Cardinality.ZERO;
        } else {
            final xpdl.extension.xpath.ItemOccurrence itemOccurrence = (xpdl.extension.xpath.ItemOccurrence)type.type.getParams().__a[0];
            switch(itemOccurrence.occurrenceIndicator) {
                case ZERO_OR_ONE:
                    return Cardinality.ZERO_OR_ONE;

                case ONE_OR_MORE:
                    return Cardinality.ONE_OR_MORE;

                case ZERO_OR_MORE:
                    return Cardinality.ZERO_OR_MORE;

                default:
                case ONE:
                    return Cardinality.ONE;
            }
        }
    }

    private static SequenceType returnTypeFrom(final xpdl.extension.xpath.SequenceType returnType) {
        return new FunctionReturnSequenceType(
                typeFrom(returnType),
                cardinalityFrom(returnType),
                null
        );
    }

    @Override
    public String getNamespaceURI() {
        return namespaceUri;
    }

    @Override
    public String getDefaultPrefix() {
        return defaultPrefix;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getReleaseVersion() {
        return "3.0";
    }
}
