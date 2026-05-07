from __future__ import annotations

from pathlib import Path
import os
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.api.schemas import LlmRequest, LlmResponse
from app.llm.provider_router import generator_for_provider, resolve_provider


class ProviderRouterTests(unittest.TestCase):
    def test_resolve_provider_from_request(self) -> None:
        provider = resolve_provider(LlmRequest(prompt="hi", provider="  CLAUDE "))
        self.assertEqual("claude", provider)

    def test_resolve_provider_from_env_default(self) -> None:
        with patch.dict(os.environ, {"JARVIS_LLM_PROVIDER": "gemini"}, clear=True):
            provider = resolve_provider(LlmRequest(prompt="hi"))
        self.assertEqual("gemini", provider)

    def test_generator_for_llama_alias_uses_local_client(self) -> None:
        with patch("app.llm.provider_router.generate_local_response") as mocked_local:
            mocked_local.return_value = LlmResponse(
                response="ok",
                model="llama3.2:latest",
                provider="llama",
                metadata={},
            )
            generator = generator_for_provider("llama")
            result = generator(LlmRequest(prompt="test", provider="llama"))

        self.assertEqual("llama", result.provider)
        mocked_local.assert_called_once()


if __name__ == "__main__":
    unittest.main()
