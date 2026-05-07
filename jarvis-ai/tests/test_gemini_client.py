from __future__ import annotations

from pathlib import Path
import json
import os
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.api.schemas import LlmRequest
from app.llm.errors import LlmIntegrationError
from app.llm.gemini_client import generate_gemini_response


class FakeHttpResponse:
    def __init__(self, payload: dict[str, object]) -> None:
        self._payload = payload

    def read(self) -> bytes:
        return json.dumps(self._payload).encode("utf-8")

    def __enter__(self) -> "FakeHttpResponse":
        return self

    def __exit__(self, exc_type, exc, tb) -> bool:  # type: ignore[override]
        return False


class GeminiClientTests(unittest.TestCase):
    def test_missing_api_key_raises(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(LlmIntegrationError):
                generate_gemini_response(LlmRequest(prompt="hello", provider="gemini"))

    def test_successful_gemini_response_is_parsed(self) -> None:
        with patch.dict(os.environ, {"GEMINI_API_KEY": "test-key"}, clear=True):
            with patch("app.llm.gemini_client.urllib.request.urlopen") as mock_urlopen:
                mock_urlopen.return_value = FakeHttpResponse(
                    {
                        "candidates": [
                            {
                                "content": {
                                    "parts": [{"text": "Here is a practical breakdown."}],
                                }
                            }
                        ],
                        "usageMetadata": {"totalTokenCount": 33},
                    }
                )

                response = generate_gemini_response(LlmRequest(prompt="Break this down", provider="gemini"))

                self.assertEqual("gemini", response.provider)
                self.assertEqual("Here is a practical breakdown.", response.response)
                self.assertEqual(33, response.metadata["usage"]["totalTokenCount"])


if __name__ == "__main__":
    unittest.main()
