from __future__ import annotations

from dataclasses import dataclass, field
from typing import Sequence

import requests


@dataclass
class SMSetup:
    server_url: str = ""
    app_key: str = ""
    secret_key: str = ""
    message: str = ""
    target_type: str = "ALL"  # ALL | USER | GROUP
    target_user_id: str = ""
    target_users: Sequence[str] = field(default_factory=list)
    timeout_s: float = 5.0

    def set_server_url(self, server_url: str) -> "SMSetup":
        self.server_url = (server_url or "").strip()
        return self

    def set_app_key(self, app_key: str) -> "SMSetup":
        self.app_key = (app_key or "").strip()
        return self

    def set_secret_key(self, secret_key: str) -> "SMSetup":
        self.secret_key = (secret_key or "").strip()
        return self

    def set_message(self, message: str) -> "SMSetup":
        self.message = message or ""
        return self

    def set_target_type(self, target_type: str) -> "SMSetup":
        self.target_type = (target_type or "ALL").upper()
        return self

    def set_target_user_id(self, user_id: str) -> "SMSetup":
        self.target_user_id = (user_id or "").strip()
        return self

    def set_target_users(self, user_ids: Sequence[str]) -> "SMSetup":
        self.target_users = list(user_ids or [])
        return self

    def _headers(self) -> dict:
        return {
            "X-App-Id": self.app_key,
            "X-App-Secret-Key": self.secret_key,
        }

    def push(self) -> str:
        if not self.server_url or not self.app_key or not self.secret_key:
            raise ValueError("server_url/app_key/secret_key required")

        if self.target_type == "ALL":
            r = requests.post(
                f"{self.server_url}/push/broadcast",
                params={"message": self.message},
                headers=self._headers(),
                timeout=self.timeout_s,
            )
            r.raise_for_status()
            return r.text

        if self.target_type == "USER":
            r = requests.post(
                f"{self.server_url}/push/user",
                params={"userId": self.target_user_id, "message": self.message},
                headers=self._headers(),
                timeout=self.timeout_s,
            )
            r.raise_for_status()
            return r.text

        r = requests.post(
            f"{self.server_url}/push/users",
            json={"userIds": list(self.target_users), "message": self.message},
            headers={**self._headers(), "Content-Type": "application/json"},
            timeout=self.timeout_s,
        )
        r.raise_for_status()
        return r.text
