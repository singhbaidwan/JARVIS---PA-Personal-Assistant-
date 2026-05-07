from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import UTC, datetime, time, timedelta
from pathlib import Path

from app.api.schemas import SearchResult

TEXT_EXTENSIONS = {
    ".bash",
    ".css",
    ".csv",
    ".env",
    ".html",
    ".java",
    ".js",
    ".json",
    ".kt",
    ".kts",
    ".md",
    ".py",
    ".rb",
    ".rs",
    ".sh",
    ".sql",
    ".swift",
    ".toml",
    ".ts",
    ".tsx",
    ".txt",
    ".xml",
    ".yaml",
    ".yml",
}

LANGUAGE_EXTENSIONS = {
    "bash": {".bash", ".sh"},
    "code": TEXT_EXTENSIONS,
    "css": {".css"},
    "html": {".html"},
    "java": {".java"},
    "javascript": {".js", ".jsx"},
    "json": {".json"},
    "kotlin": {".kt", ".kts"},
    "markdown": {".md"},
    "python": {".py"},
    "rust": {".rs"},
    "sql": {".sql"},
    "swift": {".swift"},
    "typescript": {".ts", ".tsx"},
    "yaml": {".yaml", ".yml"},
}

SKIPPED_DIRS = {
    ".build",
    ".git",
    ".gradle",
    "__pycache__",
    "build",
    "dist",
    "node_modules",
    "target",
}

MAX_INDEXED_FILES = 2_000
MAX_CONTENT_BYTES = 64_000


@dataclass(frozen=True)
class IndexedFile:
    path: Path
    name: str
    extension: str
    size_bytes: int
    modified_at: datetime
    content: str


@dataclass(frozen=True)
class SearchFilters:
    extensions: set[str]
    modified_after: datetime | None
    modified_before: datetime | None
    content_terms: set[str]


def search_files(
    query: str,
    roots: list[str],
    max_results: int = 10,
    include_content: bool = True,
    reference_time: datetime | None = None,
) -> tuple[int, list[SearchResult]]:
    reference = _to_utc(reference_time) if reference_time else datetime.now(UTC)
    filters = _filters_from_query(query, reference)
    query_tokens = _tokenize(query) - _filter_words(filters)

    indexed_files = _index_roots(roots, include_content)
    scored = []
    for item in indexed_files:
        if filters.extensions and item.extension not in filters.extensions:
            continue
        if filters.modified_after and item.modified_at < filters.modified_after:
            continue
        if filters.modified_before and item.modified_at >= filters.modified_before:
            continue

        score, match_type, reason = _score_file(item, query_tokens, filters)
        if score <= 0:
            continue

        scored.append(
            SearchResult(
                path=str(item.path),
                name=item.name,
                extension=item.extension,
                size_bytes=item.size_bytes,
                modified_at=item.modified_at,
                score=round(min(score, 100.0), 2),
                match_type=match_type,
                snippet=_snippet(item.content, query_tokens | filters.content_terms) if include_content else None,
                reason=reason,
            )
        )

    scored.sort(key=lambda result: (result.score, result.modified_at), reverse=True)
    return len(indexed_files), scored[:max_results]


def _index_roots(roots: list[str], include_content: bool) -> list[IndexedFile]:
    indexed: list[IndexedFile] = []
    for root in roots:
        root_path = Path(root).expanduser().resolve()
        if not root_path.exists():
            continue

        candidates = [root_path] if root_path.is_file() else _walk_files(root_path)
        for path in candidates:
            if len(indexed) >= MAX_INDEXED_FILES:
                return indexed
            if not path.is_file() or _should_skip(path):
                continue

            try:
                stat = path.stat()
            except OSError:
                continue

            extension = path.suffix.lower()
            content = ""
            if include_content and extension in TEXT_EXTENSIONS and stat.st_size <= MAX_CONTENT_BYTES:
                content = _read_text(path)

            indexed.append(
                IndexedFile(
                    path=path,
                    name=path.name,
                    extension=extension,
                    size_bytes=stat.st_size,
                    modified_at=datetime.fromtimestamp(stat.st_mtime, tz=UTC),
                    content=content,
                )
            )
    return indexed


def _walk_files(root: Path):
    for path in root.rglob("*"):
        if any(part in SKIPPED_DIRS for part in path.parts):
            continue
        yield path


def _should_skip(path: Path) -> bool:
    return any(part in SKIPPED_DIRS for part in path.parts) or path.name.startswith(".DS_Store")


def _read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return ""


def _filters_from_query(query: str, reference: datetime) -> SearchFilters:
    lowered = query.lower()
    extensions: set[str] = set()
    for token, mapped_extensions in LANGUAGE_EXTENSIONS.items():
        if re.search(rf"\b{re.escape(token)}\b", lowered):
            extensions.update(mapped_extensions)

    explicit_extensions = {f".{match.lower()}" for match in re.findall(r"\.([a-zA-Z0-9]{1,8})\b", query)}
    extensions.update(explicit_extensions)

    modified_after = None
    modified_before = None
    if "yesterday" in lowered:
        target = reference.date() - timedelta(days=1)
        modified_after = datetime.combine(target, time.min, tzinfo=UTC)
        modified_before = modified_after + timedelta(days=1)
    elif "today" in lowered:
        target = reference.date()
        modified_after = datetime.combine(target, time.min, tzinfo=UTC)
        modified_before = modified_after + timedelta(days=1)
    elif "last week" in lowered:
        modified_after = reference - timedelta(days=7)

    quoted_terms = {term.strip().lower() for term in re.findall(r'"([^"]+)"', query) if term.strip()}
    return SearchFilters(
        extensions=extensions,
        modified_after=modified_after,
        modified_before=modified_before,
        content_terms=quoted_terms,
    )


def _score_file(
    item: IndexedFile,
    query_tokens: set[str],
    filters: SearchFilters,
) -> tuple[float, str, str]:
    name_tokens = _tokenize(item.name)
    path_tokens = _tokenize(str(item.path))
    content_tokens = _tokenize(item.content)

    name_hits = query_tokens & name_tokens
    path_hits = query_tokens & path_tokens
    content_hits = query_tokens & content_tokens
    phrase_hits = {term for term in filters.content_terms if term in item.content.lower()}

    score = 0.0
    reasons: list[str] = []
    if name_hits:
        score += 45.0 + len(name_hits) * 5.0
        reasons.append(f"name matched {', '.join(sorted(name_hits))}")
    if path_hits:
        score += 18.0 + len(path_hits) * 2.0
        reasons.append(f"path matched {', '.join(sorted(path_hits))}")
    if content_hits:
        score += 25.0 + len(content_hits) * 2.0
        reasons.append(f"content matched {', '.join(sorted(content_hits)[:4])}")
    if phrase_hits:
        score += 35.0
        reasons.append("quoted phrase matched")
    if filters.extensions and item.extension in filters.extensions:
        score += 20.0
        reasons.append(f"extension matched {item.extension}")
    if filters.modified_after or filters.modified_before:
        score += 15.0
        reasons.append("modified time matched")

    if not reasons and not query_tokens and (filters.extensions or filters.modified_after):
        score = 20.0
        reasons.append("metadata matched")

    if name_hits:
        match_type = "filename"
    elif content_hits or phrase_hits:
        match_type = "content"
    elif filters.extensions or filters.modified_after:
        match_type = "metadata"
    else:
        match_type = "semantic"

    return score, match_type, "; ".join(reasons) if reasons else "No meaningful match"


def _snippet(content: str, terms: set[str]) -> str | None:
    if not content:
        return None

    lowered = content.lower()
    positions = [lowered.find(term.lower()) for term in terms if term and lowered.find(term.lower()) >= 0]
    start = max(0, min(positions) - 80) if positions else 0
    snippet = content[start : start + 220].replace("\n", " ").strip()
    return re.sub(r"\s+", " ", snippet) if snippet else None


def _tokenize(value: str) -> set[str]:
    return {
        token
        for token in re.findall(r"[a-zA-Z0-9_]+", value.lower())
        if len(token) > 1 and token not in STOP_WORDS
    }


def _filter_words(filters: SearchFilters) -> set[str]:
    words = {"file", "files", "find", "search", "edited", "modified", "opened"}
    for extensions in LANGUAGE_EXTENSIONS.values():
        if filters.extensions.intersection(extensions):
            words.update(key for key, value in LANGUAGE_EXTENSIONS.items() if value == extensions)
    return words


def _to_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=UTC)
    return value.astimezone(UTC)


STOP_WORDS = {
    "a",
    "an",
    "and",
    "at",
    "for",
    "from",
    "i",
    "in",
    "me",
    "my",
    "of",
    "on",
    "or",
    "the",
    "to",
}
