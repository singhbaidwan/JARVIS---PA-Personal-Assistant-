from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.api.routes import assistant as assistant_route
from app.api.schemas import AssistantChatRequest, AssistantContext, EventInput, LlmResponse
from app.assistant.engine import build_assistant_prompt, extract_action_suggestions


class AssistantEngineTests(unittest.TestCase):
    def test_prompt_includes_context(self) -> None:
        context = AssistantContext(
            recent_events=[
                EventInput(
                    type="APP_SWITCHED",
                    timestamp=datetime(2026, 4, 12, 9, 0, tzinfo=UTC),
                    payload={"to": "VS Code"},
                )
            ],
            facts={"mode": "focus"},
        )

        system_prompt, prompt = build_assistant_prompt("What next?", context)

        self.assertIn("Core will decide", system_prompt)
        self.assertIn("VS Code", prompt)
        self.assertIn("mode=focus", prompt)

    def test_extract_action_suggestions_from_json_block(self) -> None:
        response = """
        Open VS Code when you are ready.
        ```json
        {"suggested_actions":[{"action":"OPEN_APP","app":"VS Code","confidence":0.82,"reason":"User asked for coding setup"}]}
        ```
        """

        suggestions = extract_action_suggestions(response)

        self.assertEqual(1, len(suggestions))
        self.assertEqual("OPEN_APP", suggestions[0].action)
        self.assertEqual("VS Code", suggestions[0].app)
        self.assertEqual(0.82, suggestions[0].confidence)

    def test_assistant_route_uses_llm_and_returns_suggestions(self) -> None:
        expected = LlmResponse(
            response=(
                "I can help search for that.\n"
                '{"suggested_actions":[{"action":"SEARCH_FILES","query":"python yesterday",'
                '"confidence":0.76,"reason":"The user wants a file lookup"}]}'
            ),
            model="llama3.2:latest",
            provider="local",
            metadata={"eval_count": 10},
        )

        with patch("app.api.routes.assistant.resolve_provider", return_value="local"):
            with patch("app.api.routes.assistant.generator_for_provider") as mocked_factory:
                mocked_factory.return_value = lambda request: expected
                actual = assistant_route.assistant_chat(
                    AssistantChatRequest(message="Find the python file I edited yesterday", provider="local")
                )

        self.assertEqual("local", actual.provider)
        self.assertEqual(1, len(actual.suggested_actions))
        self.assertEqual("SEARCH_FILES", actual.suggested_actions[0].action)
        self.assertEqual("python yesterday", actual.suggested_actions[0].query)


if __name__ == "__main__":
    unittest.main()
