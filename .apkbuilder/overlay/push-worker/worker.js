/**
 * Duties push Worker (Cloudflare Workers, free plan, no credit card).
 *
 * The app calls  POST /notify  after a message reached the Realtime Database:
 *   header  Authorization: Bearer <Firebase ID token of the sender>
 *   body    { "channelId": "...", "messageId": "...", "channelName": "..." }
 *
 * The Worker:
 *   1. checks the Firebase login token (really signed by Google, for this project)
 *   2. reads the message from the database and checks it is the sender's own and is fresh (never trusts text from the app)
 *   3. reads the members of the chat and their FCM tokens
 *   4. sends one FCM (HTTP v1) data message to each phone (not to the sender)
 *   5. removes the tokens that Google says are dead
 *
 * Settings (Worker > Settings > Variables and Secrets):
 *   FIREBASE_PROJECT_ID  (text)    e.g. duties-a11bf
 *   DB_URL               (text)    e.g. https://duties-a11bf-default-rtdb.firebaseio.com
 *   SERVICE_ACCOUNT      (SECRET)  the whole JSON file of the Firebase service account
 */

const MAX_DEVICES = 40;          // free plan: max 50 outgoing requests per call
const MAX_MESSAGE_AGE_MS = 120000; // a message older than this can not be pushed (stops replays)
const ID_RE = /^[A-Za-z0-9_-]{1,128}$/;

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/notify") {
      return new Response("Duties push Worker is running", { status: 200 });
    }
    try {
      const result = await handleNotify(request, env, ctx);
      return json(result, 200);
    } catch (e) {
      if (e instanceof HttpError) return json({ error: e.message }, e.status);
      console.log("worker error", e && e.stack ? e.stack : String(e));
      return json({ error: "internal" }, 500);
    }
  },
};

async function handleNotify(request, env, ctx) {
  if (!env.FIREBASE_PROJECT_ID || !env.DB_URL || !env.SERVICE_ACCOUNT) {
    throw new HttpError(500, "worker is not configured");
  }

  // 1) who is calling
  const auth = request.headers.get("Authorization") || "";
  const idToken = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  const senderUid = await verifyIdToken(idToken, env.FIREBASE_PROJECT_ID);

  let body;
  try {
    body = await request.json();
  } catch {
    throw new HttpError(400, "bad json");
  }
  const channelId = String(body.channelId || "");
  const messageId = String(body.messageId || "");
  const channelName = String(body.channelName || "").slice(0, 60);
  if (!ID_RE.test(channelId) || !ID_RE.test(messageId)) throw new HttpError(400, "bad ids");

  const sa = JSON.parse(env.SERVICE_ACCOUNT);
  const accessToken = await getAccessToken(sa);
  const db = env.DB_URL.replace(/\/+$/, "");
  const dbGet = async (path) => {
    const r = await fetch(`${db}/${path}.json?access_token=${accessToken}`);
    if (!r.ok) throw new HttpError(502, "database " + r.status);
    return r.json();
  };

  // 2) the message must exist, be the caller's own, and be fresh
  const msg = await dbGet(`chats/${channelId}/messages/${messageId}`);
  if (!msg || msg.uid !== senderUid) throw new HttpError(403, "not your message");
  const createdAt = Number(msg.createdAt) || 0;
  if (Date.now() - createdAt > MAX_MESSAGE_AGE_MS) throw new HttpError(409, "message too old");

  // 3) members (without the sender) and their phones
  const members = (await dbGet(`chats/${channelId}/members`)) || {};
  if (!members[senderUid]) throw new HttpError(403, "not a member");
  const targets = Object.keys(members).filter((u) => members[u] === true && u !== senderUid);
  if (targets.length === 0) return { sent: 0 };

  const allTokens = (await dbGet("fcmTokens")) || {};
  const devices = [];
  for (const uid of targets) {
    const tokens = allTokens[uid];
    if (!tokens) continue;
    for (const token of Object.keys(tokens)) devices.push({ uid, token });
  }
  const list = devices.slice(0, MAX_DEVICES);

  // 4) send
  const data = {
    type: "chat",
    channelId,
    channelName,
    messageId,
    senderUid,
    senderName: String(msg.name || "").slice(0, 40),
    text: String(msg.text || "").slice(0, 300),
    ts: String(createdAt || Date.now()),
  };
  const sendUrl = `https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`;
  const results = await Promise.all(
    list.map(async ({ uid, token }) => {
      try {
        const r = await fetch(sendUrl, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token,
              data,
              android: { priority: "HIGH", ttl: "3600s" },
            },
          }),
        });
        if (r.ok) return { ok: true };
        const err = await r.text();
        const dead = r.status === 404 || /UNREGISTERED|NOT_FOUND|INVALID_ARGUMENT/.test(err);
        return { ok: false, dead, uid, token };
      } catch {
        return { ok: false, dead: false };
      }
    })
  );

  // 5) clean the dead tokens
  const dead = results.filter((x) => x.dead);
  if (dead.length) {
    ctx.waitUntil(
      Promise.all(
        dead.slice(0, 8).map((d) =>
          fetch(`${db}/fcmTokens/${d.uid}/${encodeURIComponent(d.token)}.json?access_token=${accessToken}`, {
            method: "DELETE",
          }).catch(() => {})
        )
      )
    );
  }
  return { sent: results.filter((x) => x.ok).length, devices: list.length };
}

/* ------------------------------------------------------------------ Firebase login token check */

let jwksCache = { keys: null, exp: 0 };

async function getJwks() {
  if (jwksCache.keys && Date.now() < jwksCache.exp) return jwksCache.keys;
  const r = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
  if (!r.ok) throw new HttpError(502, "could not load Google keys");
  const j = await r.json();
  jwksCache = { keys: j.keys, exp: Date.now() + 60 * 60 * 1000 };
  return j.keys;
}

async function verifyIdToken(token, projectId) {
  const parts = token.split(".");
  if (parts.length !== 3) throw new HttpError(401, "bad token");
  let header, payload;
  try {
    header = JSON.parse(new TextDecoder().decode(b64urlToBytes(parts[0])));
    payload = JSON.parse(new TextDecoder().decode(b64urlToBytes(parts[1])));
  } catch {
    throw new HttpError(401, "bad token");
  }
  if (header.alg !== "RS256" || !header.kid) throw new HttpError(401, "bad token");

  const jwk = (await getJwks()).find((k) => k.kid === header.kid);
  if (!jwk) throw new HttpError(401, "unknown key");
  const key = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"]
  );
  const valid = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    key,
    b64urlToBytes(parts[2]),
    new TextEncoder().encode(parts[0] + "." + parts[1])
  );
  if (!valid) throw new HttpError(401, "bad signature");

  const now = Date.now() / 1000;
  if (
    payload.aud !== projectId ||
    payload.iss !== "https://securetoken.google.com/" + projectId ||
    !payload.sub ||
    payload.exp < now ||
    payload.iat > now + 60
  ) {
    throw new HttpError(401, "token not valid for this project");
  }
  return payload.sub;
}

/* ------------------------------------------------------------------ Google access token (service account) */

let tokenCache = { token: null, exp: 0 };

async function getAccessToken(sa) {
  if (tokenCache.token && Date.now() < tokenCache.exp - 60000) return tokenCache.token;
  const now = Math.floor(Date.now() / 1000);
  const enc = new TextEncoder();
  const unsigned =
    bytesToB64url(enc.encode(JSON.stringify({ alg: "RS256", typ: "JWT" }))) +
    "." +
    bytesToB64url(
      enc.encode(
        JSON.stringify({
          iss: sa.client_email,
          scope:
            "https://www.googleapis.com/auth/firebase.messaging https://www.googleapis.com/auth/firebase.database https://www.googleapis.com/auth/userinfo.email",
          aud: "https://oauth2.googleapis.com/token",
          iat: now,
          exp: now + 3600,
        })
      )
    );
  const pem = sa.private_key.replace(/-----[A-Z ]+-----/g, "").replace(/\s+/g, "");
  const key = await crypto.subtle.importKey(
    "pkcs8",
    b64ToBytes(pem),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, enc.encode(unsigned));
  const assertion = unsigned + "." + bytesToB64url(new Uint8Array(sig));

  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!r.ok) throw new HttpError(502, "google token " + r.status);
  const j = await r.json();
  tokenCache = { token: j.access_token, exp: Date.now() + (j.expires_in || 3600) * 1000 };
  return tokenCache.token;
}

/* ------------------------------------------------------------------ helpers */

function json(obj, status) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function b64ToBytes(b64) {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function b64urlToBytes(s) {
  s = s.replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4) s += "=";
  return b64ToBytes(s);
}

function bytesToB64url(bytes) {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
