<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>gl</language>
			<calendar.day0>Lun</calendar.day0>
			<calendar.day1>Mar</calendar.day1>
			<calendar.day2>Mer</calendar.day2>
			<calendar.day3>Xov</calendar.day3>
			<calendar.day4>Ven</calendar.day4>
			<calendar.day5>Sab</calendar.day5>
			<calendar.day6>Dom</calendar.day6>
			<calendar.initDay>0</calendar.initDay>
			<calendar.month0>Xaneiro</calendar.month0>
			<calendar.month1>Febreiro</calendar.month1>
			<calendar.month2>Marzo</calendar.month2>
			<calendar.month3>Abril</calendar.month3>
			<calendar.month4>Maio</calendar.month4>
			<calendar.month5>Xuño</calendar.month5>
			<calendar.month6>Xulio</calendar.month6>
			<calendar.month7>Agosto</calendar.month7>
			<calendar.month8>Septembro</calendar.month8>
			<calendar.month9>Outubro</calendar.month9>
			<calendar.month10>Novembro</calendar.month10>
			<calendar.month11>Decembro</calendar.month11>
			<format.date>dd/MM/yyyy</format.date>
			<format.datetime>dd/MM/yyyy hh:mm:ss</format.datetime>
			<format.decimal>,</format.decimal>
			<status>... Loading ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>