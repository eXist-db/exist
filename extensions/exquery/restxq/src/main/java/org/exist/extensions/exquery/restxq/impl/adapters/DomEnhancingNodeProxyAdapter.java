/*
Copyright (c) 2012, Adam Retter
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Adam Retter Consulting nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl.adapters;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Dispatcher;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.NoOp;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeProxy;
import org.exist.xquery.value.Type;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

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
        
        final Class<? extends Node> clazzes[] = getNodeClasses(nodeProxy);
         
        // NoOp Callback is for NodeProxy calls
        // NodeDispatched Callback is for the underlying Node calls
        final Callback[] callbacks = {
          NoOp.INSTANCE,
          new NodeDispatcher(nodeProxy)
        };
        
        final CallbackFilter callbackFilter = method -> {

            final Class declaringClass = method.getDeclaringClass();

            //look for nodes
            boolean isMethodOnNode = false;
            if(declaringClass.equals(Node.class)) {
                isMethodOnNode = true;
            } else {
                //search parent interfaces
                for(final Class iface : declaringClass.getInterfaces()) {
                    if(iface.equals(Node.class)) {
                        isMethodOnNode = true;
                        break;
                    }
                }
            }

            if(isMethodOnNode) {
                return 1; //The NodeDispatcher
            } else {
                return 0; //The NoOp pass through
            }
        };
        
        final Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(NodeProxy.class);
        enhancer.setInterfaces(clazzes);
        enhancer.setCallbackFilter(callbackFilter);
        enhancer.setCallbacks(callbacks);
        
        return (NodeProxy)enhancer.create(
            new Class[] {
                NodeHandle.class
            },
            new Object[] {
                nodeProxy
            });
    }
    
    private static Class<? extends Node>[] getNodeClasses(final NodeProxy nodeProxy) {
        switch(nodeProxy.getType()) {
            
            case Type.DOCUMENT:
                return new Class[] {
                    Document.class,
                    Node.class
                };
            
            case Type.ELEMENT:
                return new Class[] {
                    Element.class,
                    Node.class
                };

            case Type.ATTRIBUTE:
                return new Class[] {
                    Attr.class,
                    Node.class
                };
                
            case Type.TEXT:
                return new Class[] {
                    Text.class,
                    Node.class
                };

            default:
                return new Class[] {
                    Node.class
                };
        }
    }
    
    public static class NodeDispatcher implements Dispatcher {
        
        private final NodeProxy nodeProxy;
        
        public NodeDispatcher(final NodeProxy nodeProxy) {
            this.nodeProxy = nodeProxy;
        }
        
        @Override
        public Object loadObject() throws Exception {
            return nodeProxy.getNode();
        }
    }
}