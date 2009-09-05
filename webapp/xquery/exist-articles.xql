xquery version "1.0";

declare namespace mods="http://www.loc.gov/mods/v3";
declare namespace mads="http://www.loc.gov/mads/";
declare namespace xlink="http://www.w3.org/1999/xlink";
declare namespace fo="http://www.w3.org/1999/XSL/Format";

declare option exist:serialize "media-type=text/xml";

declare function mods:add-part($part, $sep as xs:string) {
    if (empty($part) or string-length($part[1]) eq 0) then
        ()
    else
        concat(string-join($part, " "), $sep)
};

declare function mods:get-extent($extent as element(mods:extent)) {
    if ($extent/mods:end) then
        concat($extent/mods:start, " - ", $extent/mods:end)
    else
        $extent/mods:start
};

declare function mods:get-part-and-origin($entry as element()) {
    let $part := $entry/mods:part
    let $origin := $entry/mods:originInfo
    return (
        mods:add-part($origin/mods:place/mods:placeTerm, ": "),
        mods:add-part($origin/mods:publisher, ", "),
        ($origin/mods:dateIssued/string(), $part/mods:date/string(),
        $origin/mods:dateCreated/string())[1]
    )
};

declare function mods:get-conference($entry as element(mods:mods)) {
    let $date := ($entry/mods:originInfo/mods:dateIssued/string(), $entry/mods:part/mods:date/string(),
            $entry/mods:originInfo/mods:dateCreated/string())[1]
    return
        concat("Paper presented at ", 
            mods:add-part($entry/mods:name[@type = 'conference']/mods:namePart, ", "),
            mods:add-part($entry/mods:originInfo/mods:place/mods:placeTerm, ", "),
            $date
        )
};

declare function mods:get-name($name as element(mods:name), $pos as xs:integer) {
	if ($name/@type = 'corporate') then
        $name/mods:namePart[@lang]
	else
		let $family := $name/mods:namePart[@type = 'family']
		let $given := $name/mods:namePart[@type = 'given']
		return
			if ($family and $given) then
				if ($pos eq 1) then
				    concat($family/text(), ', ', $given/text())
				else
					concat($given/text(), ' ', $family/text())
		  else
		      $name/text()
};

declare function mods:get-names($entry as element()) {
    let $names :=
        for $name at $pos in $entry/mods:name[@type = ('personal', 'corporate')]
        return
            mods:get-name($name, $pos)
    let $nameCount := count($names)
    let $formatted :=
        if ($nameCount eq 0) then
            ()
        else if ($nameCount eq 1) then
            concat($names[1], '. ')
        else
            concat(
                string-join(subsequence($names, 1, $nameCount - 1), ", "),
                ", and ",
                $names[$nameCount],
                ". "
            )
    return
        $formatted
};

declare function mods:get-title($entry as element()) {
    let $title := $entry/mods:titleInfo
    return
        if ($entry//mods:url) then
            <a href="{$entry//mods:url/string()}">
            { $title/mods:title/string() }.
            </a>
        else
            <emphasis>
            { $title/mods:title/string() }.
            </emphasis>
};

declare function mods:get-related($entry as element(mods:mods)) {
    let $related := $entry/mods:relatedItem[@type = 'host']
    return
        if ($related) then
            concat(
                "In:",
                mods:get-names($related), mods:get-title($related),
                if ($related/mods:originInfo or $related/mods:part) then
                    mods:get-part-and-origin($related)
                else if ($related/mods:location/mods:url) then
                    concat(", ", $related/mods:location/mods:url)
                else 
                    ()
            )
        else
            ()
};

declare function mods:process($entry as element(mods:mods)) {
    <para>
    { 
        mods:get-names($entry), 
        mods:get-title($entry),
        if ($entry/mods:name[@type = 'conference']) then
            mods:get-conference($entry)
        else (
            mods:get-part-and-origin($entry),
            mods:get-related($entry)
        ),
        if ($entry/mods:location/mods:url[@displayLabel]) then
            <span> (<a href="{$entry/mods:location/mods:url}">{$entry/mods:location/mods:url/@displayLabel/string()}</a>)</span>
        else ()
      }
    </para>
};

declare function mods:list-articles() {
    for $entry in /mods:modsCollection/mods:mods
    let $date := ($entry/mods:originInfo/mods:dateIssued/string(), $entry/mods:part/mods:date/string(),
            $entry/mods:originInfo/mods:dateCreated/string())[1]
    order by $date descending
    return
        mods:process($entry)
};

<book>
    <bookinfo>
        <graphic fileref="logo.jpg"/>

        <productname>Open Source Native XML Database</productname>
        <title>Articles on eXist</title>
        <link rel="shortcut icon" href="../resources/exist_icon_16x16.ico"/>
		<link rel="icon" href="../resources/exist_icon_16x16.png" type="image/png"/>
    </bookinfo>
    
    <xi:include xmlns:xi="http://www.w3.org/2001/XInclude" href="sidebar.xml"/>

    <chapter>
        <title>Articles on eXist</title>
        
        {
            if (empty(/mods:mods)) then
                <para>Article references are contained in a MODS document which will be installed
                    with the other sample documents. Please go to the 
                    <ulink url="admin/admin.xql">admin interface</ulink> and launch
                    "Examples Setup".</para>
            else
                <para>A collection of articles, blog posts, tutorials and papers which mention eXist. They
                may help to get started or provide information on special features. The references can
                also be queried/sorted via the "<ulink url="cocoon/biblio.xq">Bibliographic</ulink>" 
                demo.</para>
        }
        
        { mods:list-articles() }
    </chapter>
</book>