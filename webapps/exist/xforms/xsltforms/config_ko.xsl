<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>ko</language>
			<calendar.day0>월</calendar.day0>
			<calendar.day1>화</calendar.day1>
			<calendar.day2>수</calendar.day2>
			<calendar.day3>목</calendar.day3>
			<calendar.day4>금</calendar.day4>
			<calendar.day5>토</calendar.day5>
			<calendar.day6>일</calendar.day6>
			<calendar.initDay>6</calendar.initDay>
			<calendar.month0>1월</calendar.month0>
			<calendar.month1>2월</calendar.month1>
			<calendar.month2>3월</calendar.month2>
			<calendar.month3>4월</calendar.month3>
			<calendar.month4>5월</calendar.month4>
			<calendar.month5>6월</calendar.month5>
			<calendar.month6>7월</calendar.month6>
			<calendar.month7>8월</calendar.month7>
			<calendar.month8>9월</calendar.month8>
			<calendar.month9>10월</calendar.month9>
			<calendar.month10>11월</calendar.month10>
			<calendar.month11>12월</calendar.month11>
			<format.date>yyyy-MM-dd</format.date>
			<format.datetime>yyyy-MM-dd hh:mm:ss</format.datetime>
			<format.decimal>.</format.decimal>
			<status>... 로드하는 중 ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>
