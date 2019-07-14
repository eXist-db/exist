package org.exist.indexing.lucene.suggest;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.spell.Dictionary;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.analyzing.FuzzySuggester;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class FuzzySuggesterWrapper extends Suggester {

    private final FuzzySuggester suggester;
    private final Path storage;

    public FuzzySuggesterWrapper(String id, String field, Element config, Path indexDir, Analyzer analyzer) throws DatabaseConfigurationException {
        super(id, field, config, indexDir, analyzer);

        suggester = new FuzzySuggester(analyzer);

        storage = indexDir.resolve("suggest_" + id);
        try {
            if (Files.exists(storage)) {
                if (Files.isDirectory(storage)) {
                    FileUtils.delete(storage);
                } else {
                    suggester.load(Files.newInputStream(storage, StandardOpenOption.READ));
                }
            }
        } catch (IOException e) {
            throw new DatabaseConfigurationException("Error initializing fuzzy suggester: " + e.getMessage(), e);
        }
    }

    @Override
    List<Lookup.LookupResult> lookup(CharSequence key, boolean onlyMorePopular, int num) throws IOException {
        return suggester.lookup(key, onlyMorePopular, num);
    }

    @Override
    void build(Dictionary dictionary) throws IOException {
        suggester.build(dictionary);
        suggester.store(Files.newOutputStream(storage, StandardOpenOption.WRITE, StandardOpenOption.CREATE));
    }

    @Override
    void close() {
    }
}
