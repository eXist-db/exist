/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
package org.exist.xpath.functions;

import org.exist.xpath.AbstractInternalModule;
import org.exist.xpath.FunctionDef;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class ModuleImpl extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://www.w3.org/2003/05/xpath-functions";
	
	public final static String PREFIX = "";
	
	public final static FunctionDef[] functions = {
		new FunctionDef(FunAbs.signature, FunAbs.class),
		new FunctionDef(FunAvg.signature, FunAvg.class),
		new FunctionDef(FunBaseURI.signature, FunBaseURI.class),
		new FunctionDef(FunBoolean.signature, FunBoolean.class),
		new FunctionDef(FunCeiling.signature, FunCeiling.class),
		new FunctionDef(FunConcat.signature, FunConcat.class),
		new FunctionDef(FunContains.signature, FunContains.class),
		new FunctionDef(FunCount.signature, FunCount.class),
		new FunctionDef(FunCurrentDate.signature, FunCurrentDate.class),
		new FunctionDef(FunCurrentDateTime.signature, FunCurrentDateTime.class),
		new FunctionDef(FunCurrentTime.signature, FunCurrentTime.class),
		new FunctionDef(FunData.signature, FunData.class),
		new FunctionDef(FunDistinctValues.signature, FunDistinctValues.class),
		new FunctionDef(FunDoc.signature, FunDoc.class),
		new FunctionDef(FunDocumentURI.signature, FunDocumentURI.class),
		new FunctionDef(FunEmpty.signature, FunEmpty.class),
		new FunctionDef(FunEndsWith.signature, FunEndsWith.class),
        new FunctionDef(FunError.signature, FunError.class),
		new FunctionDef(FunExactlyOne.signature, FunExactlyOne.class),
		new FunctionDef(FunExists.signature, FunExists.class),
		new FunctionDef(FunFalse.signature, FunFalse.class),
		new FunctionDef(FunFloor.signature, FunFloor.class),
		new FunctionDef(FunGetDayFromDate.signature, FunGetDayFromDate.class),
		new FunctionDef(FunGetDaysFromDayTimeDuration.signature, FunGetDaysFromDayTimeDuration.class),
		new FunctionDef(FunGetHoursFromDayTimeDuration.signature, FunGetHoursFromDayTimeDuration.class),
		new FunctionDef(FunGetMinutesFromDayTimeDuration.signature, FunGetMinutesFromDayTimeDuration.class),
		new FunctionDef(FunGetMonthFromDate.signature, FunGetMonthFromDate.class),
		new FunctionDef(FunGetSecondsFromDayTimeDuration.signature, FunGetSecondsFromDayTimeDuration.class),
		new FunctionDef(FunGetYearFromDate.signature, FunGetYearFromDate.class),
		new FunctionDef(FunId.signature, FunId.class),
		new FunctionDef(FunItemAt.signature, FunItemAt.class),
		new FunctionDef(FunLang.signature, FunLang.class),
		new FunctionDef(FunLast.signature, FunLast.class),
		new FunctionDef(FunLocalName.signature, FunLocalName.class),
		new FunctionDef(FunLowerCase.signature, FunLowerCase.class),
		new FunctionDef(FunMatches.signature, FunMatches.class),
		new FunctionDef(FunMax.signature, FunMax.class),
		new FunctionDef(FunMin.signature, FunMin.class),
		new FunctionDef(FunName.signature, FunName.class),
		new FunctionDef(FunNamespaceURI.signature, FunNamespaceURI.class),
		new FunctionDef(FunNormalizeSpace.signature, FunNormalizeSpace.class),
		new FunctionDef(FunNot.signature, FunNot.class),
		new FunctionDef(FunNumber.signature, FunNumber.class),
		new FunctionDef(FunOneOrMore.signature, FunOneOrMore.class),
		new FunctionDef(FunPosition.signature, FunPosition.class),
		new FunctionDef(FunReplace.signature, FunReplace.class),
		new FunctionDef(FunRoot.signature, FunRoot.class),
		new FunctionDef(FunRound.signature, FunRound.class),
		new FunctionDef(FunStartsWith.signature, FunStartsWith.class),
		new FunctionDef(FunString.signature, FunString.class),
		new FunctionDef(FunStringPad.signature, FunStringPad.class),
		new FunctionDef(FunStrLength.signature, FunStrLength.class),
		new FunctionDef(FunSubSequence.signature, FunSubSequence.class),
		new FunctionDef(FunSubstring.signature, FunSubstring.class),
		new FunctionDef(FunSubstringAfter.signature, FunSubstringAfter.class),
		new FunctionDef(FunSubstringBefore.signature, FunSubstringBefore.class),
		new FunctionDef(FunSum.signature, FunSum.class),
		new FunctionDef(FunTokenize.signature, FunTokenize.class),
		new FunctionDef(FunTranslate.signature, FunTranslate.class),
		new FunctionDef(FunTrue.signature, FunTrue.class),
		new FunctionDef(FunUpperCase.signature, FunUpperCase.class),
		new FunctionDef(FunZeroOrOne.signature, FunZeroOrOne.class),
		new FunctionDef(ExtCollection.signature, ExtCollection.class),
		new FunctionDef(ExtXCollection.signature, ExtXCollection.class),
		new FunctionDef(ExtDoctype.signature, ExtDoctype.class),
		new FunctionDef(ExtDocument.signature, ExtDocument.class),
		new FunctionDef(ExtRegexp.signature, ExtRegexp.class),
		new FunctionDef(ExtRegexpOr.signature, ExtRegexpOr.class)
	};
	
	public ModuleImpl() {
		super(functions);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Module#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Module#getDefaultPrefix()
	 */
	public String getDefaultPrefix() {
		return PREFIX;
	}
	
}
