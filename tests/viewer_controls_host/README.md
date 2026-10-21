# Viewer controls host regression

Run with the existing research Python and JDK:

```sh
/opt/anaconda3/bin/python -B tests/viewer_controls_host/run.py
```

Optional `--output-json PATH` records the helper hashes and test result. `--work-dir DIR` chooses the parent of a temporary class directory; temporary classes are removed automatically.

The harness compiles the actual `src/com/alensw/jni/ViewerControls.java` and `MenuLayout.java` files. Android API doubles expose menu item identity, action dispatch, app theme colors, drawable mutation, dialog input, preferences, and slideshow queue state. They do not reproduce the helper implementation. Historical files are never read or changed while running the suite.

This is a host behavior regression, not an APK build or Android rendering test. Packaged translations and XML menu order belong to the patch/resource checks. Device checks remain necessary for touch targets, keyboard behavior, icon appearance, and Android dialog layout.

The dialog checks traverse the actual ScrollView, RadioGroup, buttons and compact editor built by the helper. Saved custom values and unsaved input are exercised separately. The doubles model generated view IDs, RadioGroup selection and descendant focus delegation, including counterexamples for anonymous prechecked children and ScrollView's default child-first focus. They test those boundaries without claiming Android pixel layout or keyboard equivalence.
