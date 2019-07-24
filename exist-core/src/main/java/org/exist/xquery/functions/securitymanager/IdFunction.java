/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2015 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xquery.functions.securitymanager;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.security.Subject;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 *
 */
public class IdFunction extends BasicFunction {

    public final static FunctionSignature FNS_ID = new FunctionSignature(
        new QName("id", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX),    
        "Returns the user and group names of the account executing the XQuery. " +
        "If the real and effective accounts are different, then both the real " +
        "and effective account details are returned, otherwise only the real " +
        "account details are returned.",
        null,
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "Example output when an XQuery is running setUid <id xmlns=\"http://exist-db.org/xquery/securitymanager\"><real><username>guest</username><groups><group>guest</group></groups></real><effective><username>admin</username><groups><group>dba</group></groups></effective></id>.")
    );

    public IdFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence args[], final Sequence contextSequence) throws XPathException {
        if(mySignature == FNS_ID) {
            return functionId();
        } else {
            throw new XPathException("Unknown function call: " + getSignature());
        }
    }
    
    /**
     * Returns a document describing the accounts of the executing process
     * 
     * @return An in-memory document describing the accounts
     */
    private org.exist.dom.memtree.DocumentImpl functionId() throws XPathException {
        final MemTreeBuilder builder = context.getDocumentBuilder();
        builder.startDocument();

        builder.startElement(new QName("id", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX), null);
        
        builder.startElement(new QName("real", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX), null);
        subjectToXml(builder, context.getRealUser());
        builder.endElement();

        if(context.getRealUser().getId() != context.getEffectiveUser().getId()) {
            builder.startElement(new QName("effective", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX), null);
            subjectToXml(builder, context.getEffectiveUser());
            builder.endElement();
        }

        builder.endElement();
        
        builder.endDocument();
        
        return builder.getDocument();
    }
    
    private void subjectToXml(final MemTreeBuilder builder, final Subject subject) {
        builder.startElement(new QName("username", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX), null);
        builder.characters(subject.getName());
        builder.endElement();
        builder.startElement(new QName("groups", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX), null);
        for(final String group : subject.getGroups()) {
            builder.startElement(new QName("group", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX), null);
            builder.characters(group);
            builder.endElement();
        }
        builder.endElement();
    }
}
