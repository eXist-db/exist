<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>pt</language>
			<calendar.day0>Seg</calendar.day0>
			<calendar.day1>Ter</calendar.day1>
			<calendar.day2>Qua</calendar.day2>
			<calendar.day3>Qui</calendar.day3>
			<calendar.day4>Sex</calendar.day4>
			<calendar.day5>Sab</calendar.day5>
			<calendar.day6>Dom</calendar.day6>
			<calendar.initDay>0</calendar.initDay>
			<calendar.month0>Janeiro</calendar.month0>
			<calendar.month1>Fevereiro</calendar.month1>
			<calendar.month2>Março</calendar.month2>
			<calendar.month3>Abril</calendar.month3>
			<calendar.month4>Maio</calendar.month4>
			<calendar.month5>Junho</calendar.month5>
			<calendar.month6>Julho</calendar.month6>
			<calendar.month7>Agosto</calendar.month7>
			<calendar.month8>Setembro</calendar.month8>
			<calendar.month9>Outubro</calendar.month9>
			<calendar.month10>Novembro</calendar.month10>
			<calendar.month11>Dezembro</calendar.month11>
			<format.date>dd/MM/yyyy</format.date>
			<format.datetime>dd/MM/yyyy hh:mm:ss</format.datetime>
			<format.decimal>,</format.decimal>
			<status>... A carregar ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>