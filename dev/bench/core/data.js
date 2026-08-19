window.BENCHMARK_DATA = {
  "lastUpdate": 1787131584445,
  "repoUrl": "https://github.com/eXist-db/exist",
  "entries": {
    "exist-core-jmh": [
      {
        "commit": {
          "author": {
            "name": "Duncan Paterson",
            "username": "duncdrum",
            "email": "duncdrum@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a8afa8f2485c59748d851d798281cfa659a4e566",
          "message": "Merge pull request #6475 from joewiz/feat/external-var-default-override\n\n[bugfix] External variable: supplied value overrides declared default",
          "timestamp": "2026-08-19T08:41:10Z",
          "url": "https://github.com/eXist-db/exist/commit/a8afa8f2485c59748d851d798281cfa659a4e566"
        },
        "date": 1787131582907,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.storage.lock.LockTableBenchmark.testEvent",
            "value": 1065168734.3073314,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"1\"} )",
            "value": 83618536.08572893,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"2\"} )",
            "value": 19810646.900710892,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"5\"} )",
            "value": 7863549.028536839,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"10\"} )",
            "value": 4500979.166812321,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"100\"} )",
            "value": 500125.70624494273,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"1000\"} )",
            "value": 43945.56881541275,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"10000\"} )",
            "value": 2934.585584162208,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"1\"} )",
            "value": 28775703.55119171,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"2\"} )",
            "value": 19071769.04533099,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"5\"} )",
            "value": 8268068.165748532,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"10\"} )",
            "value": 4696260.961992966,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"100\"} )",
            "value": 413024.64328224125,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"1000\"} )",
            "value": 44395.12058567816,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"10000\"} )",
            "value": 2929.835116865778,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"1\"} )",
            "value": 48875197.91127363,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"2\"} )",
            "value": 21522594.620970678,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"5\"} )",
            "value": 8564815.18871245,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"10\"} )",
            "value": 5148284.42977682,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"100\"} )",
            "value": 530412.7461067064,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"1000\"} )",
            "value": 48214.56524258972,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"10000\"} )",
            "value": 3349.668792727828,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"1\"} )",
            "value": 20646603.265451927,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"2\"} )",
            "value": 15839847.699891498,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"5\"} )",
            "value": 8364501.873784078,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"10\"} )",
            "value": 4424962.293019598,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"100\"} )",
            "value": 414873.7246296477,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"1000\"} )",
            "value": 44700.914452243655,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"10000\"} )",
            "value": 2917.7410050194894,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"1\"} )",
            "value": 49010839.44697729,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"2\"} )",
            "value": 27087417.39913693,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"5\"} )",
            "value": 13914027.505945971,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"10\"} )",
            "value": 6999016.026482165,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"100\"} )",
            "value": 819285.9879255244,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"1000\"} )",
            "value": 74659.9100704921,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"10000\"} )",
            "value": 3477.5650207450935,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.URIUtilsBenchmark.encodeForURI",
            "value": 6488550.050407021,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.following ( {\"shape\":\"1500_20\"} )",
            "value": 10.000982804113217,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.following ( {\"shape\":\"500_100\"} )",
            "value": 17.191556145592006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.following ( {\"shape\":\"100_500\"} )",
            "value": 16.41108437076854,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.followingSibling ( {\"shape\":\"1500_20\"} )",
            "value": 175.57094552609195,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.followingSibling ( {\"shape\":\"500_100\"} )",
            "value": 1820.5664399333334,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.followingSibling ( {\"shape\":\"100_500\"} )",
            "value": 17501.6022706,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.preceding ( {\"shape\":\"1500_20\"} )",
            "value": 10.312040341209313,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.preceding ( {\"shape\":\"500_100\"} )",
            "value": 17.334765964111796,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.preceding ( {\"shape\":\"100_500\"} )",
            "value": 16.80047763988512,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.precedingSibling ( {\"shape\":\"1500_20\"} )",
            "value": 182.11693188439153,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.precedingSibling ( {\"shape\":\"500_100\"} )",
            "value": 1969.8124014666664,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.precedingSibling ( {\"shape\":\"100_500\"} )",
            "value": 17631.423721799998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"1\",\"queryVariant\":\"single\",\"verificationMode\":\"STRICT\"} )",
            "value": 38.631356049371696,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"1\",\"queryVariant\":\"unionExplicit\",\"verificationMode\":\"STRICT\"} )",
            "value": 38.64157141471675,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"1\",\"queryVariant\":\"unionCollectionParens\",\"verificationMode\":\"STRICT\"} )",
            "value": 39.2567791171875,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"10\",\"queryVariant\":\"single\",\"verificationMode\":\"STRICT\"} )",
            "value": 12.703092441448309,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"10\",\"queryVariant\":\"unionExplicit\",\"verificationMode\":\"STRICT\"} )",
            "value": 12.719557542275801,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"10\",\"queryVariant\":\"unionCollectionParens\",\"verificationMode\":\"STRICT\"} )",
            "value": 13.318743866794327,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"1\",\"queryVariant\":\"single\",\"verificationMode\":\"STRICT\"} )",
            "value": 696.99190615,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"1\",\"queryVariant\":\"unionExplicit\",\"verificationMode\":\"STRICT\"} )",
            "value": 697.061407675,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"1\",\"queryVariant\":\"unionCollectionParens\",\"verificationMode\":\"STRICT\"} )",
            "value": 726.8671656285715,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"10\",\"queryVariant\":\"single\",\"verificationMode\":\"STRICT\"} )",
            "value": 122.12787622055748,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"10\",\"queryVariant\":\"unionExplicit\",\"verificationMode\":\"STRICT\"} )",
            "value": 121.96750625969803,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"10\",\"queryVariant\":\"unionCollectionParens\",\"verificationMode\":\"STRICT\"} )",
            "value": 123.27119893658536,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 36.144228970814176,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 26.64903422907825,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 166.35506924285716,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 118.11506344499999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 35.83683482671389,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 24.205536226385064,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 173.2146951645238,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 115.14523528277778,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 33.2321824037096,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 23.117154752158367,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 146.44742307619046,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 122.5984923681746,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 36.550003389971224,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 23.843282962283013,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 168.12678876222225,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 116.255248755,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 35.94275891931087,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 31.837603630953204,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 154.32858703888888,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 121.12757945936508,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 35.38326367530406,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 25.893686797475304,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 154.07153100952382,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 117.34659745611111,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 28.135715206697192,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 24.625515503449094,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 173.2699348634921,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 119.84896424000002,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 39.827590631652484,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 26.028433721144786,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 150.43171331944444,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 129.28333699428575,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 10.984610185055931,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 5.6903549141083705,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 28.52338122031918,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 16.563110524811076,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 9.358379114996396,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 5.082131092974903,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 37.04892800196565,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 14.9922514421134,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 10.842037981526165,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 4.756350810762133,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 33.80408869333409,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 12.476294129101165,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 8.995423074788144,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 4.452219780546635,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 33.50081591800732,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 14.181061617403932,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 9.992570638036845,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 4.283069217423643,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 30.59590277364166,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 14.084835500719937,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 77.53508335715284,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 70.66149513847327,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 401.6652814333333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 315.2304552833333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 64.80235458844537,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 67.86033092812525,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 407.87496419999997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 314.60095366666667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 75.10779153186812,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 61.10465189421827,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 396.91675606666666,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 314.22052958333336,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 47.88813491127992,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 29.14452508989541,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 190.27523963571429,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 172.21827110476193,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 50.48731739268811,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 30.597865654363638,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 213.70295826952378,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 172.54912521904762,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 50.3692876457517,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 30.104656710925923,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 183.0698467285714,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 172.1426845847619,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 52.85115841741013,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 34.72212325117493,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 210.57818465238097,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 172.98418421238097,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 49.650565056961234,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 33.31880473071321,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 157.87176884166666,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 171.85709517428572,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.UtilExpandHighlightingBenchmark.expandBatchWildcardHighlightingOff",
            "value": 59.642734967703085,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.UtilExpandHighlightingBenchmark.expandBatchWildcardHighlightingOn",
            "value": 328.24660646,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.UtilExpandHighlightingBenchmark.expandSingleHitHighlightingOff",
            "value": 0.05303657904541752,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.UtilExpandHighlightingBenchmark.expandSingleHitHighlightingOn",
            "value": 0.15359321658371217,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.storage.ReindexBenchmark.reindex ( {\"docCount\":\"100\",\"verificationMode\":\"STRICT\"} )",
            "value": 12.581376726915765,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.storage.ReindexBenchmark.reindex ( {\"docCount\":\"500\",\"verificationMode\":\"STRICT\"} )",
            "value": 52.831751162894385,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.storage.ReindexBenchmark.reindex ( {\"docCount\":\"1000\",\"verificationMode\":\"STRICT\"} )",
            "value": 102.59879335989858,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.ArrowOperatorBenchmark.arrowChain ( {\"iterations\":\"100000\"} )",
            "value": 60.4797626,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.ArrowOperatorBenchmark.arrowSingleCall ( {\"iterations\":\"100000\"} )",
            "value": 38.467429203112154,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.ArrowOperatorBenchmark.directChain ( {\"iterations\":\"100000\"} )",
            "value": 61.51276316750979,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.ArrowOperatorBenchmark.directSingleCall ( {\"iterations\":\"100000\"} )",
            "value": 38.02891088787879,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.precedingSiblingBaseline ( {\"refPosition\":\"5000\"} )",
            "value": 133.03106760666665,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.precedingSiblingBaseline ( {\"refPosition\":\"25000\"} )",
            "value": 151.9684046,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.precedingSiblingBaseline ( {\"refPosition\":\"45000\"} )",
            "value": 177.61913140000001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.wildcardPrecedingWithPositionalPredicate ( {\"refPosition\":\"5000\"} )",
            "value": 148.34077318571428,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.wildcardPrecedingWithPositionalPredicate ( {\"refPosition\":\"25000\"} )",
            "value": 236.05122799999998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.wildcardPrecedingWithPositionalPredicate ( {\"refPosition\":\"45000\"} )",
            "value": 332.5772498333333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"identical\"} )",
            "value": 0.6250184028839458,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"directSuper\"} )",
            "value": 8.1843774151287,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"deepSuper\"} )",
            "value": 19.573011798420534,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"unionMember\"} )",
            "value": 3.1497745674142656,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"unionSubtype\"} )",
            "value": 26.52579172661252,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"notSubType\"} )",
            "value": 11.255873329338387,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}