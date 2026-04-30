export class SMSetup {
  constructor() {
    this.serverUrl = "";
    this.appKey = "";
    this.secretKey = "";
    this.message = "";
    this.targetType = "ALL"; // ALL | USER | GROUP
    this.targetUserId = "";
    this.targetUsers = [];
  }

  setServerUrl(url) { this.serverUrl = (url || "").trim(); return this; }
  setAppKey(appKey) { this.appKey = (appKey || "").trim(); return this; }
  setSecretKey(secretKey) { this.secretKey = (secretKey || "").trim(); return this; }
  setMessage(message) { this.message = message || ""; return this; }
  setTargetType(targetType) { this.targetType = (targetType || "ALL").toUpperCase(); return this; }
  setTargetUserId(userId) { this.targetUserId = (userId || "").trim(); return this; }
  setTargetUsers(users) { this.targetUsers = Array.isArray(users) ? users : []; return this; }

  async push() {
    if (!this.serverUrl || !this.appKey || !this.secretKey) {
      throw new Error("serverUrl/appKey/secretKey required");
    }

    const headers = {
      "X-App-Id": this.appKey,
      "X-App-Secret-Key": this.secretKey,
    };

    if (this.targetType === "ALL") {
      const r = await fetch(`${this.serverUrl}/push/broadcast?message=${encodeURIComponent(this.message)}`, {
        method: "POST",
        headers,
      });
      if (!r.ok) throw new Error(await r.text());
      return await r.text();
    }

    if (this.targetType === "USER") {
      const r = await fetch(`${this.serverUrl}/push/user?userId=${encodeURIComponent(this.targetUserId)}&message=${encodeURIComponent(this.message)}`, {
        method: "POST",
        headers,
      });
      if (!r.ok) throw new Error(await r.text());
      return await r.text();
    }

    const r = await fetch(`${this.serverUrl}/push/users`, {
      method: "POST",
      headers: { ...headers, "Content-Type": "application/json" },
      body: JSON.stringify({ userIds: this.targetUsers, message: this.message }),
    });
    if (!r.ok) throw new Error(await r.text());
    return await r.text();
  }
}
