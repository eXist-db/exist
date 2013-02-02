/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2013 The eXist-db Project
 *  http://exist-db.org
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
import java.util.List;
import java.util.Map;
import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.inspect.InspectFunction;


/**
 * Module function definitions for util module.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author ljo
 * @author Andrzej Taramina (andrzej@chaeron.com)
 * @author Adam retter <adam.retter@googlemail.com>
 */
public class UtilModule extends AbstractInternalModule {
    
    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/util";

    public final static String PREFIX = "util";
    public final static String INCLUSION_DATE = "2004-09-12";
    public final static String RELEASED_IN_VERSION = "pre eXist-1.0";
    
    public boolean evalDisabled = false;

    public final static FunctionDef[] functions = {
        new FunctionDef(BuiltinFunctions.signatures[0], BuiltinFunctions.class),
        new FunctionDef(BuiltinFunctions.signatures[1], BuiltinFunctions.class),
        new FunctionDef(BuiltinFunctions.signatures[2], BuiltinFunctions.class),
        new FunctionDef(BuiltinFunctions.signatures[3], BuiltinFunctions.class),
        new FunctionDef(BuiltinFunctions.signatures[4], BuiltinFunctions.class),
        new FunctionDef(InspectFunction.SIGNATURE_DEPRECATED, InspectFunction.class),
        new FunctionDef(ModuleInfo.moduleDescriptionSig, ModuleInfo.class),
        new FunctionDef(ModuleInfo.registeredModuleSig, ModuleInfo.class),
        new FunctionDef(ModuleInfo.registeredModulesSig, ModuleInfo.class),
        new FunctionDef(ModuleInfo.mapModuleSig, ModuleInfo.class),
        new FunctionDef(ModuleInfo.unmapModuleSig, ModuleInfo.class),
        new FunctionDef(ModuleInfo.mappedModuleSig, ModuleInfo.class),
        new FunctionDef(ModuleInfo.mappedModulesSig, ModuleInfo.class),
        new FunctionDef(ModuleInfo.moduleInfoSig, ModuleInfo.class),
        new FunctionDef(ModuleInfo.moduleInfoWithURISig, ModuleInfo.class),
        new FunctionDef(Expand.signatures[0], Expand.class),
        new FunctionDef(Expand.signatures[1], Expand.class),
        new FunctionDef(DescribeFunction.signature, DescribeFunction.class),
        new FunctionDef(FunDoctype.signature, FunDoctype.class),
        new FunctionDef(Eval.signatures[0], Eval.class),
        new FunctionDef(Eval.signatures[1], Eval.class),
        new FunctionDef(Eval.signatures[2], Eval.class),
        new FunctionDef(Eval.signatures[3], Eval.class),
        new FunctionDef(Eval.signatures[4], Eval.class),
        new FunctionDef(Eval.signatures[5], Eval.class),
        new FunctionDef(Eval.signatures[6], Eval.class),
        new FunctionDef(Eval.signatures[7], Eval.class),
        new FunctionDef(Compile.signatures[0], Compile.class),
        new FunctionDef(Compile.signatures[1], Compile.class),
        new FunctionDef(Compile.signatures[2], Compile.class),
        new FunctionDef(DocumentNameOrId.docIdSignature, DocumentNameOrId.class),
        new FunctionDef(DocumentNameOrId.docNameSignature, DocumentNameOrId.class),
        new FunctionDef(DocumentNameOrId.absoluteResourceIdSignature, DocumentNameOrId.class),
        new FunctionDef(DocumentNameOrId.resourceByAbsoluteIdSignature, DocumentNameOrId.class),
        new FunctionDef(CollectionName.signature, CollectionName.class),
        new FunctionDef(LogFunction.signatures[0], LogFunction.class),
        new FunctionDef(LogFunction.signatures[1], LogFunction.class),
        new FunctionDef(LogFunction.signatures[2], LogFunction.class),
        new FunctionDef(LogFunction.signatures[3], LogFunction.class),
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
        new FunctionDef(IndexKeys.signatures[2], IndexKeys.class),
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
        new FunctionDef(BinaryDoc.signatures[2], BinaryDoc.class),
        new FunctionDef(BinaryToString.signatures[0], BinaryToString.class),
        new FunctionDef(BinaryToString.signatures[1], BinaryToString.class),
        new FunctionDef(BinaryToString.signatures[2], BinaryToString.class),
        new FunctionDef(BinaryToString.signatures[3], BinaryToString.class),
        new FunctionDef(Profile.signatures[0], Profile.class),
        new FunctionDef(Profile.signatures[1], Profile.class),
        new FunctionDef(PrologFunctions.signatures[0], PrologFunctions.class),
        new FunctionDef(PrologFunctions.signatures[1], PrologFunctions.class),
        new FunctionDef(PrologFunctions.signatures[2], PrologFunctions.class),
        new FunctionDef(PrologFunctions.signatures[3], PrologFunctions.class),
        new FunctionDef(SystemTime.signatures[0], SystemTime.class),
        new FunctionDef(SystemTime.signatures[1], SystemTime.class),
        new FunctionDef(SystemTime.signatures[2], SystemTime.class),
        new FunctionDef(RandomFunction.signatures[0], RandomFunction.class),
        new FunctionDef(RandomFunction.signatures[1], RandomFunction.class),
        new FunctionDef(RandomFunction.signatures[2], RandomFunction.class),
        new FunctionDef(FunUnEscapeURI.signature, FunUnEscapeURI.class),
        new FunctionDef(UUID.signatures[0], UUID.class),
        new FunctionDef(UUID.signatures[1], UUID.class),
        new FunctionDef(DeepCopyFunction.signature, DeepCopyFunction.class),
        new FunctionDef(GetSequenceType.signature, GetSequenceType.class),
        new FunctionDef(Parse.signatures[0], Parse.class),
        new FunctionDef(Parse.signatures[1], Parse.class),
        new FunctionDef(NodeXPath.signature, NodeXPath.class),
        new FunctionDef(Hash.signatures[0], Hash.class),
        new FunctionDef(Hash.signatures[1], Hash.class),
        new FunctionDef(GetFragmentBetween.signature, GetFragmentBetween.class),
        new FunctionDef(BaseConverter.signatures[0], BaseConverter.class),
        new FunctionDef(BaseConverter.signatures[1], BaseConverter.class),
        new FunctionDef(Wait.signatures[0], Wait.class),
        new FunctionDef(Base64Functions.signatures[0], Base64Functions.class),
        new FunctionDef(Base64Functions.signatures[1], Base64Functions.class),
        new FunctionDef(Base64Functions.signatures[2], Base64Functions.class),
        new FunctionDef(BaseConversionFunctions.FNS_INT_TO_OCTAL, BaseConversionFunctions.class),
        new FunctionDef(BaseConversionFunctions.FNS_OCTAL_TO_INT, BaseConversionFunctions.class)
            
    };

    static {
        Arrays.sort(functions, new FunctionComparator());
    }

    public final static QName EXCEPTION_QNAME = new QName("exception", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);

    public final static QName EXCEPTION_MESSAGE_QNAME = new QName("exception-message", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);

    public UtilModule(Map<String, List<? extends Object>> parameters) throws XPathException {
        super(functions, parameters, true);
        declareVariable(EXCEPTION_QNAME, null);
        declareVariable(EXCEPTION_MESSAGE_QNAME, null);
        
        final List<String> evalDisabledParamList = (List<String>)getParameter("evalDisabled");
        if(evalDisabledParamList != null && !evalDisabledParamList.isEmpty()) {
            final String strEvalDisabled = evalDisabledParamList.get(0);
            if(strEvalDisabled != null) {
                this.evalDisabled = Boolean.parseBoolean(strEvalDisabled);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getDescription()
     */
    @Override
    public String getDescription() {
        return "A module for various utility extension functions.";
    }


    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getNamespaceURI()
     */
    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }


    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getDefaultPrefix()
     */
    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }


    @Override
    public String getReleaseVersion(){
        return RELEASED_IN_VERSION;
    }
    
    public boolean isEvalDisabled() {
        return evalDisabled;
    }
}
