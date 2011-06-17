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
