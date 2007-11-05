xquery version "1.0";
(: $Id: get-examples.xql 6434 2007-08-28 18:59:23Z ellefj $ :)

declare option exist:serialize "media-type=text/xml omit-xml-declaration=yes";

<select id="saved" name="saved">
    <option value=""></option>
    {
    for $entry in //example-queries/query
    return
        <option value="{$entry/code}">{$entry/description}</option>
    }
</select>