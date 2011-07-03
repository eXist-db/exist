xquery version "3.0";

declare option exist:serialize "method=json media-type=text/javascript";

let $xml := request:get-parameter("xml", ())
return
	try {
		util:parse($xml)
	} catch exerr:EXUTLPARSE001 ($code, $desc, $val) {
		$val
	}
