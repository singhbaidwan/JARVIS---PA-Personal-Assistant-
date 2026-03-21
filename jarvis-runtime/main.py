import argparse
from pathlib import Path

from config_loader import load_service_configs
from health_checker import check_http
from insights_cli import print_daily_insights
from process_manager import ProcessManager

HEALTH_ENDPOINTS = {
    "jarvis-core": "http://127.0.0.1:8080/health",
    "jarvis-ai": "http://127.0.0.1:8000/health",
}


def run() -> None:
    parser = argparse.ArgumentParser(description="jarvis runtime process manager")
    parser.add_argument("action", choices=["start", "stop", "status", "insights"])
    parser.add_argument("--date", help="date in YYYY-MM-DD for insights lookup")
    args = parser.parse_args()

    pid_dir = Path(__file__).resolve().parent / ".pids"
    manager = ProcessManager(pid_dir=pid_dir)
    services = load_service_configs()

    if args.action == "start":
        for service in services:
            pid = manager.start(service)
            print(f"started {service.name} pid={pid}")

    elif args.action == "stop":
        for service in services:
            stopped = manager.stop(service.name)
            print(f"stopped {service.name}={stopped}")

    elif args.action == "status":
        for service in services:
            running = manager.is_running(service.name)
            health_url = HEALTH_ENDPOINTS.get(service.name)
            healthy = check_http(health_url) if health_url else None
            print(f"{service.name}: running={running} health={healthy}")

    elif args.action == "insights":
        print_daily_insights(
            base_url="http://127.0.0.1:8080",
            date=args.date,
        )


if __name__ == "__main__":
    run()
