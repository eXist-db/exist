
package org.exist.dom;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.exist.storage.Signatures;
import org.exist.util.ByteConversion;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    9. Juli 2002
 */
public class ProcessingInstructionImpl extends NodeImpl implements ProcessingInstruction {

    protected String target;
    protected String data;


    /**  Constructor for the ProcessingInstructionImpl object */
    public ProcessingInstructionImpl() {
        super();
    }


    /**
     *  Constructor for the ProcessingInstructionImpl object
     *
     *@param  gid  Description of the Parameter
     */
    public ProcessingInstructionImpl( long gid ) {
        super( Node.PROCESSING_INSTRUCTION_NODE, gid );
    }


    /**
     *  Constructor for the ProcessingInstructionImpl object
     *
     *@param  gid     Description of the Parameter
     *@param  target  Description of the Parameter
     *@param  data    Description of the Parameter
     */
    public ProcessingInstructionImpl( long gid, String target, String data ) {
        super( Node.PROCESSING_INSTRUCTION_NODE, gid );
        this.target = target;
        this.data = data;
    }


    /**
     *  Gets the target attribute of the ProcessingInstructionImpl object
     *
     *@return    The target value
     */
    public String getTarget() {
        return target;
    }


    /**
     *  Sets the target attribute of the ProcessingInstructionImpl object
     *
     *@param  target  The new target value
     */
    public void setTarget( String target ) {
        this.target = target;
    }


    /**
     *  Gets the data attribute of the ProcessingInstructionImpl object
     *
     *@return    The data value
     */
    public String getData() {
        return data;
    }


    /**
     *  Sets the data attribute of the ProcessingInstructionImpl object
     *
     *@param  data  The new data value
     */
    public void setData( String data ) {
        this.data = data;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append( "<?" );
        buf.append( target );
        buf.append( " " );
        buf.append( data );
        buf.append( " ?>" );
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
            ArrayList prefixes )
             throws SAXException {
        contentHandler.processingInstruction( getTarget(), getData() );
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public byte[] serialize() {
        byte[] td;
        byte[] dd;
        try {
            td = target.getBytes( "UTF-8" );
            dd = data.getBytes( "UTF-8" );
        } catch ( UnsupportedEncodingException uee ) {
            td = target.getBytes();
            dd = data.getBytes();
        }
        byte[] d = new byte[td.length + dd.length + 5];
        d[0] = (byte) ( Signatures.Proc << 0x5 );

        ByteConversion.intToByte( td.length, d, 1 );
        System.arraycopy( td, 0, d, 5, td.length );
        System.arraycopy( dd, 0, d, 5 + td.length, dd.length );
        return d;
    }


    /**
     *  Description of the Method
     *
     *@param  data  Description of the Parameter
     *@return       Description of the Return Value
     */
    public static NodeImpl deserialize( byte[] data, int start, int len ) {
        int l = ByteConversion.byteToInt( data, start + 1 );
        String target;
        String cdata;
        try {
            target = new String( data, start + 5, l, "UTF-8" );
            cdata = new String( data, start + 5 + l, len - 5 - l, "UTF-8" );
        } catch ( UnsupportedEncodingException uee ) {
            target = new String( data, start + 5, l );
            cdata = new String( data, start + 5 + l, len - 5 - l );
        }
        return new ProcessingInstructionImpl( 0, target, cdata );
    }

}

