from __future__ import annotations

from pathlib import Path
import json
import os
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.api.schemas import LlmRequest
from app.llm.anthropic_client import generate_anthropic_response
from app.llm.errors import LlmIntegrationError


class FakeHttpResponse:
    def __init__(self, payload: dict[str, object]) -> None:
        self._payload = payload

    def read(self) -> bytes:
        return json.dumps(self._payload).encode("utf-8")

    def __enter__(self) -> "FakeHttpResponse":
        return self

    def __exit__(self, exc_type, exc, tb) -> bool:  # type: ignore[override]
        return False


class AnthropicClientTests(unittest.TestCase):
    def test_missing_api_key_raises(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(LlmIntegrationError):
                generate_anthropic_response(LlmRequest(prompt="hello", provider="claude"))

    def test_successful_anthropic_response_is_parsed(self) -> None:
        with patch.dict(os.environ, {"ANTHROPIC_API_KEY": "test-key"}, clear=True):
            with patch("app.llm.anthropic_client.urllib.request.urlopen") as mock_urlopen:
                mock_urlopen.return_value = FakeHttpResponse(
                    {
                        "model": "claude-3-5-sonnet-latest",
                        "content": [{"type": "text", "text": "I can help with that plan."}],
                        "usage": {"input_tokens": 10, "output_tokens": 22},
                    }
                )

                response = generate_anthropic_response(LlmRequest(prompt="Plan my week", provider="claude"))

                self.assertEqual("claude", response.provider)
                self.assertEqual("claude-3-5-sonnet-latest", response.model)
                self.assertEqual("I can help with that plan.", response.response)
                self.assertEqual(22, response.metadata["usage"]["output_tokens"])


if __name__ == "__main__":
    unittest.main()
