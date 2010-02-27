<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>ro</language>
			<calendar.day0>Lun</calendar.day0>
			<calendar.day1>Mar</calendar.day1>
			<calendar.day2>Mie</calendar.day2>
			<calendar.day3>Joi</calendar.day3>
			<calendar.day4>Vin</calendar.day4>
			<calendar.day5>Sâm</calendar.day5>
			<calendar.day6>Dum</calendar.day6>
			<calendar.initDay>0</calendar.initDay>
			<calendar.month0>Ianurie</calendar.month0>
			<calendar.month1>Februarie</calendar.month1>
			<calendar.month2>Martie</calendar.month2>
			<calendar.month3>Aprilie</calendar.month3>
			<calendar.month4>Mai</calendar.month4>
			<calendar.month5>Iunie</calendar.month5>
			<calendar.month6>Iulie</calendar.month6>
			<calendar.month7>August</calendar.month7>
			<calendar.month8>Septembrie</calendar.month8>
			<calendar.month9>Octombrie</calendar.month9>
			<calendar.month10>Noiembrie</calendar.month10>
			<calendar.month11>Decembrie</calendar.month11>
			<format.date>dd/MM/yyyy</format.date>
			<format.datetime>dd/MM/yyyy hh:mm:ss</format.datetime>
			<format.decimal>,</format.decimal>
			<status>... Încărcare pagină ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>