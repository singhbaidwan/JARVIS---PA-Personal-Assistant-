import { Search } from "lucide-react";
import { useState } from "react";
import type { SearchResponse, SearchResult } from "../services/jarvisApi";

interface SearchPanelProps {
  onSearch: (query: string, roots: string[]) => Promise<SearchResponse>;
  onError: (message: string) => void;
}

export function SearchPanel({ onSearch, onError }: SearchPanelProps) {
  const [query, setQuery] = useState("Find python file about behavioral outlier");
  const [roots, setRoots] = useState("./jarvis-ai");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [indexedCount, setIndexedCount] = useState<number | null>(null);
  const [isSearching, setIsSearching] = useState(false);

  async function handleSearch() {
    setIsSearching(true);
    try {
      const response = await onSearch(
        query,
        roots
          .split(",")
          .map((root) => root.trim())
          .filter(Boolean),
      );
      setResults(response.results);
      setIndexedCount(response.indexedCount);
    } catch (error) {
      onError(error instanceof Error ? error.message : "Search failed");
      setResults([]);
      setIndexedCount(null);
    } finally {
      setIsSearching(false);
    }
  }

  return (
    <section className="panel search-panel" aria-labelledby="search-title">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Phase 7</p>
          <h2 id="search-title">Smart Search</h2>
        </div>
        <button disabled={isSearching} onClick={handleSearch} type="button">
          <Search size={16} />
          Search
        </button>
      </div>
      <label className="field">
        <span>Query</span>
        <input value={query} onChange={(event) => setQuery(event.target.value)} />
      </label>
      <label className="field">
        <span>Roots</span>
        <input value={roots} onChange={(event) => setRoots(event.target.value)} />
      </label>
      <div className="result-list">
        {indexedCount !== null && <article className="notice">Indexed {indexedCount} files</article>}
        {results.length === 0 ? (
          <article className="notice">No search results loaded.</article>
        ) : (
          results.map((result) => <SearchResultCard result={result} key={result.path} />)
        )}
      </div>
    </section>
  );
}

interface SearchResultCardProps {
  result: SearchResult;
}

function SearchResultCard({ result }: SearchResultCardProps) {
  return (
    <article className="result-card">
      <strong>{result.name}</strong>
      <span className="mono">{result.path}</span>
      <p>{result.snippet || result.reason}</p>
      <small>
        {result.matchType} · score {result.score} · {result.extension || "file"}
      </small>
    </article>
  );
}
