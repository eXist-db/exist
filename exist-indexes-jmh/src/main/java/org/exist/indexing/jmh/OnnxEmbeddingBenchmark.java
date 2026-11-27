/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.jmh;

import org.exist.vector.OnnxVectorProvider;
import org.openjdk.jmh.annotations.*;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The cost of embedding one text with the local ONNX model (all-MiniLM-L6-v2), as the indexer does for each text of a
 * vector field. The texts are {@value #TEXTS} passages of {@value #WORDS} words of the vendored plays, about the median
 * length of a FRUS division; the model reads the first 128 tokens of each. The score is the time per text.
 *
 * <p>The number of CPUs matters: run it in a container with a CPU limit (for example {@code docker run --cpus 4}) to see
 * the cost on a CI runner or a server with a quota. The model is read from {@code -Dexist.vector.model.path}, else
 * {@code target/onnx-models/all-MiniLM-L6-v2} (built with {@code -Ponnx-model}), else
 * {@code ~/.m2/onnx-models/all-MiniLM-L6-v2}.</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 5)
@Fork(1)
public class OnnxEmbeddingBenchmark {

    static final int TEXTS = 32;
    static final int WORDS = 400;
    private static final String MODEL = "all-MiniLM-L6-v2";
    private static final String[] PLAYS = {"hamlet.xml", "macbeth.xml", "r_and_j.xml"};

    private OnnxVectorProvider provider;
    private List<String> texts;

    /**
     * Loads the model and cuts the passages.
     *
     * @throws Exception if the plays cannot be read
     */
    @Setup(Level.Trial)
    public void setUp() throws Exception {
        final Path model = modelPath();
        provider = OnnxVectorProvider.create(model, 384);
        if (provider == null) {
            throw new IllegalStateException("Could not load the model from " + model);
        }
        texts = passages();
    }

    /**
     * Closes the model.
     */
    @TearDown(Level.Trial)
    public void tearDown() {
        if (provider != null) {
            provider.close();
        }
    }

    /**
     * Embeds all passages, one at a time.
     *
     * @return the vectors, so that the work is not eliminated
     */
    @Benchmark
    @OperationsPerInvocation(TEXTS)
    public List<float[]> embed() {
        final List<float[]> vectors = new ArrayList<>(TEXTS);
        for (final String text : texts) {
            vectors.add(provider.embed(text));
        }
        return vectors;
    }

    private static Path modelPath() {
        final String configured = System.getProperty("exist.vector.model.path");
        final List<Path> candidates = configured != null ? List.of(Path.of(configured))
                : List.of(Path.of("target/onnx-models", MODEL), Path.of(System.getProperty("user.home"), ".m2/onnx-models", MODEL));
        for (final Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("model.onnx")) && Files.isRegularFile(candidate.resolve("tokenizer.json"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("No ONNX model in " + candidates + ": build with -Ponnx-model or set -Dexist.vector.model.path");
    }

    private static List<String> passages() throws Exception {
        final List<String> words = new ArrayList<>();
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        for (final String play : PLAYS) {
            try (InputStream in = OnnxEmbeddingBenchmark.class.getResourceAsStream(play)) {
                final NodeList lines = factory.newDocumentBuilder().parse(in).getElementsByTagName("LINE");
                for (int i = 0; i < lines.getLength(); i++) {
                    words.addAll(Arrays.asList(lines.item(i).getTextContent().trim().split("\\s+")));
                }
            }
        }
        final List<String> passages = new ArrayList<>(TEXTS);
        for (int i = 0; i < TEXTS; i++) {
            passages.add(String.join(" ", words.subList(i * WORDS, (i + 1) * WORDS)));
        }
        return passages;
    }
}
