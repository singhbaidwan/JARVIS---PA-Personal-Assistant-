import os
import signal
import subprocess
import time
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

    def _read_pid(self, service_name: str) -> int | None:
        pid_file = self._pid_file(service_name)
        if not pid_file.exists():
            return None

        try:
            pid = int(pid_file.read_text().strip())
        except (TypeError, ValueError):
            pid_file.unlink(missing_ok=True)
            return None

        if pid <= 0:
            pid_file.unlink(missing_ok=True)
            return None

        return pid

    def _is_pid_alive(self, pid: int) -> bool:
        # If this runtime process is the parent, reap defunct children so
        # zombie processes do not look "alive" during stop checks.
        try:
            reaped_pid, _ = os.waitpid(pid, os.WNOHANG)
            if reaped_pid == pid:
                return False
        except ChildProcessError:
            pass
        except OSError:
            pass

        try:
            os.kill(pid, 0)
            return True
        except PermissionError:
            return True
        except ProcessLookupError:
            return False

    def _send_signal(self, pid: int, sig: int) -> bool:
        # Start with process-group signaling because services run in their own
        # session and may spawn subprocesses that need the same signal.
        try:
            os.killpg(pid, sig)
            return True
        except ProcessLookupError:
            pass
        except PermissionError:
            pass
        except OSError:
            pass

        try:
            os.kill(pid, sig)
            return True
        except PermissionError:
            return False
        except ProcessLookupError:
            return False

    def _wait_for_exit(self, pid: int, timeout_seconds: float, poll_interval_seconds: float = 0.25) -> bool:
        deadline = time.monotonic() + timeout_seconds
        while time.monotonic() < deadline:
            if not self._is_pid_alive(pid):
                return True
            time.sleep(poll_interval_seconds)
        return not self._is_pid_alive(pid)

    def start(self, config: ServiceConfig) -> int:
        pid_file = self._pid_file(config.name)
        existing_pid = self._read_pid(config.name)
        if existing_pid is not None and self._is_pid_alive(existing_pid):
            return existing_pid

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

    def stop(
        self,
        service_name: str,
        grace_timeout_seconds: float = 8.0,
        kill_timeout_seconds: float = 2.0,
    ) -> bool:
        pid_file = self._pid_file(service_name)
        pid = self._read_pid(service_name)
        if pid is None:
            return False

        if not self._send_signal(pid, signal.SIGTERM):
            if not self._is_pid_alive(pid):
                pid_file.unlink(missing_ok=True)
                return False
            return False

        if self._wait_for_exit(pid, grace_timeout_seconds):
            pid_file.unlink(missing_ok=True)
            return True

        if not self._send_signal(pid, signal.SIGKILL):
            if not self._is_pid_alive(pid):
                pid_file.unlink(missing_ok=True)
                return True
            return False

        if self._wait_for_exit(pid, kill_timeout_seconds):
            pid_file.unlink(missing_ok=True)
            return True

        return False

    def is_running(self, service_name: str) -> bool:
        pid_file = self._pid_file(service_name)
        pid = self._read_pid(service_name)
        if pid is None:
            return False

        if self._is_pid_alive(pid):
            return True

        pid_file.unlink(missing_ok=True)
        return False
