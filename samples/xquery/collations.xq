declare variable $words {
	("wußte", "wüßte", "Apfel", "Äpfel", "Buch", "Bücher", "Jagen", "Jäger", "Bauer", "Bäuerin")
};

<words>
{
	for $w in $words
	order by $w collation "?lang=de-DE"
	return
		<word>{$w}</word>
}
</words>
