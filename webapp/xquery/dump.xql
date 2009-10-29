xquery version "1.0";

declare namespace dump="http://exist-db.org/xquery/dump";

declare option exist:serialize "media-type=text/xml omit-xml-declaration=no";

declare function dump:attributes() {
    for $attr in request:attribute-names()
    return
        <attribute name="{$attr}">{request:get-attribute($attr)}</attribute>
};

declare function dump:headers() {
	for $header in request:get-header-names()
	return
		<header name="{$header}">{request:get-header($header)}</header>
};

declare function dump:dump($data) {
	<request uri="{request:get-uri()}">
		<headers>
		{ dump:headers() }
		</headers>
		{ dump:attributes() }
		<data>{$data}</data>
	</request>
};

let $data := request:get-data()
return
	dump:dump($data)
