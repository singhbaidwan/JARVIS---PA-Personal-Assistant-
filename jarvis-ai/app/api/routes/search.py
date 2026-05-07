from fastapi import APIRouter

from app.api.schemas import SearchRequest, SearchResponse
from app.search import search_files

router = APIRouter(tags=["search"])


@router.post("/search", response_model=SearchResponse)
def search(request: SearchRequest) -> SearchResponse:
    indexed_count, results = search_files(
        query=request.query,
        roots=request.roots,
        max_results=request.max_results,
        include_content=request.include_content,
        reference_time=request.reference_time,
    )
    return SearchResponse(
        query=request.query,
        indexed_count=indexed_count,
        results=results,
    )
