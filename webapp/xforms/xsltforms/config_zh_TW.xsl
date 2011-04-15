<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>zh_TW</language>
			<calendar.day0>星期一</calendar.day0>
			<calendar.day1>星期二</calendar.day1>
			<calendar.day2>星期三</calendar.day2>
			<calendar.day3>星期四</calendar.day3>
			<calendar.day4>星期五</calendar.day4>
			<calendar.day5>星期六</calendar.day5>
			<calendar.day6>星期日</calendar.day6>
			<calendar.initDay>6</calendar.initDay>
			<calendar.month0>1 月</calendar.month0>
			<calendar.month1>2 月</calendar.month1>
			<calendar.month2>3 月</calendar.month2>
			<calendar.month3>4 月</calendar.month3>
			<calendar.month4>5 月</calendar.month4>
			<calendar.month5>6 月</calendar.month5>
			<calendar.month6>7 月</calendar.month6>
			<calendar.month7>8 月</calendar.month7>
			<calendar.month8>9 月</calendar.month8>
			<calendar.month9>10 月</calendar.month9>
			<calendar.month10>11 月</calendar.month10>
			<calendar.month11>12 月</calendar.month11>
			<format.date>MM/dd/yyyy</format.date>
			<format.datetime>MM/dd/yyyy hh:mm:ss</format.datetime>
			<format.decimal>.</format.decimal>
			<status>... 正在載入 ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>
