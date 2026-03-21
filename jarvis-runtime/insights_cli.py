from __future__ import annotations

import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Optional


def print_daily_insights(base_url: str, date: Optional[str] = None) -> None:
    endpoint = f"{base_url.rstrip('/')}/insights/daily"
    if date:
        endpoint = f"{endpoint}?{urllib.parse.urlencode({'date': date})}"

    try:
        with urllib.request.urlopen(endpoint, timeout=5) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        message = error.read().decode("utf-8", errors="replace")
        print(f"failed to fetch insights: status={error.code} body={message}", file=sys.stderr)
        return
    except urllib.error.URLError as error:
        print(f"failed to fetch insights: {error.reason}", file=sys.stderr)
        return

    date_value = payload.get("date", "unknown-date")
    apps = payload.get("apps", {})
    total = payload.get("totalTracked", "0m")

    print(f"Today's Usage ({date_value}):")
    if not apps:
        print("- No tracked app usage yet")
        return

    for app, duration in apps.items():
        print(f"- {app}: {duration}")
    print(f"Total: {total}")
