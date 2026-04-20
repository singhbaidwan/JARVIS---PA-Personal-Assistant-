from __future__ import annotations

from pathlib import Path
import json
import os
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.api.routes import llm as llm_route
from app.api.schemas import LlmRequest, LlmResponse
from app.llm.local_client import generate_local_response


class FakeHttpResponse:
    def __init__(self, payload: dict[str, object]) -> None:
        self._payload = payload

    def read(self) -> bytes:
        return json.dumps(self._payload).encode("utf-8")

    def __enter__(self) -> "FakeHttpResponse":
        return self

    def __exit__(self, exc_type, exc, tb) -> bool:  # type: ignore[override]
        return False


class LocalLlmClientTests(unittest.TestCase):
    def test_successful_local_response_is_parsed(self) -> None:
        with patch.dict(os.environ, {"LOCAL_LLM_BASE_URL": "http://127.0.0.1:11434"}, clear=True):
            with patch("app.llm.local_client.urllib.request.urlopen") as mock_urlopen:
                mock_urlopen.return_value = FakeHttpResponse(
                    {
                        "model": "llama3.2:latest",
                        "message": {"role": "assistant", "content": "Local plan ready."},
                        "prompt_eval_count": 42,
                        "eval_count": 14,
                    }
                )

                response = generate_local_response(
                    LlmRequest(prompt="Plan my next block", provider="local")
                )

                self.assertEqual("local", response.provider)
                self.assertEqual("llama3.2:latest", response.model)
                self.assertEqual("Local plan ready.", response.response)
                self.assertEqual(42, response.metadata["prompt_eval_count"])

    def test_route_uses_local_provider_when_requested(self) -> None:
        expected = LlmResponse(
            response="Local endpoint response",
            model="llama3.2:latest",
            provider="local",
            metadata={},
        )
        with patch("app.api.routes.llm.generate_local_response", return_value=expected) as mocked:
            actual = llm_route.llm(LlmRequest(prompt="hello", provider="local"))
            self.assertEqual(expected, actual)
            mocked.assert_called_once()

    def test_route_uses_local_provider_from_env_default(self) -> None:
        expected = LlmResponse(
            response="Env local response",
            model="llama3.2:latest",
            provider="local",
            metadata={},
        )
        with patch.dict(os.environ, {"JARVIS_LLM_PROVIDER": "local"}, clear=True):
            with patch("app.api.routes.llm.generate_local_response", return_value=expected) as mocked:
                actual = llm_route.llm(LlmRequest(prompt="hello"))
                self.assertEqual(expected, actual)
                mocked.assert_called_once()


if __name__ == "__main__":
    unittest.main()
