package org.exist.dom;

import java.util.Stack;

/**
 * NodeProxyFactory.java enclosing_type
 * 
 * @author wolf
 *
 */
public class NodeProxyFactory {

    private static Stack stack = new Stack();
    
    public static void release( NodeProxy proxy ) {
        stack.push( proxy );
    }
    
    public static NodeProxy createInstance(
        DocumentImpl doc, long gid, short nodeType, long address) {
        if(stack.empty())
            return new NodeProxy(doc, gid, address);
        else {
            NodeProxy proxy = (NodeProxy)stack.pop();
            proxy.setDoc( doc );
            proxy.setGID( gid );
            proxy.setNodeType( nodeType );
            proxy.setInternalAddress( address );
            return proxy;
        }
    }
    
    public static NodeProxy createInstance( DocumentImpl doc, long gid) {
        return createInstance(doc, gid, (short)-1, -1);
    }
    
    public static NodeProxy createInstance( DocumentImpl doc, long gid, 
        short nodeType) {
        return createInstance( doc, gid, nodeType, -1 );
    }
    
    public static NodeProxy createInstance( NodeProxy p ) {
        return createInstance( p.doc, p.gid, p.getNodeType(), p.getNodeType() );
    }
}
