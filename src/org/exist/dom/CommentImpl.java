package org.exist.dom;

import org.exist.numbering.NodeId;
import org.exist.storage.Signatures;
import org.exist.util.ByteConversion;
import org.exist.util.pool.NodePool;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.StringValue;
import org.w3c.dom.Comment;
import org.w3c.dom.Node;

import java.io.UnsupportedEncodingException;

public class CommentImpl extends CharacterDataImpl implements Comment {

    public CommentImpl() {
        super(Node.COMMENT_NODE);
    }

    public CommentImpl( String data ) {
        super( Node.COMMENT_NODE, data );
    }

    public CommentImpl( char[] data, int start, int howmany ) {
        super( Node.COMMENT_NODE, data, start, howmany );
    }

    @Override
    public String getLocalName() {
        return "";
    }

    @Override
    public String getNamespaceURI() {
        return "";
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append( "<!-- " );
        buf.append( cdata.toString() );
        buf.append( " -->" );
        return buf.toString();
    }

    @Override
    public byte[] serialize() {
        String s;
        try {
            s = StringValue.expand(cdata);
        } catch (final XPathException e) {
            LOG.warn(e);
            s = cdata.toString();
        }
        byte[] cd;
        try {
            cd = s.getBytes( "UTF-8" );
        } catch (final UnsupportedEncodingException uee) {
            LOG.warn(uee);
            cd = s.getBytes();
        }
        int nodeIdLen = nodeId.size();
        final byte[] data = new byte[StoredNode.LENGTH_SIGNATURE_LENGTH + NodeId.LENGTH_NODE_ID_UNITS +
           + nodeIdLen + cd.length];
        int pos = 0;
        data[pos] = (byte) ( Signatures.Comm << 0x5 );
        pos += StoredNode.LENGTH_SIGNATURE_LENGTH;
        ByteConversion.shortToByte((short) nodeId.units(), data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        nodeId.serialize(data, pos);
        pos += nodeIdLen;
        System.arraycopy( cd, 0, data, pos, cd.length );
        return data;
    }

    public static StoredNode deserialize(byte[] data, int start, int len,
           DocumentImpl doc, boolean pooled) {
        int pos = start;
        pos += LENGTH_SIGNATURE_LENGTH;
        final int dlnLen = ByteConversion.byteToShort(data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        final NodeId dln = doc.getBrokerPool().getNodeFactory().createFromData(dlnLen, data, pos);
        int nodeIdLen = dln.size();
        pos += nodeIdLen;
        String cdata;
        try {
            cdata = new String(data, pos, len - (pos - start), "UTF-8" );
        } catch ( final UnsupportedEncodingException uee ) {
            LOG.warn(uee);
            cdata = new String(data, pos, len - (pos - start));
        }
        //OK : we have the necessary material to build the comment
        CommentImpl comment;
        if(pooled)
            {comment = (CommentImpl) NodePool.getInstance().borrowNode(Node.COMMENT_NODE);}
            //comment = (CommentImpl)NodeObjectPool.getInstance().borrowNode(CommentImpl.class);
        else
            {comment = new CommentImpl();}
        comment.setNodeId(dln);
        comment.appendData(cdata);
        return comment;
    }

    @Override
    public boolean hasChildNodes() {
        return false;
    }

    @Override
    public int getChildCount() {
        return 0;
    }

    @Override
    public Node getFirstChild() {
        //bad implementations don't call hasChildNodes before
        return null;
    }
}

