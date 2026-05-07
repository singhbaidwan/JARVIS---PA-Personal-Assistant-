from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path
import os
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.search.engine import search_files


class SearchEngineTests(unittest.TestCase):
    def test_search_finds_python_file_edited_yesterday(self) -> None:
        reference = datetime(2026, 4, 12, 10, 0, tzinfo=UTC)
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            target = root / "automation_report.py"
            target.write_text("def summarize_workflows():\n    return 'automation insight'\n", encoding="utf-8")
            yesterday = datetime(2026, 4, 11, 15, 30, tzinfo=UTC).timestamp()
            os.utime(target, (yesterday, yesterday))

            decoy = root / "automation_notes.md"
            decoy.write_text("automation insight", encoding="utf-8")
            today = datetime(2026, 4, 12, 9, 0, tzinfo=UTC).timestamp()
            os.utime(decoy, (today, today))

            indexed_count, results = search_files(
                query="Find python file I edited yesterday about automation",
                roots=[str(root)],
                reference_time=reference,
            )

        self.assertEqual(2, indexed_count)
        self.assertGreaterEqual(len(results), 1)
        self.assertEqual("automation_report.py", results[0].name)
        self.assertEqual(".py", results[0].extension)
        self.assertIn("modified time matched", results[0].reason)

    def test_search_returns_content_snippet(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            target = root / "notes.txt"
            target.write_text(
                "Meeting notes. The launch checklist needs a database backup before release.",
                encoding="utf-8",
            )

            _, results = search_files(
                query='"database backup"',
                roots=[str(root)],
            )

        self.assertEqual("notes.txt", results[0].name)
        self.assertEqual("content", results[0].match_type)
        self.assertIn("database backup", results[0].snippet)


if __name__ == "__main__":
    unittest.main()
