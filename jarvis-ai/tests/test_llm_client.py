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
from app.llm.openai_client import generate_openai_response


class FakeHttpResponse:
    def __init__(self, payload: dict[str, object]) -> None:
        self._payload = payload

    def read(self) -> bytes:
        return json.dumps(self._payload).encode("utf-8")

    def __enter__(self) -> "FakeHttpResponse":
        return self

    def __exit__(self, exc_type, exc, tb) -> bool:  # type: ignore[override]
        return False


class LlmClientTests(unittest.TestCase):
    def test_missing_api_key_raises(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(LlmIntegrationError):
                generate_openai_response(LlmRequest(prompt="hello"))

    def test_successful_openai_response_is_parsed(self) -> None:
        with patch.dict(os.environ, {"OPENAI_API_KEY": "test-key"}, clear=True):
            with patch("app.llm.openai_client.urllib.request.urlopen") as mock_urlopen:
                mock_urlopen.return_value = FakeHttpResponse(
                    {
                        "model": "gpt-4o-mini",
                        "choices": [{"message": {"role": "assistant", "content": "Action plan ready."}}],
                        "usage": {"prompt_tokens": 12, "completion_tokens": 5, "total_tokens": 17},
                    }
                )

                response = generate_openai_response(LlmRequest(prompt="Plan my workday"))

                self.assertEqual("openai", response.provider)
                self.assertEqual("gpt-4o-mini", response.model)
                self.assertEqual("Action plan ready.", response.response)
                self.assertEqual(17, response.metadata["usage"]["total_tokens"])


if __name__ == "__main__":
    unittest.main()
