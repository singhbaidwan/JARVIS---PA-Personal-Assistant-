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

    def test_route_uses_provider_router(self) -> None:
        expected = LlmResponse(
            response="Local endpoint response",
            model="llama3.2:latest",
            provider="local",
            metadata={},
        )
        with patch("app.api.routes.llm.resolve_provider", return_value="local") as mocked_resolve:
            with patch("app.api.routes.llm.generator_for_provider") as mocked_factory:
                mocked_factory.return_value = lambda request: expected
                actual = llm_route.llm(LlmRequest(prompt="hello", provider="local"))

        self.assertEqual(expected, actual)
        mocked_resolve.assert_called_once()
        mocked_factory.assert_called_once_with("local")


if __name__ == "__main__":
    unittest.main()
