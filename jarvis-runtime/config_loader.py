from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class ServiceConfig:
    name: str
    command: str
    workdir: Path


ROOT_DIR = Path(__file__).resolve().parents[1]


def load_service_configs() -> list[ServiceConfig]:
    return [
        ServiceConfig(
            name="jarvis-core",
            command="./gradlew --no-daemon bootRun",
            workdir=ROOT_DIR / "jarvis-core",
        ),
        ServiceConfig(
            name="jarvis-ai",
            command="python3 -m uvicorn app.main:app --host 127.0.0.1 --port 8000",
            workdir=ROOT_DIR / "jarvis-ai",
        ),
        ServiceConfig(
            name="jarvis-agent",
            command=(
                "SWIFT_MODULE_CACHE_PATH=../jarvis-data/build/swift-module-cache "
                "CLANG_MODULE_CACHE_PATH=../jarvis-data/build/clang-module-cache "
                "swift run --build-path ../jarvis-data/build/jarvis-agent"
            ),
            workdir=ROOT_DIR / "jarvis-agent",
        ),
    ]
