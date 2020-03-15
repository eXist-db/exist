(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "1.0";

declare namespace exi="http://exist-db.org/xquery/exi";

declare option exist:serialize "media-type=application/xml";

(: round trip demo :)

let $xml :=
<ForecastList>
    <Forecast>
        <SiteId>34567</SiteId>
        <TimeStep>2011-06-13T12:00:00Z</TimeStep>
        <WeatherElement name="WindSpeed" uom="mps">11.2</WeatherElement>
        <WeatherElement name="ScreenTemperature" uom="degC">16.4</WeatherElement>
        <WeatherElement name="Visibility" uom="metres">6000.0</WeatherElement>
    </Forecast>
</ForecastList>,
$exi := exi:encode-from-xml($xml) return
exi:decode-to-xml($exi)
