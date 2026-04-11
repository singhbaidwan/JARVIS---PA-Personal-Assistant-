import os
import signal
import subprocess
from datetime import datetime
from dataclasses import dataclass
from pathlib import Path

from config_loader import ServiceConfig


@dataclass
class ManagedProcess:
    config: ServiceConfig
    pid_file: Path


class ProcessManager:
    def __init__(self, pid_dir: Path):
        self.pid_dir = pid_dir
        self.pid_dir.mkdir(parents=True, exist_ok=True)
        self.log_dir = Path(__file__).resolve().parents[1] / "logs"
        self.log_dir.mkdir(parents=True, exist_ok=True)

    def _pid_file(self, service_name: str) -> Path:
        return self.pid_dir / f"{service_name}.pid"

    def _log_file(self, service_name: str) -> Path:
        return self.log_dir / f"{service_name}.log"

    def start(self, config: ServiceConfig) -> int:
        pid_file = self._pid_file(config.name)
        if pid_file.exists() and self.is_running(config.name):
            return int(pid_file.read_text().strip())

        log_file = self._log_file(config.name)
        with log_file.open("a", encoding="utf-8") as log_handle:
            log_handle.write(f"\n[{datetime.now().isoformat()}] starting {config.name}\n")
            log_handle.flush()

            process = subprocess.Popen(
                config.command,
                cwd=config.workdir,
                shell=True,
                stdout=log_handle,
                stderr=subprocess.STDOUT,
                start_new_session=True,
            )

        pid_file.write_text(str(process.pid))
        return process.pid

    def stop(self, service_name: str) -> bool:
        pid_file = self._pid_file(service_name)
        if not pid_file.exists():
            return False

        pid = int(pid_file.read_text().strip())
        try:
            os.kill(pid, signal.SIGTERM)
        except PermissionError:
            return False
        except ProcessLookupError:
            pid_file.unlink(missing_ok=True)
            return False

        pid_file.unlink(missing_ok=True)
        return True

    def is_running(self, service_name: str) -> bool:
        pid_file = self._pid_file(service_name)
        if not pid_file.exists():
            return False

        pid = int(pid_file.read_text().strip())
        try:
            os.kill(pid, 0)
            return True
        except PermissionError:
            return True
        except ProcessLookupError:
            pid_file.unlink(missing_ok=True)
            return False
