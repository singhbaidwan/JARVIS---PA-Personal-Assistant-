from http.client import HTTPException
import socket
from urllib.error import URLError
from urllib.request import urlopen


def check_http(url: str, timeout: int = 2) -> bool:
    try:
        with urlopen(url, timeout=timeout) as response:
            return 200 <= response.status < 300
    except (URLError, HTTPException, OSError, TimeoutError, socket.timeout):
        return False
