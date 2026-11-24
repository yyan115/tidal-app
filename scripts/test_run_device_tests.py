import os
import pathlib
import shutil
import stat
import subprocess
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).with_name("run_device_tests.sh")
NOT_FOUND = "Plugin [id: 'info.solidsoft.pitest', version: '1.15.0', apply: false] was not found in any of the following sources:"
REFUSED = "Could not GET 'https://repo.maven.apache.org/maven2/x.pom'. Received status code 429 from server: Too Many Requests"


class DeviceTestRun(unittest.TestCase):
    """The script that runs the device tests starts Gradle once for each shard, and tries again when Maven Central refuses a download."""

    def run_script(self, behaviour: str, shards: int = 1, api: str = "29"):
        """Runs the script with a Gradle that behaves as [behaviour] says for each call, in turn: 'ok', 'refused', 'notfound' or 'failed'."""
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "scripts").mkdir()
            shutil.copy(SCRIPT, root / "scripts" / "run_device_tests.sh")
            (root / "behaviour").write_text(behaviour, encoding="utf-8")
            gradle = root / "gradlew"
            gradle.write_text(f"""#!/usr/bin/env bash
echo "$*" >> calls.log
n=$(wc -l < calls.log)
what=$(tr ' ' '\\n' < behaviour | sed -n "${{n}}p")
[ -z "$what" ] && what=$(tr ' ' '\\n' < behaviour | tail -1)
case "$what" in
  ok) exit 0 ;;
  refused) echo "{REFUSED}"; exit 1 ;;
  notfound) echo "{NOT_FOUND}"; exit 1 ;;
  failed) echo "SomeTest > aTest FAILED"; exit 1 ;;
esac
""", encoding="utf-8")
            gradle.chmod(gradle.stat().st_mode | stat.S_IEXEC)
            done = subprocess.run(["bash", "scripts/run_device_tests.sh", api, str(shards)], cwd=root, capture_output=True, text=True,
                                  env={**os.environ, "RETRY_SECONDS": "0"})
            calls = (root / "calls.log").read_text(encoding="utf-8").splitlines()
            return done.returncode, calls, sorted(p.name for p in (root / "shard-results").glob("*"))

    def test_runs_gradle_once_for_each_shard(self):
        code, calls, results = self.run_script("ok", shards=2)
        self.assertEqual(0, code)
        self.assertEqual(2, len(calls))
        self.assertIn("shardIndex=0", calls[0]); self.assertIn("shardIndex=1", calls[1])
        self.assertIn("numShards=2", calls[0])
        self.assertEqual(["0", "1"], results)

    def test_a_refused_download_is_tried_again(self):
        code, calls, _ = self.run_script("refused refused ok")
        self.assertEqual(0, code)
        self.assertEqual(3, len(calls))

    def test_a_plugin_that_was_not_found_is_tried_again(self):
        code, calls, _ = self.run_script("notfound ok")
        self.assertEqual(0, code)
        self.assertEqual(2, len(calls))

    def test_a_download_that_stays_refused_stops_after_four_attempts(self):
        code, calls, _ = self.run_script("refused")
        self.assertNotEqual(0, code)
        self.assertEqual(4, len(calls))

    def test_a_failed_test_is_not_tried_again(self):
        code, calls, _ = self.run_script("failed")
        self.assertNotEqual(0, code)
        self.assertEqual(1, len(calls))

    def test_one_failed_shard_fails_the_run_and_the_next_shard_still_runs(self):
        code, calls, results = self.run_script("failed ok", shards=2)
        self.assertNotEqual(0, code)
        self.assertEqual(2, len(calls))
        self.assertEqual(["0", "1"], results)

    def test_every_shard_gets_its_own_attempts(self):
        code, calls, _ = self.run_script("refused ok refused ok", shards=2)
        self.assertEqual(0, code)
        self.assertEqual(4, len(calls))

    def test_the_arguments_of_each_android_version_are_kept_on_a_retry(self):
        _, calls, _ = self.run_script("refused ok", api="33")
        self.assertEqual(2, len(calls))
        self.assertEqual(calls[0], calls[1])
        self.assertIn("crawl=true", calls[0]); self.assertIn("matrixOffset=1", calls[0])


if __name__ == "__main__":
    unittest.main()
