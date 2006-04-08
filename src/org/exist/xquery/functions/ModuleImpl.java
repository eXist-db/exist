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
package org.exist.xquery.functions;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionDef;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class ModuleImpl extends AbstractInternalModule {

	public final static String PREFIX = "";
	
	public final static FunctionDef[] functions = {
		new FunctionDef(FunAbs.signature, FunAbs.class),
		new FunctionDef(FunAvg.signature, FunAvg.class),
		new FunctionDef(FunBaseURI.signatures[0], FunBaseURI.class),
        new FunctionDef(FunBaseURI.signatures[1], FunBaseURI.class),
		new FunctionDef(FunBoolean.signature, FunBoolean.class),
		new FunctionDef(FunCeiling.signature, FunCeiling.class),
		new FunctionDef(FunCodepointsToString.signature, FunCodepointsToString.class),
		new FunctionDef(FunCompare.signatures[0], FunCompare.class),
		new FunctionDef(FunCompare.signatures[1], FunCompare.class),		
		new FunctionDef(FunConcat.signature, FunConcat.class),
		new FunctionDef(FunContains.signatures[0], FunContains.class),
		new FunctionDef(FunContains.signatures[1], FunContains.class),
		new FunctionDef(FunCount.signature, FunCount.class),
		new FunctionDef(FunCurrentDate.signature, FunCurrentDate.class),
		new FunctionDef(FunCurrentDateTime.signature, FunCurrentDateTime.class),
		new FunctionDef(FunCurrentTime.signature, FunCurrentTime.class),
		new FunctionDef(FunData.signature, FunData.class),
		new FunctionDef(FunDeepEqual.signature, FunDeepEqual.class),
		new FunctionDef(FunDistinctValues.signature, FunDistinctValues.class),
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
		new FunctionDef(FunGetDateComponent.fnGetDayFromDate, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnGetMonthFromDate, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnGetYearFromDate, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnGetTimezoneFromDate, FunGetDateComponent.class),
		new FunctionDef(FunGetTimeComponent.fnHoursFromTime, FunGetTimeComponent.class),
		new FunctionDef(FunGetTimeComponent.fnMinutesFromTime, FunGetTimeComponent.class),
		new FunctionDef(FunGetTimeComponent.fnSecondsFromTime, FunGetTimeComponent.class),
		new FunctionDef(FunGetTimeComponent.fnTimezoneFromTime, FunGetTimeComponent.class),
		new FunctionDef(FunGetDateTimeComponent.fnGetDayFromDateTime, FunGetDateTimeComponent.class),
		new FunctionDef(FunGetDateTimeComponent.fnGetMonthFromDateTime, FunGetDateTimeComponent.class),
		new FunctionDef(FunGetDateTimeComponent.fnGetYearFromDateTime, FunGetDateTimeComponent.class),
		new FunctionDef(FunGetDateTimeComponent.fnHoursFromDateTime, FunGetDateTimeComponent.class),
		new FunctionDef(FunGetDateTimeComponent.fnMinutesFromDateTime, FunGetDateTimeComponent.class),
		new FunctionDef(FunGetDateTimeComponent.fnSecondsFromDateTime, FunGetDateTimeComponent.class),
		new FunctionDef(FunGetDateTimeComponent.fnTimezoneFromDateTime, FunGetDateTimeComponent.class),
                new FunctionDef(FunGetYearsFromDayTimeDuration.signature, FunGetYearsFromDayTimeDuration.class),
                new FunctionDef(FunGetMonthsFromDayTimeDuration.signature, FunGetMonthsFromDayTimeDuration.class),
		new FunctionDef(FunGetDaysFromDayTimeDuration.signature, FunGetDaysFromDayTimeDuration.class),
		new FunctionDef(FunGetHoursFromDayTimeDuration.signature, FunGetHoursFromDayTimeDuration.class),
		new FunctionDef(FunGetMinutesFromDayTimeDuration.signature, FunGetMinutesFromDayTimeDuration.class),
		new FunctionDef(FunGetSecondsFromDayTimeDuration.signature, FunGetSecondsFromDayTimeDuration.class),
		new FunctionDef(FunAdjustTimezone.fnAdjustDateToTimezone[0], FunAdjustTimezone.class),
		new FunctionDef(FunAdjustTimezone.fnAdjustDateToTimezone[1], FunAdjustTimezone.class),
		new FunctionDef(FunAdjustTimezone.fnAdjustTimeToTimezone[0], FunAdjustTimezone.class),
		new FunctionDef(FunAdjustTimezone.fnAdjustTimeToTimezone[1], FunAdjustTimezone.class),
		new FunctionDef(FunAdjustTimezone.fnAdjustDateTimeToTimezone[0], FunAdjustTimezone.class),
		new FunctionDef(FunAdjustTimezone.fnAdjustDateTimeToTimezone[1], FunAdjustTimezone.class),
		new FunctionDef(FunId.signature, FunId.class),
		new FunctionDef(FunImplicitTimezone.signature, FunImplicitTimezone.class),
		new FunctionDef(FunIndexOf.fnIndexOf[0], FunIndexOf.class),
		new FunctionDef(FunIndexOf.fnIndexOf[1], FunIndexOf.class),
		new FunctionDef(FunIRIToURI.signature, FunIRIToURI.class),
		new FunctionDef(FunItemAt.signature, FunItemAt.class),
		new FunctionDef(FunInScopePrefixes.signature, FunInScopePrefixes.class),
		new FunctionDef(FunInsertBefore.signature, FunInsertBefore.class),
		new FunctionDef(FunLang.signature, FunLang.class),
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
		new FunctionDef(FunNormalizeSpace.signatures[0], FunNormalizeSpace.class),
		new FunctionDef(FunNormalizeSpace.signatures[1], FunNormalizeSpace.class),
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
		new FunctionDef(FunTranslate.signature, FunTranslate.class),
		new FunctionDef(FunTrueOrFalse.fnTrue, FunTrueOrFalse.class),
		new FunctionDef(FunTrueOrFalse.fnFalse, FunTrueOrFalse.class),
		new FunctionDef(FunUpperOrLowerCase.fnLowerCase, FunUpperOrLowerCase.class),
		new FunctionDef(FunUpperOrLowerCase.fnUpperCase, FunUpperOrLowerCase.class),
		new FunctionDef(FunZeroOrOne.signature, FunZeroOrOne.class),
		new FunctionDef(FunUnordered.signature, FunUnordered.class),
		new FunctionDef(ExtCollection.signature, ExtCollection.class),
		new FunctionDef(ExtXCollection.signature, ExtXCollection.class),
		new FunctionDef(ExtDoctype.signature, ExtDoctype.class),
		new FunctionDef(ExtDocument.signature, ExtDocument.class),
		new FunctionDef(ExtRegexp.signature, ExtRegexp.class),
		new FunctionDef(ExtRegexpOr.signature, ExtRegexpOr.class),
		new FunctionDef(QNameFunctions.localNameFromQName, QNameFunctions.class),
		new FunctionDef(QNameFunctions.prefixFromQName, QNameFunctions.class),
		new FunctionDef(QNameFunctions.namespaceURIFromQName, QNameFunctions.class)
	};
	
	public ModuleImpl() {
		super(functions);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() {
		return "XQuery/XPath Core Library Functions";
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
	
}
