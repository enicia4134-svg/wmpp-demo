from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, Optional, Sequence

import requests


@dataclass(frozen=True)
class WmppServerClient:
    base_url: str
    app_id: str
    app_secret: str
    timeout_s: float = 5.0

    def _headers(self) -> dict:
        return {
            "X-App-Id": self.app_id,
            "X-App-Secret-Key": self.app_secret,
        }

    def broadcast(self, msg: str) -> str:
        r = requests.post(
            f"{self.base_url}/push/broadcast",
            params={"message": msg},
            headers=self._headers(),
            timeout=self.timeout_s,
        )
        r.raise_for_status()
        return r.text

    def push_user(self, user_id: str, msg: str) -> str:
        r = requests.post(
            f"{self.base_url}/push/user",
            params={"userId": user_id, "message": msg},
            headers=self._headers(),
            timeout=self.timeout_s,
        )
        r.raise_for_status()
        return r.text

    def push_users(self, user_ids: Sequence[str], msg: str) -> str:
        r = requests.post(
            f"{self.base_url}/push/users",
            json={"userIds": list(user_ids), "message": msg},
            headers={**self._headers(), "Content-Type": "application/json"},
            timeout=self.timeout_s,
        )
        r.raise_for_status()
        return r.text

    def push_topic(self, topic: str, msg: str) -> str:
        r = requests.post(
            f"{self.base_url}/push/topic",
            params={"topic": topic, "message": msg},
            headers=self._headers(),
            timeout=self.timeout_s,
        )
        r.raise_for_status()
        return r.text

