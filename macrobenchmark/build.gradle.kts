plugins {
    id("mihon.benchmark")
}

android {
    namespace = "tachiyomi.macrobenchmark"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Default keeps the historical behaviour (only BaselineProfileGenerator runs).
        // Override to run macrobenchmarks:
        //   ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest -PbenchRules=Macrobenchmark
        testInstrumentationRunnerArguments["androidx.benchmark.enabledRules"] =
            (properties["benchRules"] as String?) ?: "BaselineProfile"
        // Chapter under test for ReaderScrollBenchmark (it skips itself when absent):
        //   -PbenchMangaId=<id> -PbenchChapterId=<id>
        (properties["benchMangaId"] as String?)?.let {
            testInstrumentationRunnerArguments["benchMangaId"] = it
        }
        (properties["benchChapterId"] as String?)?.let {
            testInstrumentationRunnerArguments["benchChapterId"] = it
        }
    }

    buildTypes {
        // This benchmark buildType is used for benchmarking, and should function like your
        // release build (for example, with minification on). It's signed with a debug key
        // for easy local/CI testing.
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks.add("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(androidx.test.ext)
    implementation(androidx.test.espresso.core)
    implementation(androidx.test.uiautomator)
    implementation(androidx.benchmark.macro)
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}
