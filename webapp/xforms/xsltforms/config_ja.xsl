<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>ja</language>
			<calendar.day0>月</calendar.day0>
			<calendar.day1>火</calendar.day1>
			<calendar.day2>水</calendar.day2>
			<calendar.day3>木</calendar.day3>
			<calendar.day4>金</calendar.day4>
			<calendar.day5>土</calendar.day5>
			<calendar.day6>日</calendar.day6>
			<calendar.initDay>6</calendar.initDay>
			<calendar.month0>1月</calendar.month0>
			<calendar.month1>2月</calendar.month1>
			<calendar.month2>3月</calendar.month2>
			<calendar.month3>4月</calendar.month3>
			<calendar.month4>5月</calendar.month4>
			<calendar.month5>6月</calendar.month5>
			<calendar.month6>7月</calendar.month6>
			<calendar.month7>8月</calendar.month7>
			<calendar.month8>9月</calendar.month8>
			<calendar.month9>10月</calendar.month9>
			<calendar.month10>11月</calendar.month10>
			<calendar.month11>12月</calendar.month11>
			<format.date>yyyy/MM/dd</format.date>
			<format.datetime>yyyy/MM/dd hh:mm:ss</format.datetime>
			<format.decimal>.</format.decimal>
			<status>... 読み込み中 ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>