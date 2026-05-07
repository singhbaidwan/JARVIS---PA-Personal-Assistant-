import argparse
import sys
import time
from pathlib import Path

from config_loader import load_service_configs
from health_checker import check_http
from insights_cli import print_daily_insights
from process_manager import ProcessManager

HEALTH_ENDPOINTS = {
    "jarvis-core": "http://127.0.0.1:8080/health",
    "jarvis-ai": "http://127.0.0.1:8000/health",
}
STARTUP_HEALTH_TIMEOUT_SECONDS = 90
STARTUP_HEALTH_POLL_SECONDS = 2


def wait_for_health(url: str, timeout_seconds: int = STARTUP_HEALTH_TIMEOUT_SECONDS) -> bool:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if check_http(url):
            return True
        time.sleep(STARTUP_HEALTH_POLL_SECONDS)
    return check_http(url)


def run() -> None:
    parser = argparse.ArgumentParser(description="jarvis runtime process manager")
    parser.add_argument("action", choices=["start", "stop", "status", "insights"])
    parser.add_argument("--date", help="date in YYYY-MM-DD for insights lookup")
    args = parser.parse_args()

    pid_dir = Path(__file__).resolve().parent / ".pids"
    manager = ProcessManager(pid_dir=pid_dir)
    services = load_service_configs()

    if args.action == "start":
        print("starting services...")
        start_failures = 0
        for service in services:
            was_running = manager.is_running(service.name)
            try:
                pid = manager.start(service)
            except Exception as exc:
                start_failures += 1
                print(f"{service.name}: start failed ({exc})")
                continue

            state = "already running" if was_running else "started"
            print(f"{service.name}: {state} pid={pid}")

        print("checking startup health for core services...")
        health_failures = 0
        for service_name, health_url in HEALTH_ENDPOINTS.items():
            service_known = any(service.name == service_name for service in services)
            if not service_known:
                continue

            if not manager.is_running(service_name):
                print(f"{service_name}: health=failed (process not running) url={health_url}")
                health_failures += 1
                continue

            healthy = wait_for_health(health_url)
            status = "ok" if healthy else "failed"
            print(f"{service_name}: health={status} url={health_url}")
            if not healthy:
                health_failures += 1

        if start_failures or health_failures:
            print(
                "startup finished with issues "
                f"(start_failures={start_failures}, health_failures={health_failures})"
            )
            sys.exit(1)

        print("startup complete: all services started and core health checks passed")

    elif args.action == "stop":
        print("stopping services...")
        stop_failures = 0
        for service in reversed(services):
            was_running = manager.is_running(service.name)
            try:
                stopped = manager.stop(service.name)
            except Exception as exc:
                stop_failures += 1
                print(f"{service.name}: stop failed ({exc})")
                continue

            if stopped:
                print(f"{service.name}: stopped")
            elif was_running:
                stop_failures += 1
                print(f"{service.name}: failed to stop")
            else:
                print(f"{service.name}: not running")

        if stop_failures:
            print(f"shutdown finished with issues (stop_failures={stop_failures})")
            sys.exit(1)

        print("shutdown complete")

    elif args.action == "status":
        for service in services:
            running = manager.is_running(service.name)
            health_url = HEALTH_ENDPOINTS.get(service.name)
            health = "n/a"
            if health_url:
                health = "ok" if check_http(health_url) else "down"
            print(f"{service.name}: running={running} health={health}")

    elif args.action == "insights":
        print_daily_insights(
            base_url="http://127.0.0.1:8080",
            date=args.date,
        )


if __name__ == "__main__":
    run()
