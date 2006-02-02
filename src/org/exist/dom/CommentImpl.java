package org.exist.dom;

import java.io.UnsupportedEncodingException;

import org.exist.storage.Signatures;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.StringValue;
import org.w3c.dom.Comment;
import org.w3c.dom.Node;

public class CommentImpl extends CharacterDataImpl implements Comment {

    public CommentImpl() {
    	super(Node.COMMENT_NODE);
    }
    
    public CommentImpl( long gid ) {
        super( Node.COMMENT_NODE, gid );
    }

    public CommentImpl( long gid, String data ) {
        super( Node.COMMENT_NODE, gid, data );
    }

    public CommentImpl( String data ) {
        super( Node.COMMENT_NODE, data );
    }

    public CommentImpl( char[] data, int start, int howmany ) {
        super( Node.COMMENT_NODE, data, start, howmany );
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append( "<!-- " );
        buf.append( cdata.toString() );
        buf.append( " -->" );
        return buf.toString();
    }

    public byte[] serialize() {
        String s;
        try {
            s = StringValue.expand(cdata);
        } catch (XPathException e) {
            s = cdata.toString();
        }
        byte[] cd;
        try {
            cd = s.getBytes( "UTF-8" );
        } catch ( UnsupportedEncodingException uee ) {
            cd = s.getBytes();
        }
        byte[] data = new byte[cd.length + 1];
        data[0] = (byte) ( Signatures.Comm << 0x5 );

        System.arraycopy( cd, 0, data, 1, cd.length );
        return data;
    }

    public static StoredNode deserialize( byte[] data, int start, int len, boolean pooled ) {
        String cdata;
        try {
            cdata = new String( data, start + 1, len - 1, "UTF-8" );
        } catch ( UnsupportedEncodingException uee ) {
            cdata = new String( data, start + 1, len - 1 );
        }
        CommentImpl comment;
        if(pooled)
            comment = (CommentImpl)
				NodeObjectPool.getInstance().borrowNode(CommentImpl.class);
        else
            comment = new CommentImpl();
        comment.appendData( cdata );
        return comment;
    }
    
    public boolean hasChildNodes() {
        return false;        
    }
    
    public Node getFirstChild() {   
        //bad implementations don't call hasChildNodes before
        return null;
    }        
 
}

