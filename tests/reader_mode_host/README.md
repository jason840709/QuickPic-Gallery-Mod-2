# Reader mode host tests

Run with an existing JDK and Python:

```sh
python3 -B tests/reader_mode_host/run.py --output-json /tmp/reader-mode.json
```

The runner compiles the actual `ReaderMode.java` with small controlled Android doubles. It checks page identity and pixels-as-frame ownership, repeated navigation, stale messages, cancellation, failure, blocked-main queue ordering, mixed media and slideshow transactions. Compilation files are disposable. No packages or tools are downloaded.

These tests do not execute the legacy DEX bridges, Android codecs or a real screen. Check the final APK's exact descriptors and run it on Android separately.
