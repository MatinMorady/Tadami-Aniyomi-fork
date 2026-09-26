# Baseline profiles

The baseline profile for this app is located at [`app/src/main/baseline-prof.txt`](../app/src/main/baseline-prof.txt).
It contains rules that enable AOT compilation of the critical user path taken during app launch.
For more information on baseline profiles, read [this document](https://developer.android.com/studio/profile/baselineprofiles).

> Note: The baseline profile needs to be re-generated for release builds that touch code which changes app startup.

To generate the baseline profile, select the `devBenchmark` build variant and run the
`BaselineProfileGenerator` benchmark test on an AOSP Android Emulator.
Then copy the resulting baseline profile from the emulator to [`app/src/main/baseline-prof.txt`](../app/src/main/baseline-prof.txt).

## Macrobenchmarks (startup / scroll frame timing)

Requires a device with the `benchmark` build of the app installed (Gradle does this
automatically for `connectedBenchmarkAndroidTest`).

```bash
# Cold/warm/hot startup + home-screen fling frame timing:
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest -PbenchRules=Macrobenchmark

# Reader fling frame timing (webtoon scroll smoothness / pager swipes).
# mangaId/chapterId are DB ids of a chapter in the benchmark-profile app data
# (use a WEBTOON-mode chapter for scroll metrics):
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest -PbenchRules=Macrobenchmark \
    -PbenchMangaId=<id> -PbenchChapterId=<id>
```

Results (frameDurationCpuMs / frameOverrunMs / timeToInitialDisplayMs percentiles) land in
`macrobenchmark/build/outputs/androidTest-results/connected/`. Run the identical command
before and after a change for an A/B comparison. Without `-PbenchRules=Macrobenchmark`
only the baseline-profile generator runs (historical default).

To find ids for the reader benchmark: open the chapter in the `.benchmark` app once and
check logcat `ReaderViewModel` output, or query the exported DB ids from a debug build
(`SELECT id FROM chapters WHERE ...`); ids are shared across profiles of the same install
only if the app data matches - the benchmark target has its own data dir
(`com.tadami.aurora.benchmark`).