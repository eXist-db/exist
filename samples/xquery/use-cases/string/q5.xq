for $item in doc("news.xml")//news_item
where contains(string($item/content), "Gorilla Corporation")
return
    <item_summary>
        { $item/title/text() }.
        { $item/date/text() }.
        { string(($item//par)[1]) }
    </item_summary>
