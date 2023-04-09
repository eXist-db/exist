/*
 * Copyright © 2001, Adam Retter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl.adapters;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.matcher.ElementMatchers;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeProxy;
import org.exist.xquery.Expression;
import org.exist.xquery.value.Type;
import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

import java.lang.reflect.InvocationTargetException;


/**
 * A NodeProxy Proxy which enhances NodeProxy
 * with a W3C DOM implementation by proxying to
 * its underlying typed node which is available
 * through NodeProxy.getNode()
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
class DomEnhancingNodeProxyAdapter {

    public static NodeProxy create(final NodeProxy nodeProxy) {

        final Class<? extends Node> domClazz = getNodeClass(nodeProxy);

        final DynamicType.Builder<? extends NodeProxy> byteBuddyBuilder = new ByteBuddy()
                .subclass(NodeProxy.class)
                .implement(domClazz)

                .method(ElementMatchers.isDeclaredBy(NodeProxy.class))
                .intercept(MethodCall.invokeSelf().on(nodeProxy).withAllArguments())

                .method(ElementMatchers.isDeclaredBy(domClazz).or(ElementMatchers.isDeclaredBy(Node.class)))
                .intercept(MethodCall.invokeSelf().on(nodeProxy.getNode()).withAllArguments())

                .method(ElementMatchers.isHashCode())
                .intercept(MethodCall.invokeSelf().on(nodeProxy));

        try {
            final NodeProxy nodeProxyProxy = byteBuddyBuilder
                    .make()
                    .load(nodeProxy.getClass().getClassLoader())
                    .getLoaded()
                    .getDeclaredConstructor(Expression.class, NodeHandle.class)
                    .newInstance(nodeProxy.getExpression(), nodeProxy);

            return nodeProxyProxy;
        } catch (final NoSuchMethodException | InstantiationException | IllegalAccessException |
                       InvocationTargetException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static Class<? extends Node> getNodeClass(final NodeProxy nodeProxy) {
        switch (nodeProxy.getType()) {
            case Type.ELEMENT:
                return Element.class;
            case Type.ATTRIBUTE:
                return Attr.class;
            case Type.TEXT:
                return Text.class;
            case Type.PROCESSING_INSTRUCTION:
                return ProcessingInstruction.class;
            case Type.COMMENT:
                return Comment.class;
            case Type.DOCUMENT:
                return Document.class;
            case Type.CDATA_SECTION:
                return CDATASection.class;
            default:
                return Node.class;
        }
    }
}
