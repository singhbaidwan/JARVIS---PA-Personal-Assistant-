from __future__ import annotations

from datetime import UTC, datetime, timedelta
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.api.schemas import EventInput
from app.intelligence.engine import detect_anomalies, generate_recommendations, predict_next_action


class IntelligenceEngineTests(unittest.TestCase):
    def test_predict_uses_contextual_routine(self) -> None:
        base = datetime(2026, 4, 6, 10, 0, tzinfo=UTC)  # Monday
        events = [
            EventInput(type="APP_SWITCH", timestamp=base - timedelta(days=7), payload={"to": "VS Code"}),
            EventInput(type="APP_SWITCH", timestamp=base - timedelta(days=6), payload={"to": "VS Code"}),
            EventInput(type="APP_SWITCH", timestamp=base - timedelta(days=5), payload={"to": "VS Code"}),
            EventInput(type="APP_SWITCH", timestamp=base - timedelta(days=4), payload={"to": "Slack"}),
        ]

        result = predict_next_action(events, reference_time=base)
        self.assertEqual(result.predicted_action, "OPEN_APP:VS Code")
        self.assertGreaterEqual(result.confidence, 0.7)
        self.assertEqual(result.context.get("routine_scope"), "contextual")

    def test_anomaly_detects_resource_spike(self) -> None:
        now = datetime(2026, 4, 11, 12, 0, tzinfo=UTC)
        events = [
            EventInput(type="RESOURCE_SAMPLE", timestamp=now - timedelta(minutes=3), payload={"cpu_percent": 93}),
            EventInput(type="RESOURCE_SAMPLE", timestamp=now - timedelta(minutes=2), payload={"memory_percent": 92}),
        ]

        result = detect_anomalies(events, reference_time=now)
        self.assertTrue(result.anomaly_detected)
        self.assertIn(result.severity, {"MEDIUM", "HIGH", "CRITICAL"})
        self.assertGreaterEqual(result.score, 40.0)

    def test_anomaly_names_process_for_cpu_spike(self) -> None:
        now = datetime(2026, 4, 11, 12, 0, tzinfo=UTC)
        events = [
            EventInput(
                type="RESOURCE_SAMPLE",
                timestamp=now - timedelta(minutes=1),
                payload={"process": "Chrome", "cpu_percent": 91.2, "memory_percent": 21.0},
            ),
        ]

        result = detect_anomalies(events, reference_time=now)
        self.assertTrue(result.anomaly_detected)
        self.assertEqual("Chrome using 91% CPU", result.reason)
        self.assertIn("Chrome using 91% CPU", result.signals)

    def test_anomaly_detects_unusual_app_usage(self) -> None:
        now = datetime(2026, 4, 11, 23, 30, tzinfo=UTC)
        events = [
            EventInput(
                type="APP_SWITCHED",
                timestamp=now - timedelta(days=day_offset, hours=10),
                payload={"to": "VS Code" if day_offset % 2 else "Slack"},
            )
            for day_offset in range(1, 21)
        ]
        events.append(
            EventInput(
                type="APP_SWITCHED",
                timestamp=now - timedelta(minutes=2),
                payload={"to": "Steam"},
            )
        )

        result = detect_anomalies(events, reference_time=now)
        self.assertTrue(result.anomaly_detected)
        self.assertIn("Behavioral outlier: Steam", result.signals[0])

    def test_recommendations_include_focus_and_break(self) -> None:
        now = datetime(2026, 4, 11, 15, 0, tzinfo=UTC)
        start = now - timedelta(hours=2, minutes=30)
        events = []
        timeline = [
            ("YouTube", 0),
            ("X", 10),
            ("VS Code", 20),
            ("Instagram", 30),
            ("VS Code", 45),
            ("Reddit", 60),
            ("VS Code", 75),
            ("Slack", 90),
            ("VS Code", 100),
            ("YouTube", 115),
            ("VS Code", 130),
        ]
        for app, minute_offset in timeline:
            events.append(
                EventInput(
                    type="APP_SWITCH",
                    timestamp=start + timedelta(minutes=minute_offset),
                    payload={"to": app},
                )
            )

        result = generate_recommendations(events, reference_time=now)
        categories = {item.category for item in result.recommendations}
        self.assertIn("focus", categories)
        self.assertIn("break", categories)
        self.assertLess(result.focus_score, 70.0)


if __name__ == "__main__":
    unittest.main()
