import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import run_ai_task


class ModelSelectionTests(unittest.TestCase):
    def test_missing_model_uses_low_cost_default(self):
        self.assertEqual(run_ai_task.resolve_model(""), "gpt-5.6-luna")

    def test_retired_gpt_35_alias_uses_supported_replacement(self):
        self.assertEqual(run_ai_task.resolve_model("gpt-3.5-turbo"), "gpt-5.6-luna")

    def test_retired_gpt_35_snapshot_uses_supported_replacement(self):
        self.assertEqual(run_ai_task.resolve_model(" gpt-3.5-turbo-0125 "), "gpt-5.6-luna")

    def test_non_retired_configuration_is_preserved(self):
        self.assertEqual(run_ai_task.resolve_model("gpt-5.6-terra"), "gpt-5.6-terra")


if __name__ == "__main__":
    unittest.main()
