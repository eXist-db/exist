/****************************************************************************/
/*  File:       DomSequence.java                                            */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2015-01-08                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2015 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package org.expath.tools.model.dom;

import java.io.OutputStream;
import org.expath.tools.ToolsException;
import org.expath.tools.model.Sequence;
import org.expath.tools.serial.SerialParameters;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

/**
 * Trivial, in-memory implementation, for test purposes.
 *
 * @author Florent Georges
 * @date   2015-01-09
 */
public class DomSequence
        implements Sequence
{
    public DomSequence(NodeList nodes)
    {
        myNext = 0;
        myNodes = nodes;
    }

    @Override
    public boolean isEmpty() throws ToolsException
    {
        return myNext >= myNodes.getLength();
    }

    @Override
    public Sequence next() throws ToolsException
    {
        Node node = myNodes.item(myNext++);
        return new DomSingleton(node);
    }

    @Override
    public void serialize(OutputStream out, SerialParameters params)
            throws ToolsException
    {
        int length = myNodes.getLength();
        if ( length == 0 ) {
            return;
        }
        Document doc = myNodes.item(0).getOwnerDocument();
        DOMImplementationLS impl = (DOMImplementationLS) doc.getImplementation();
        LSSerializer serial = impl.createLSSerializer();
        LSOutput lsout = impl.createLSOutput();
        lsout.setByteStream(out);
        for ( int i = 0; i < myNodes.getLength(); ++i ) {
            serial.write(myNodes.item(i), lsout);
        }
    }

    NodeList getUnderlyingNodeList()
    {
        return myNodes;
    }

    private int myNext;
    private final NodeList myNodes;
}


/* ------------------------------------------------------------------------ */
/*  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS COMMENT.               */
/*                                                                          */
/*  The contents of this file are subject to the Mozilla Public License     */
/*  Version 1.0 (the "License"); you may not use this file except in        */
/*  compliance with the License. You may obtain a copy of the License at    */
/*  http://www.mozilla.org/MPL/.                                            */
/*                                                                          */
/*  Software distributed under the License is distributed on an "AS IS"     */
/*  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.  See    */
/*  the License for the specific language governing rights and limitations  */
/*  under the License.                                                      */
/*                                                                          */
/*  The Original Code is: all this file.                                    */
/*                                                                          */
/*  The Initial Developer of the Original Code is Florent Georges.          */
/*                                                                          */
/*  Contributor(s): none.                                                   */
/* ------------------------------------------------------------------------ */
