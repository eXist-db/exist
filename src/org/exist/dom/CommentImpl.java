/*
 *  CommentImpl.java
 *
 *  Created on 14. Mai 2001, 12:09
 */
package org.exist.dom;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Set;

import org.exist.storage.Signatures;
import org.w3c.dom.Comment;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

/**
 *@author     klaus
 *@created    9. Juli 2002
 *@version
 */
public class CommentImpl extends CharacterDataImpl implements Comment {

    /**
     *  Constructor for the CommentImpl object
     *
     *@param  gid  Description of the Parameter
     */
    public CommentImpl( long gid ) {
        super( Node.COMMENT_NODE, gid );
    }


    /**
     *  Constructor for the CommentImpl object
     *
     *@param  gid   Description of the Parameter
     *@param  data  Description of the Parameter
     */
    public CommentImpl( long gid, String data ) {
        super( Node.COMMENT_NODE, gid, data );
    }


    /**
     *  Constructor for the CommentImpl object
     *
     *@param  data  Description of the Parameter
     */
    public CommentImpl( String data ) {
        super( Node.COMMENT_NODE, data );
    }


    /**
     *  Constructor for the CommentImpl object
     *
     *@param  data     Description of the Parameter
     *@param  start    Description of the Parameter
     *@param  howmany  Description of the Parameter
     */
    public CommentImpl( char[] data, int start, int howmany ) {
        super( Node.COMMENT_NODE, data, start, howmany );
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append( "<!-- " );
        buf.append( cdata );
        buf.append( " -->" );
        return buf.toString();
    }


    /**
     *  Description of the Method
     *
     *@param  contentHandler    Description of the Parameter
     *@param  lexicalHandler    Description of the Parameter
     *@param  first             Description of the Parameter
     *@param  prefixes          Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void toSAX( ContentHandler contentHandler,
            LexicalHandler lexicalHandler, boolean first,
            Set namespaces )
             throws SAXException {
        if ( lexicalHandler != null ) {
            char data[] = cdata.toString().toCharArray();
            lexicalHandler.comment( data, 0, data.length );
        }
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public byte[] serialize() {
        byte[] cd;
        try {
            cd = cdata.toString().getBytes( "UTF-8" );
        } catch ( UnsupportedEncodingException uee ) {
            cd = cdata.toString().getBytes();
        }
        byte[] data = new byte[cd.length + 1];
        data[0] = (byte) ( Signatures.Comm << 0x5 );

        System.arraycopy( cd, 0, data, 1, cd.length );
        return data;
    }


    /**
     *  Description of the Method
     *
     *@param  data  Description of the Parameter
     *@return       Description of the Return Value
     */
    public static NodeImpl deserialize( byte[] data, int start, int len ) {
        String cdata;
        try {
            cdata = new String( data, start + 1, len - 1, "UTF-8" );
        } catch ( UnsupportedEncodingException uee ) {
            cdata = new String( data, start + 1, len - 1 );
        }
        CommentImpl comment = new CommentImpl( 0 );
        comment.appendData( cdata );
        return comment;
    }
}

