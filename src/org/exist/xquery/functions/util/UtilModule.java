/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.util;

import java.util.Arrays;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.system.GetVersion;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class UtilModule extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/util";
	
	public final static String PREFIX = "util";
	
	public final static FunctionDef[] functions = {
		new FunctionDef(BuiltinFunctions.signatures[0], BuiltinFunctions.class),
		new FunctionDef(BuiltinFunctions.signatures[1], BuiltinFunctions.class),
		new FunctionDef(ModuleInfo.moduleDescriptionSig, ModuleInfo.class),
		new FunctionDef(ModuleInfo.registeredModulesSig, ModuleInfo.class),
		new FunctionDef(DescribeFunction.signature, DescribeFunction.class),
		new FunctionDef(FunDoctype.signature, FunDoctype.class),
		new FunctionDef(Eval.signatures[0], Eval.class),
		new FunctionDef(Eval.signatures[1], Eval.class),
		new FunctionDef(Eval.signatures[2], Eval.class),
		new FunctionDef(Eval.signatures[3], Eval.class),
		new FunctionDef(Eval.signatures[4], Eval.class),
		new FunctionDef(Compile.signature, Compile.class),
		new FunctionDef(FileRead.signatures[0], FileRead.class),
		new FunctionDef(FileRead.signatures[1], FileRead.class),
		new FunctionDef(MD5.signature, MD5.class),
		new FunctionDef(DocumentNameOrId.docIdSignature, DocumentNameOrId.class),
		new FunctionDef(DocumentNameOrId.docNameSignature, DocumentNameOrId.class),
		new FunctionDef(CollectionName.signature, CollectionName.class),
		new FunctionDef(LogFunction.signature, LogFunction.class),
		new FunctionDef(CatchFunction.signature, CatchFunction.class),
		new FunctionDef(ExclusiveLockFunction.signature, ExclusiveLockFunction.class),
		new FunctionDef(SharedLockFunction.signature, SharedLockFunction.class),
		new FunctionDef(Collations.signature, Collations.class),
		new FunctionDef(SystemProperty.signature, SystemProperty.class),
        new FunctionDef(FunctionFunction.signature, FunctionFunction.class),
        new FunctionDef(CallFunction.signature, CallFunction.class),
        new FunctionDef(NodeId.signature, NodeId.class),
        new FunctionDef(GetNodeById.signature, GetNodeById.class),
        new FunctionDef(IndexKeys.signatures[0], IndexKeys.class),
        new FunctionDef(IndexKeys.signatures[1], IndexKeys.class),
        new FunctionDef(IndexKeyOccurrences.signatures[0], IndexKeyOccurrences.class),
        new FunctionDef(IndexKeyOccurrences.signatures[1], IndexKeyOccurrences.class),
        new FunctionDef(IndexKeyDocuments.signatures[0], IndexKeyDocuments.class),
        new FunctionDef(IndexKeyDocuments.signatures[1], IndexKeyDocuments.class),
        new FunctionDef(IndexType.signature, IndexType.class),
        new FunctionDef(QNameIndexLookup.signature, QNameIndexLookup.class),
        new FunctionDef(Serialize.signatures[0], Serialize.class),
        new FunctionDef(Serialize.signatures[1], Serialize.class),
        new FunctionDef(BinaryDoc.signatures[0], BinaryDoc.class),
        new FunctionDef(BinaryDoc.signatures[1], BinaryDoc.class),
        new FunctionDef(BinaryToString.signatures[0], BinaryToString.class),
        new FunctionDef(BinaryToString.signatures[1], BinaryToString.class),
        new FunctionDef(Profile.signatures[0], Profile.class),
        new FunctionDef(Profile.signatures[1], Profile.class),
        new FunctionDef(PrologFunctions.signatures[0], PrologFunctions.class),
        new FunctionDef(PrologFunctions.signatures[1], PrologFunctions.class),
        new FunctionDef(PrologFunctions.signatures[2], PrologFunctions.class),
        new FunctionDef(SystemTime.signature, SystemTime.class),
        new FunctionDef(RandomFunction.signatures[0], RandomFunction.class),
        new FunctionDef(RandomFunction.signatures[1], RandomFunction.class),
        new FunctionDef(FunUnEscapeURI.signature, FunUnEscapeURI.class),
        new FunctionDef(UUID.signature, UUID.class),
        new FunctionDef(DeepCopyFunction.signature, DeepCopyFunction.class),
        
        // deprecated functions
        new FunctionDef(GetVersion.deprecated, GetVersion.class),
	};

    static {
        Arrays.sort(functions, new FunctionComparator());
    }

    public final static QName EXCEPTION_QNAME =
	    new QName("exception", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);

    public final static QName EXCEPTION_MESSAGE_QNAME = 
        new QName("exception-message", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);
    
	public UtilModule() throws XPathException {
		super(functions, true);
		declareVariable(EXCEPTION_QNAME, null);
        declareVariable(EXCEPTION_MESSAGE_QNAME, null);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() {
		return "Various utility extension functions";
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDefaultPrefix()
	 */
	public String getDefaultPrefix() {
		return PREFIX;
	}
}
