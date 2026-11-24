import os
import pathlib
import shutil
import stat
import subprocess
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).with_name("prepare_emulator.sh")


class PrepareEmulator(unittest.TestCase):
    """The script restarts the system of an emulator that sets no memory limit for apps, and leaves the other emulators alone."""

    def run_script(self, limit: str, boots: str = "yes"):
        """Runs the script with a fake adb. [limit] is the memory limit that it reports, and [boots] says whether the system starts again."""
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "scripts").mkdir()
            shutil.copy(SCRIPT, root / "scripts" / "prepare_emulator.sh")
            adb = root / "adb"
            adb.write_text(f"""#!/usr/bin/env bash
echo "$*" >> calls.log
case "$*" in
  "shell getprop dalvik.vm.heapgrowthlimit") echo "{limit}" ;;
  "shell getprop sys.boot_completed") [ "{boots}" = yes ] && echo 1 || echo 0 ;;
  "shell pm path android") [ "{boots}" = yes ] || exit 1 ;;
esac
exit 0
""", encoding="utf-8")
            adb.chmod(adb.stat().st_mode | stat.S_IEXEC)
            done = subprocess.run(["bash", "scripts/prepare_emulator.sh"], cwd=root, capture_output=True, text=True,
                                  env={**os.environ, "PATH": f"{root}:{os.environ['PATH']}", "WAIT_SECONDS": "0", "WAIT_ATTEMPTS": "3"})
            calls = (root / "calls.log").read_text(encoding="utf-8").splitlines()
            return done.returncode, calls

    def test_an_emulator_with_a_limit_is_not_restarted(self):
        code, calls = self.run_script("192m")
        self.assertEqual(0, code)
        self.assertEqual(["shell getprop dalvik.vm.heapgrowthlimit"], calls)

    def test_an_emulator_without_a_limit_is_restarted_in_order(self):
        code, calls = self.run_script("")
        self.assertEqual(0, code)
        order = [calls.index(call) for call in ("root", "shell stop", "shell start")]
        self.assertEqual(sorted(order), order)
        self.assertLess(calls.index("shell start"), calls.index("shell pm path android"))

    def test_the_boot_flag_is_cleared_before_the_stop_so_that_the_wait_means_something(self):
        _, calls = self.run_script("")
        self.assertLess(calls.index("shell setprop sys.boot_completed 0"), calls.index("shell stop"))

    def test_the_animations_are_switched_off_again_after_the_restart(self):
        _, calls = self.run_script("")
        for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            self.assertIn(f"shell settings put global {setting} 0", calls)
            self.assertGreater(calls.index(f"shell settings put global {setting} 0"), calls.index("shell start"))

    def test_a_system_that_does_not_start_again_fails_the_run(self):
        code, calls = self.run_script("", boots="no")
        self.assertNotEqual(0, code)
        self.assertNotIn("shell settings put global window_animation_scale 0", calls)

    def test_the_wait_is_limited(self):
        _, calls = self.run_script("", boots="no")
        self.assertEqual(3, calls.count("shell getprop sys.boot_completed"))


if __name__ == "__main__":
    unittest.main()
