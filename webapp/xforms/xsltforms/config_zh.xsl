<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>zh</language>
			<calendar.day0>一</calendar.day0>
			<calendar.day1>二</calendar.day1>
			<calendar.day2>三</calendar.day2>
			<calendar.day3>四</calendar.day3>
			<calendar.day4>五</calendar.day4>
			<calendar.day5>六</calendar.day5>
			<calendar.day6>日</calendar.day6>
			<calendar.initDay>6</calendar.initDay>
			<calendar.month0>一月</calendar.month0>
			<calendar.month1>二月</calendar.month1>
			<calendar.month2>三月</calendar.month2>
			<calendar.month3>四月</calendar.month3>
			<calendar.month4>五月</calendar.month4>
			<calendar.month5>六月</calendar.month5>
			<calendar.month6>七月</calendar.month6>
			<calendar.month7>八月</calendar.month7>
			<calendar.month8>九月</calendar.month8>
			<calendar.month9>十月</calendar.month9>
			<calendar.month10>十一月</calendar.month10>
			<calendar.month11>十二月</calendar.month11>
			<format.date>yyyy-MM-dd</format.date>
			<format.datetime>yyyy-MM-dd hh:mm:ss</format.datetime>
			<format.decimal>.</format.decimal>
			<status>... 装载中 ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>
