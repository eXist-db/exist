<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>ru</language>
			<calendar.day0>Пон</calendar.day0>
			<calendar.day1>Вто</calendar.day1>
			<calendar.day2>Сре</calendar.day2>
			<calendar.day3>Чет</calendar.day3>
			<calendar.day4>Пят</calendar.day4>
			<calendar.day5>Суб</calendar.day5>
			<calendar.day6>Вос</calendar.day6>
			<calendar.initDay>0</calendar.initDay>
			<calendar.month0>Январь</calendar.month0>
			<calendar.month1>Февраль</calendar.month1>
			<calendar.month2>Март</calendar.month2>
			<calendar.month3>Апрель</calendar.month3>
			<calendar.month4>Май</calendar.month4>
			<calendar.month5>Июнь</calendar.month5>
			<calendar.month6>Июль</calendar.month6>
			<calendar.month7>Август</calendar.month7>
			<calendar.month8>Сентябрь</calendar.month8>
			<calendar.month9>Октябрь</calendar.month9>
			<calendar.month10>Ноябрь</calendar.month10>
			<calendar.month11>Декабрь</calendar.month11>
			<format.date>dd.MM.yyyy</format.date>
			<format.datetime>dd.MM.yyyy hh:mm:ss</format.datetime>
			<format.decimal>,</format.decimal>
			<status>... Загрузка ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>