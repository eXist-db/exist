/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
 *  
 *  $Id$
 */
package org.exist.xquery.functions;

import java.util.Arrays;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionDef;

/**
 * Module function definitions for xpath-functions module.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author ljo
 */
public class ModuleImpl extends AbstractInternalModule {

	public final static String PREFIX = "";
    public final static String INCLUSION_DATE = "2004-01-29";
    public final static String RELEASED_IN_VERSION = "&lt; eXist-1.0";
	
	public final static FunctionDef[] functions = {
		new FunctionDef(FunAbs.signature, FunAbs.class),
		new FunctionDef(FunAvg.signature, FunAvg.class),
		new FunctionDef(FunBaseURI.signatures[0], FunBaseURI.class),
		new FunctionDef(FunBaseURI.signatures[1], FunBaseURI.class),
        new FunctionDef(FunBaseURI.signatures[2], FunBaseURI.class),
		new FunctionDef(FunBoolean.signature, FunBoolean.class),
		new FunctionDef(FunCeiling.signature, FunCeiling.class),
		new FunctionDef(FunCodepointEqual.signature, FunCodepointEqual.class),
		new FunctionDef(FunCodepointsToString.signature, FunCodepointsToString.class),
		new FunctionDef(FunCompare.signatures[0], FunCompare.class),
		new FunctionDef(FunCompare.signatures[1], FunCompare.class),		
		new FunctionDef(FunConcat.signature, FunConcat.class),
		new FunctionDef(FunContains.signatures[0], FunContains.class),
		new FunctionDef(FunContains.signatures[1], FunContains.class),
		new FunctionDef(FunCount.signature, FunCount.class),
		new FunctionDef(FunCurrentDateTime.fnCurrentDate, FunCurrentDateTime.class),
		new FunctionDef(FunCurrentDateTime.fnCurrentDateTime, FunCurrentDateTime.class),
		new FunctionDef(FunCurrentDateTime.fnCurrentTime, FunCurrentDateTime.class),
		new FunctionDef(FunData.signature, FunData.class),
		new FunctionDef(FunDateTime.signature, FunDateTime.class),		
		new FunctionDef(FunDeepEqual.signatures[0], FunDeepEqual.class),
		new FunctionDef(FunDeepEqual.signatures[1], FunDeepEqual.class),
		new FunctionDef(FunDefaultCollation.signature, FunDefaultCollation.class),		
		new FunctionDef(FunDistinctValues.signatures[0], FunDistinctValues.class),
		new FunctionDef(FunDistinctValues.signatures[1], FunDistinctValues.class),
		new FunctionDef(FunDoc.signature, FunDoc.class),
		new FunctionDef(FunDocAvailable.signature, FunDocAvailable.class),
		new FunctionDef(FunDocumentURI.signature, FunDocumentURI.class),
		new FunctionDef(FunEmpty.signature, FunEmpty.class),
		new FunctionDef(FunEncodeForURI.signature, FunEncodeForURI.class),
		new FunctionDef(FunEndsWith.signatures[0], FunEndsWith.class),
		new FunctionDef(FunEndsWith.signatures[1], FunEndsWith.class),
		new FunctionDef(FunError.signature[0], FunError.class),
		new FunctionDef(FunError.signature[1], FunError.class),
		new FunctionDef(FunError.signature[2], FunError.class),
		new FunctionDef(FunError.signature[3], FunError.class),
		new FunctionDef(FunEscapeHTMLURI.signature, FunEscapeHTMLURI.class),
		new FunctionDef(FunEscapeURI.signature, FunEscapeURI.class),
		new FunctionDef(FunExactlyOne.signature, FunExactlyOne.class),
		new FunctionDef(FunExists.signature, FunExists.class),
		new FunctionDef(FunFloor.signature, FunFloor.class),
		new FunctionDef(FunGetDateComponent.fnDayFromDate, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnMonthFromDate, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnYearFromDate, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnTimezoneFromDate, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnHoursFromTime, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnMinutesFromTime, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnSecondsFromTime, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnTimezoneFromTime, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnDayFromDateTime, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnMonthFromDateTime, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnYearFromDateTime, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnHoursFromDateTime, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnMinutesFromDateTime, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnSecondsFromDateTime, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnTimezoneFromDateTime, FunGetDateComponent.class),
		new FunctionDef(FunGetDurationComponent.fnYearsFromDuration, FunGetDurationComponent.class),
		new FunctionDef(FunGetDurationComponent.fnMonthsFromDuration, FunGetDurationComponent.class),
		new FunctionDef(FunGetDurationComponent.fnDaysFromDuration, FunGetDurationComponent.class),
		new FunctionDef(FunGetDurationComponent.fnHoursFromDuration, FunGetDurationComponent.class),
		new FunctionDef(FunGetDurationComponent.fnMinutesFromDuration, FunGetDurationComponent.class),
		new FunctionDef(FunGetDurationComponent.fnSecondsFromDuration, FunGetDurationComponent.class),
		new FunctionDef(FunAdjustTimezone.fnAdjustDateToTimezone[0], FunAdjustTimezone.class),
		new FunctionDef(FunAdjustTimezone.fnAdjustDateToTimezone[1], FunAdjustTimezone.class),
		new FunctionDef(FunAdjustTimezone.fnAdjustTimeToTimezone[0], FunAdjustTimezone.class),
		new FunctionDef(FunAdjustTimezone.fnAdjustTimeToTimezone[1], FunAdjustTimezone.class),
		new FunctionDef(FunAdjustTimezone.fnAdjustDateTimeToTimezone[0], FunAdjustTimezone.class),
		new FunctionDef(FunAdjustTimezone.fnAdjustDateTimeToTimezone[1], FunAdjustTimezone.class),
		new FunctionDef(FunId.signature[0], FunId.class),
        new FunctionDef(FunId.signature[1], FunId.class),
		new FunctionDef(FunIdRef.signature[0], FunIdRef.class),
        new FunctionDef(FunIdRef.signature[1], FunIdRef.class),
		new FunctionDef(FunImplicitTimezone.signature, FunImplicitTimezone.class),
		new FunctionDef(FunIndexOf.fnIndexOf[0], FunIndexOf.class),
		new FunctionDef(FunIndexOf.fnIndexOf[1], FunIndexOf.class),
		new FunctionDef(FunIRIToURI.signature, FunIRIToURI.class),
		new FunctionDef(DeprecatedFunItemAt.signature, DeprecatedFunItemAt.class),
		new FunctionDef(FunInScopePrefixes.signature, FunInScopePrefixes.class),
		new FunctionDef(FunInsertBefore.signature, FunInsertBefore.class),
		new FunctionDef(FunLang.signatures[0], FunLang.class),
		new FunctionDef(FunLang.signatures[1], FunLang.class),
		new FunctionDef(FunLast.signature, FunLast.class),
		new FunctionDef(FunLocalName.signatures[0], FunLocalName.class),
		new FunctionDef(FunLocalName.signatures[1], FunLocalName.class),
		new FunctionDef(FunMatches.signatures[0], FunMatches.class),
		new FunctionDef(FunMatches.signatures[1], FunMatches.class),
		new FunctionDef(FunMax.signatures[0], FunMax.class),
		new FunctionDef(FunMax.signatures[1], FunMax.class),
		new FunctionDef(FunMin.signatures[0], FunMin.class),
		new FunctionDef(FunMin.signatures[1], FunMin.class),
		new FunctionDef(FunNodeName.signature, FunNodeName.class),
		new FunctionDef(FunName.signatures[0], FunName.class),
		new FunctionDef(FunName.signatures[1], FunName.class),
		new FunctionDef(FunNamespaceURI.signatures[0], FunNamespaceURI.class),
		new FunctionDef(FunNamespaceURI.signatures[1], FunNamespaceURI.class),
		new FunctionDef(FunNamespaceURIForPrefix.signature, FunNamespaceURIForPrefix.class),
		new FunctionDef(FunNilled.signature, FunNilled.class),
		new FunctionDef(FunNormalizeSpace.signatures[0], FunNormalizeSpace.class),
		new FunctionDef(FunNormalizeSpace.signatures[1], FunNormalizeSpace.class),
		new FunctionDef(FunNormalizeUnicode.signatures[0], FunNormalizeUnicode.class),
		new FunctionDef(FunNormalizeUnicode.signatures[1], FunNormalizeUnicode.class),
		new FunctionDef(FunNot.signature, FunNot.class),
		new FunctionDef(FunNumber.signatures[0], FunNumber.class),
		new FunctionDef(FunNumber.signatures[1], FunNumber.class),
		new FunctionDef(FunOneOrMore.signature, FunOneOrMore.class),
		new FunctionDef(FunPosition.signature, FunPosition.class),
		new FunctionDef(FunQName.signature, FunQName.class),
		new FunctionDef(FunRemove.signature, FunRemove.class),
		new FunctionDef(FunReplace.signatures[0], FunReplace.class),
		new FunctionDef(FunReplace.signatures[1], FunReplace.class),
		new FunctionDef(FunReverse.signature, FunReverse.class),
		new FunctionDef(FunResolveURI.signatures[0], FunResolveURI.class),
		new FunctionDef(FunResolveURI.signatures[1], FunResolveURI.class),
		new FunctionDef(FunRoot.signatures[0], FunRoot.class),
		new FunctionDef(FunRoot.signatures[1], FunRoot.class),
		new FunctionDef(FunRound.signature, FunRound.class),
		new FunctionDef(FunRoundHalfToEven.signatures[0], FunRoundHalfToEven.class),
		new FunctionDef(FunRoundHalfToEven.signatures[1], FunRoundHalfToEven.class),
		new FunctionDef(FunStartsWith.signatures[0], FunStartsWith.class),
		new FunctionDef(FunStartsWith.signatures[1], FunStartsWith.class),
		new FunctionDef(FunString.signatures[0], FunString.class),
		new FunctionDef(FunString.signatures[1], FunString.class),
		new FunctionDef(FunStringJoin.signature, FunStringJoin.class),
		new FunctionDef(FunStringPad.signature, FunStringPad.class),
		new FunctionDef(FunStringToCodepoints.signature, FunStringToCodepoints.class),
		new FunctionDef(FunStrLength.signatures[0], FunStrLength.class),
		new FunctionDef(FunStrLength.signatures[1], FunStrLength.class),
		new FunctionDef(FunSubSequence.signatures[0], FunSubSequence.class),
		new FunctionDef(FunSubSequence.signatures[1], FunSubSequence.class),
		new FunctionDef(FunSubstring.signatures[0], FunSubstring.class),
		new FunctionDef(FunSubstring.signatures[1], FunSubstring.class),
		new FunctionDef(FunSubstringAfter.signatures[0], FunSubstringAfter.class),
		new FunctionDef(FunSubstringAfter.signatures[1], FunSubstringAfter.class),
		new FunctionDef(FunSubstringBefore.signatures[0], FunSubstringBefore.class),
		new FunctionDef(FunSubstringBefore.signatures[1], FunSubstringBefore.class),
		new FunctionDef(FunSum.signatures[0], FunSum.class),
		new FunctionDef(FunSum.signatures[1], FunSum.class),
		new FunctionDef(FunTokenize.signatures[0], FunTokenize.class),
		new FunctionDef(FunTokenize.signatures[1], FunTokenize.class),
		new FunctionDef(FunTrace.signature, FunTrace.class),
		new FunctionDef(FunTranslate.signature, FunTranslate.class),
		new FunctionDef(FunTrueOrFalse.fnTrue, FunTrueOrFalse.class),
		new FunctionDef(FunTrueOrFalse.fnFalse, FunTrueOrFalse.class),
		new FunctionDef(FunUpperOrLowerCase.fnLowerCase, FunUpperOrLowerCase.class),
		new FunctionDef(FunUpperOrLowerCase.fnUpperCase, FunUpperOrLowerCase.class),
		new FunctionDef(FunZeroOrOne.signature, FunZeroOrOne.class),
		new FunctionDef(FunUnordered.signature, FunUnordered.class),
		new FunctionDef(ExtCollection.signature, ExtCollection.class),
		new FunctionDef(DeprecatedExtXCollection.signature, DeprecatedExtXCollection.class),
		new FunctionDef(DeprecatedExtDoctype.signature, DeprecatedExtDoctype.class),
		new FunctionDef(DeprecatedExtDocument.signature, DeprecatedExtDocument.class),
		new FunctionDef(DeprecatedExtRegexp.signature, DeprecatedExtRegexp.class),
		new FunctionDef(DeprecatedExtRegexpOr.signature, DeprecatedExtRegexpOr.class),
		new FunctionDef(QNameFunctions.localNameFromQName, QNameFunctions.class),
		new FunctionDef(QNameFunctions.prefixFromQName, QNameFunctions.class),
		new FunctionDef(QNameFunctions.namespaceURIFromQName, QNameFunctions.class),
        new FunctionDef(FunResolveQName.signature, FunResolveQName.class)
	};
    
    static {
        Arrays.sort(functions, new FunctionComparator());
    }

    public ModuleImpl() {
		super(functions, true);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() {
		return "A module with the XQuery/XPath Core Library Functions";
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return Function.BUILTIN_FUNCTION_NS;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDefaultPrefix()
	 */
	public String getDefaultPrefix() {
		return PREFIX;
	}
	
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

}
