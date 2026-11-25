import itertools
import pathlib
import re
import unittest

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
WORKFLOWS = ROOT / ".github" / "workflows"
REQUIRED = ROOT / ".github" / "required-checks.txt"


def lookup(values: dict, path: str):
    """A value of the matrix, such as api or screen.name."""
    value = values
    for part in path.split("."):
        value = value[part]
    return value


def job_names(workflow: pathlib.Path) -> set[str]:
    """The names of the checks that a workflow reports, with the matrix of each job written out."""
    names = set()
    for job in (yaml.safe_load(workflow.read_text(encoding="utf-8")).get("jobs") or {}).values():
        name = str(job.get("name", ""))
        matrix = (job.get("strategy") or {}).get("matrix") or {}
        keys = [key for key, values in matrix.items() if isinstance(values, list) and key not in ("include", "exclude")]
        if not keys:
            names.add(name)
            continue
        for combination in itertools.product(*(matrix[key] for key in keys)):
            values = dict(zip(keys, combination))
            names.add(re.sub(r"\$\{\{\s*matrix\.([\w.]+)\s*\}\}", lambda m: str(lookup(values, m.group(1))), name))
    return names


class RequiredChecks(unittest.TestCase):
    """The ruleset "Protect main" waits for checks by name. A job that is renamed in a workflow would leave the ruleset waiting for a check that never comes,
    and nothing could be merged. The names that the ruleset requires are kept in .github/required-checks.txt, and this test holds them against the workflows."""

    def test_every_required_check_is_reported_by_a_job(self):
        reported = set().union(*(job_names(path) for path in WORKFLOWS.glob("*.yml")))
        required = [line.strip() for line in REQUIRED.read_text(encoding="utf-8").splitlines() if line.strip()]
        self.assertTrue(required)
        missing = [name for name in required if name not in reported]
        self.assertEqual([], missing, f"No job reports these required checks. Change the ruleset together with the workflow: {missing}")

    def test_the_nightly_run_is_not_a_required_check(self):
        required = REQUIRED.read_text(encoding="utf-8")
        self.assertNotIn("Nightly", required)
        self.assertFalse([name for name in job_names(WORKFLOWS / "nightly.yml") if name in required.splitlines()])

    def test_the_nightly_run_covers_every_android_version_and_five_sizes(self):
        nightly = yaml.safe_load((WORKFLOWS / "nightly.yml").read_text(encoding="utf-8"))
        matrix = nightly["jobs"]["device"]["strategy"]["matrix"]
        self.assertEqual(list(range(29, 37)), sorted(matrix["api"]))
        self.assertEqual(5, len(matrix["screen"]))
        self.assertEqual(40, len(job_names(WORKFLOWS / "nightly.yml")))


if __name__ == "__main__":
    unittest.main()
