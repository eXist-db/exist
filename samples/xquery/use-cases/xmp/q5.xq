<books-with-prices>
  {
    for $b in doc("bib.xml")//book,
        $a in doc("reviews.xml")//entry
    where $b/title = $a/title
    return
        <book-with-prices>
            { $b/title }
            <price-amazon>{ $a/price/text() }</price-amazon>
            <price-bn>{ $b/price/text() }</price-bn>
        </book-with-prices>
  }
</books-with-prices>