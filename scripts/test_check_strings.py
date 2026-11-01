import importlib.util
import pathlib
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("check_strings", pathlib.Path(__file__).with_name("check_strings.py"))
check = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(check)


class KotlinScan(unittest.TestCase):
    def found(self, source: str):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory, "Screen.kt")
            path.write_text(source, encoding="utf-8")
            return [(kind, text) for _, kind, text in check.scan_kotlin(path)]

    def test_reports_text_written_in_code(self):
        self.assertEqual([("text argument", '"Play"')], self.found('Text("Play", style = x)'))
        self.assertEqual([("text argument", '"Delete"')], self.found('TextButton(onClick = {}) { Text(text = "Delete") }'))
        self.assertEqual([("content description", '"Close sheet"')], self.found('Modifier.semantics { contentDescription = "Close sheet" }'))
        self.assertEqual([("icon description", '"Back"')], self.found('Icon(OpenTuneIcons.ArrowBack, "Back")'))
        self.assertEqual([("setting title", '"Theme"')], self.found('SettingRow(OpenTuneIcons.Contrast, "Theme", value)'))
        self.assertEqual([("text argument", '"Saved"')], self.found('vm.notify("Saved")'))
        self.assertEqual([("text argument", '"Name"')], self.found('OutlinedTextField(v, c, label = { Text("Name") })'))
        self.assertEqual([("text parameter", '"Folder"')], self.found('Heading(title = "Folder")'))

    def test_accepts_resources_and_data(self):
        self.assertEqual([], self.found('Text(stringResource(R.string.play), style = x)'))
        self.assertEqual([], self.found('Text(track.title)'))
        self.assertEqual([], self.found('Icon(OpenTuneIcons.Close, null)'))
        self.assertEqual([], self.found('Icon(OpenTuneIcons.Close, stringResource(R.string.close))'))
        self.assertEqual([], self.found('Text("${track.number}")'))
        self.assertEqual([], self.found('Text("·")'))
        self.assertEqual([], self.found('Text("%02d".format(index + 1))'))

    def test_honours_the_ignore_comment_and_comments(self):
        self.assertEqual([], self.found('Text("Play") // i18n-ignore: a symbol'))
        self.assertEqual([], self.found('// Text("Play")'))
        self.assertEqual([], self.found(' * Text("Play")'))

    def test_labels_of_animations_are_not_text(self):
        self.assertEqual([], self.found('val color by animateColorAsState(c, label = "line-color")'))


class XmlScan(unittest.TestCase):
    def found(self, source: str):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory, "layout.xml")
            path.write_text(source, encoding="utf-8")
            return [text for _, _, text in check.scan_xml(path)]

    def test_reports_attributes_with_text(self):
        self.assertEqual(["Play or pause"], self.found('<ImageButton android:contentDescription="Play or pause" />'))
        self.assertEqual(["Nothing playing"], self.found('<TextView android:text="Nothing playing" />'))

    def test_accepts_references(self):
        self.assertEqual([], self.found('<TextView android:text="@string/nothing_playing" />'))
        self.assertEqual([], self.found('<ImageView android:contentDescription="@null" />'))


class Repository(unittest.TestCase):
    def test_no_text_is_written_in_code(self):
        self.assertEqual(0, check.main())


if __name__ == "__main__":
    unittest.main()
