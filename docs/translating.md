# Translating OpenTune

OpenTune is ready for translation. Only English ships so far. This page describes where text lives, how to add a language and how to check the result.

## Where text lives

- All text for the user is in `app/src/main/res/values/strings.xml`. Counts use `<plurals>`.
- The widget layout and the manifest refer to the same file.
- Failures from TIDAL, ListenBrainz and the backup file carry a `Problem` value from the `core` module. `app/src/main/kotlin/io/github/yyan115/opentune/ErrorText.kt` maps each value to a string. The compiler rejects a new `Problem` that has no string.
- Names of tracks, albums, artists and playlists come from TIDAL or from the audio files. OpenTune does not translate them. Page text from TIDAL, such as section titles, follows the language of the device because OpenTune sends the device language with each page request.
- The licence texts in **Open-source licenses** stay in English because they are legal documents.

## Add a language

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<code>/strings.xml`. Use the Android folder names, for example `values-de` or `values-pt-rBR`.
2. Translate every value. Keep placeholders such as `%1$s` and `%1$d`. A placeholder may change position, so always write the number in it.
3. For each `<plurals>` entry, write the forms that the language needs. The [CLDR plural rules](https://cldr.unicode.org/index/cldr-spec/plural-rules) list them.
4. Build the app. The language appears in the Android language settings for OpenTune without any further list, because the build reads the `values-*` folders.
5. Run `./gradlew :app:lintDebug`. Lint reports a missing translation as an error, so translate every string in one pull request.

Do not translate names of products and formats, such as TIDAL, ListenBrainz and FLAC.

## Rules for new text

- In Compose, use `stringResource` and `pluralStringResource`. In other code, use `context.getString`.
- Write one string for a whole sentence. Do not join pieces, because word order differs between languages.
- Give a count a `<plurals>` entry, even when English needs only two forms.
- The `core` module holds no text for the user. Add a `Problem` value and a string for it.
- Run `python3 scripts/check_strings.py`. It fails when a literal appears where text reaches the screen, a content description or a notification. A line that must keep a literal can end with `// i18n-ignore: <reason>`. The same check runs in CI.

## Check a translation

Debug builds include two pseudo-languages. `en-XA` wraps every string from a resource in square brackets and accents its letters. `ar-XB` mirrors the layout from right to left. Text that stays plain on screen is written in code or comes from data.

```sh
adb shell cmd locale set-app-locales io.github.yyan115.opentune.debug --locales en-XA
adb shell cmd locale set-app-locales io.github.yyan115.opentune.debug
```

The first command selects the pseudo-language, the second returns to the device language. This needs Android 13 or later. On older versions, select the language in the system settings.

`PseudoLocaleTest` opens the main screens, sheets and dialogs in `en-XA` and fails when readable English remains:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.yyan115.opentune.PseudoLocaleTest
```

## Limits

- Android 13 and later offer a language setting for each app. Earlier versions use the language of the device.
- OpenTune has no language picker of its own.
- Rows saved before a language change, such as the title that stands in for a missing title, keep the language they were saved in.
