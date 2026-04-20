from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from app.api.schemas import EventInput, Recommendation

PRODUCTIVE_APPS = {
    "VS CODE",
    "XCODE",
    "INTELLIJ IDEA",
    "TERMINAL",
    "ITERM2",
    "PYCHARM",
    "WEBSTORM",
    "NOTION",
    "OBSIDIAN",
}

DISTRACTING_APPS = {
    "YOUTUBE",
    "TWITTER",
    "X",
    "INSTAGRAM",
    "TIKTOK",
    "REDDIT",
    "NETFLIX",
    "DISCORD",
}


@dataclass(frozen=True)
class PredictionResult:
    predicted_action: str
    confidence: float
    reason: str
    context: dict[str, object]


@dataclass(frozen=True)
class AnomalyResult:
    anomaly_detected: bool
    reason: str
    severity: str
    score: float
    signals: list[str]


@dataclass(frozen=True)
class RecommendationResult:
    focus_score: float
    peak_productivity_window: str | None
    summary: str
    recommendations: list[Recommendation]


def predict_next_action(events: list[EventInput], reference_time: datetime | None = None) -> PredictionResult:
    reference = _resolve_reference_time(events, reference_time)
    app_events = _extract_app_events(events)
    if not app_events:
        return PredictionResult(
            predicted_action="NO_ACTION",
            confidence=0.2,
            reason="No usable app activity found in recent events",
            context={"event_count": len(events)},
        )

    context_key = _day_context(reference)
    context_events = [
        app
        for ts, app in app_events
        if ts is not None and _day_context(ts) == context_key and _hour_distance(ts.hour, reference.hour) <= 1
    ]

    if context_events:
        counts = Counter(context_events)
        app, count = counts.most_common(1)[0]
        total = sum(counts.values())
        confidence = min(0.95, 0.55 + (count / max(total, 1)) * 0.35)
        reason = (
            f"Detected a {context_key.lower()} routine around {reference.hour:02d}:00; "
            f"{app} appears most frequently."
        )
        context = {"routine_scope": "contextual", "hour": reference.hour, "day_context": context_key}
    else:
        counts = Counter(app for _, app in app_events)
        app, count = counts.most_common(1)[0]
        total = sum(counts.values())
        confidence = min(0.7, 0.35 + (count / max(total, 1)) * 0.35)
        reason = f"Fallback to overall usage pattern; {app} is the most frequent app."
        context = {"routine_scope": "global", "day_context": context_key}

    return PredictionResult(
        predicted_action=f"OPEN_APP:{app}",
        confidence=round(confidence, 3),
        reason=reason,
        context=context,
    )


def detect_anomalies(events: list[EventInput], reference_time: datetime | None = None) -> AnomalyResult:
    reference = _resolve_reference_time(events, reference_time)
    app_events = _extract_app_events(events)

    score = 0.0
    signals: list[str] = []

    cpu_spikes = _count_threshold_hits(events, "cpu_percent", 85)
    if cpu_spikes:
        score += 30.0
        signals.append(f"CPU usage spike detected in {cpu_spikes} event(s)")

    memory_spikes = _count_threshold_hits(events, "memory_percent", 90)
    if memory_spikes:
        score += 30.0
        signals.append(f"Memory usage spike detected in {memory_spikes} event(s)")

    network_spikes = _count_threshold_hits(events, "network_kbps", 50_000)
    if network_spikes:
        score += 25.0
        signals.append(f"Network throughput spike detected in {network_spikes} event(s)")

    recent_volume = _count_recent_events(events, reference, timedelta(hours=1))
    baseline = _hourly_baseline(events)
    if baseline > 0 and recent_volume >= max(20, baseline * 2):
        score += 20.0
        signals.append(
            f"Event volume anomaly: last hour={recent_volume} vs hourly baseline={baseline:.1f}"
        )

    behavioral_signal = _behavioral_outlier_signal(app_events, reference)
    if behavioral_signal:
        score += 25.0
        signals.append(behavioral_signal)

    score = min(score, 100.0)
    severity = _severity_from_score(score)
    anomaly_detected = score >= 25.0
    reason = signals[0] if signals else "No anomaly detected"

    return AnomalyResult(
        anomaly_detected=anomaly_detected,
        reason=reason,
        severity=severity,
        score=round(score, 2),
        signals=signals,
    )


def generate_recommendations(
    events: list[EventInput], reference_time: datetime | None = None
) -> RecommendationResult:
    reference = _resolve_reference_time(events, reference_time)
    app_events = _extract_app_events(events)
    focus_score, distraction_ratio, switch_rate = _focus_metrics(app_events)
    peak_window = _peak_productivity_window(app_events)

    recommendations: list[Recommendation] = []
    if focus_score < 55:
        recommendations.append(
            Recommendation(
                category="focus",
                message="Start a 25-minute focus block and pause non-essential apps.",
                confidence=0.84,
                details={"focus_score": focus_score},
            )
        )

    if distraction_ratio > 0.25:
        recommendations.append(
            Recommendation(
                category="distraction",
                message="Distraction activity is elevated. Consider muting social apps for 1 hour.",
                confidence=0.8,
                details={"distraction_ratio": round(distraction_ratio, 3)},
            )
        )

    if _long_stretch_without_break(app_events):
        recommendations.append(
            Recommendation(
                category="break",
                message="You have been active for a long stretch. Take a 5-minute break.",
                confidence=0.9,
                details={},
            )
        )

    prediction = predict_next_action(events, reference)
    if prediction.predicted_action.startswith("OPEN_APP:") and prediction.confidence >= 0.6:
        app = prediction.predicted_action.split(":", 1)[1]
        recommendations.append(
            Recommendation(
                category="workflow",
                message=f"Create a one-click workflow to open {app} at your routine time.",
                confidence=round(min(0.9, prediction.confidence), 3),
                details={"day_context": prediction.context.get("day_context")},
            )
        )

    if peak_window:
        recommendations.append(
            Recommendation(
                category="schedule",
                message=f"Your peak productivity window is {peak_window}. Reserve it for deep work.",
                confidence=0.7,
                details={"switch_rate": round(switch_rate, 3)},
            )
        )

    if not recommendations:
        recommendations.append(
            Recommendation(
                category="status",
                message="No major issues detected. Current usage pattern looks stable.",
                confidence=0.7,
                details={"focus_score": focus_score},
            )
        )

    summary = (
        f"Focus score {focus_score:.1f}/100 with "
        f"{len(recommendations)} recommendation(s) generated for {_day_context(reference).lower()} context."
    )
    return RecommendationResult(
        focus_score=round(focus_score, 2),
        peak_productivity_window=peak_window,
        summary=summary,
        recommendations=recommendations,
    )


def _extract_app_events(events: list[EventInput]) -> list[tuple[datetime | None, str]]:
    extracted: list[tuple[datetime | None, str]] = []
    for event in events:
        app = _extract_app_name(event)
        if not app:
            continue
        extracted.append((_event_timestamp(event), app))
    return extracted


def _extract_app_name(event: EventInput) -> str | None:
    for key in ("to", "app", "target_app", "targetApp", "application", "process"):
        value = event.payload.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None


def _event_timestamp(event: EventInput) -> datetime | None:
    if event.timestamp is not None:
        return _to_utc(event.timestamp)

    payload_timestamp = event.payload.get("timestamp")
    if isinstance(payload_timestamp, str):
        try:
            return _to_utc(datetime.fromisoformat(payload_timestamp.replace("Z", "+00:00")))
        except ValueError:
            return None
    return None


def _to_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=UTC)
    return value.astimezone(UTC)


def _resolve_reference_time(events: list[EventInput], reference_time: datetime | None) -> datetime:
    if reference_time is not None:
        return _to_utc(reference_time)

    timestamps = [ts for ts in (_event_timestamp(event) for event in events) if ts is not None]
    if timestamps:
        return max(timestamps)
    return datetime.now(UTC)


def _day_context(value: datetime) -> str:
    return "WEEKEND" if value.weekday() >= 5 else "WEEKDAY"


def _hour_distance(hour_a: int, hour_b: int) -> int:
    return min((hour_a - hour_b) % 24, (hour_b - hour_a) % 24)


def _count_threshold_hits(events: list[EventInput], key: str, threshold: float) -> int:
    hits = 0
    for event in events:
        value = event.payload.get(key)
        if isinstance(value, (int, float)) and float(value) >= threshold:
            hits += 1
    return hits


def _count_recent_events(events: list[EventInput], reference: datetime, window: timedelta) -> int:
    minimum = reference - window
    count = 0
    for event in events:
        ts = _event_timestamp(event)
        if ts is not None and ts >= minimum:
            count += 1
    return count


def _hourly_baseline(events: list[EventInput]) -> float:
    timestamps = [ts for ts in (_event_timestamp(event) for event in events) if ts is not None]
    if len(timestamps) < 2:
        return 0.0
    earliest = min(timestamps)
    latest = max(timestamps)
    span_hours = max((latest - earliest).total_seconds() / 3600.0, 1.0)
    return len(timestamps) / span_hours


def _behavioral_outlier_signal(
    app_events: list[tuple[datetime | None, str]], reference: datetime
) -> str | None:
    dated = [(ts, app) for ts, app in app_events if ts is not None]
    if len(dated) < 10:
        return None

    _, latest_app = max(dated, key=lambda item: item[0])
    overall_counts = Counter(app for _, app in dated)
    latest_frequency = overall_counts[latest_app] / max(len(dated), 1)

    contextual_counts = Counter(
        app
        for ts, app in dated
        if _day_context(ts) == _day_context(reference) and _hour_distance(ts.hour, reference.hour) <= 1
    )
    contextual_frequency = contextual_counts.get(latest_app, 0) / max(sum(contextual_counts.values()), 1)

    if latest_frequency <= 0.05 and contextual_frequency == 0:
        return f"Behavioral outlier: {latest_app} is unusual for this time context"
    return None


def _focus_metrics(app_events: list[tuple[datetime | None, str]]) -> tuple[float, float, float]:
    if not app_events:
        return 0.0, 0.0, 0.0

    productive = 0
    distracting = 0
    sequence = [app for _, app in app_events]
    for app in sequence:
        upper = app.upper()
        if upper in PRODUCTIVE_APPS:
            productive += 1
        if upper in DISTRACTING_APPS:
            distracting += 1

    switches = 0
    for previous, current in zip(sequence, sequence[1:]):
        if previous != current:
            switches += 1

    total = len(sequence)
    productive_ratio = productive / total
    distraction_ratio = distracting / total
    switch_rate = switches / max(total - 1, 1)

    focus_score = productive_ratio * 100 - distraction_ratio * 25 - switch_rate * 20 + 10
    focus_score = max(0.0, min(100.0, focus_score))
    return focus_score, distraction_ratio, switch_rate


def _peak_productivity_window(app_events: list[tuple[datetime | None, str]]) -> str | None:
    per_hour: dict[int, int] = defaultdict(int)
    for ts, app in app_events:
        if ts is None:
            continue
        if app.upper() not in PRODUCTIVE_APPS:
            continue
        per_hour[ts.hour] += 1

    if not per_hour:
        return None

    best_hour = max(per_hour.items(), key=lambda item: item[1])[0]
    next_hour = (best_hour + 1) % 24
    return f"{best_hour:02d}:00-{next_hour:02d}:00"


def _long_stretch_without_break(app_events: list[tuple[datetime | None, str]]) -> bool:
    dated_events = sorted((ts, app) for ts, app in app_events if ts is not None)
    if len(dated_events) < 2:
        return False

    max_stretch = timedelta(0)
    stretch_start = dated_events[0][0]
    previous = dated_events[0][0]
    for ts, _ in dated_events[1:]:
        if ts - previous <= timedelta(minutes=15):
            stretch = ts - stretch_start
            if stretch > max_stretch:
                max_stretch = stretch
        else:
            stretch_start = ts
        previous = ts

    return max_stretch >= timedelta(hours=2)


def _severity_from_score(score: float) -> str:
    if score >= 80:
        return "CRITICAL"
    if score >= 60:
        return "HIGH"
    if score >= 40:
        return "MEDIUM"
    if score >= 20:
        return "LOW"
    return "NONE"
