import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

const FCM_SCOPE = 'https://www.googleapis.com/auth/firebase.messaging'
const TOKEN_URL = 'https://oauth2.googleapis.com/token'
const FCM_SEND_URL = (projectId: string) =>
  `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`

// ---------------------------------------------------------------------------
// Service account parsing — FIREBASE_SERVICE_ACCOUNT_JSON lives ONLY in Edge
// Function secrets. It is never returned to clients and never logged.
// ---------------------------------------------------------------------------

interface ServiceAccount {
  project_id: string
  client_email: string
  private_key: string
}

function parseServiceAccount(raw: string): ServiceAccount {
  let json: Record<string, unknown>
  try {
    json = JSON.parse(raw)
  } catch (_) {
    throw new Error('FIREBASE_SERVICE_ACCOUNT_JSON is not valid JSON')
  }

  const projectId = String(json.project_id ?? '')
  const clientEmail = String(json.client_email ?? '')
  const privateKey = String(json.private_key ?? '')

  if (!projectId || !clientEmail || !privateKey) {
    throw new Error('Service account JSON is missing project_id, client_email or private_key')
  }
  if (!privateKey.includes('-----BEGIN PRIVATE KEY-----')) {
    throw new Error('Service account private_key must be PKCS#8 PEM format')
  }
  return { project_id: projectId, client_email: clientEmail, private_key: privateKey }
}

// ---------------------------------------------------------------------------
// base64url helpers + RS256 JWT signing via Web Crypto (no external deps)
// ---------------------------------------------------------------------------

function base64UrlEncode(bytes: Uint8Array): string {
  let bin = ''
  for (const b of bytes) bin += String.fromCharCode(b)
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

function base64UrlEncodeString(s: string): string {
  return base64UrlEncode(new TextEncoder().encode(s))
}

function base64UrlDecodeToBytes(s: string): Uint8Array {
  const b64 = s.replace(/-/g, '+').replace(/_/g, '/')
  const bin = atob(b64 + '='.repeat((4 - (b64.length % 4)) % 4))
  const bytes = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
  return bytes
}

/** Strips the PEM header/footer and all whitespace from a PKCS#8 private key. */
function pemToDerBase64(pem: string): string {
  return pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, '')
    .replace(/-----END PRIVATE KEY-----/g, '')
    .replace(/\s+/g, '')
}

/**
 * Signs a Google OAuth2 JWT assertion (RS256) with the service account key and
 * exchanges it for a short-lived access token that can call the FCM HTTP v1 API.
 * The private key is used only inside this function and is never logged.
 */
async function getGoogleAccessToken(sa: ServiceAccount): Promise<{ token: string; expiresAt: number }> {
  const now = Math.floor(Date.now() / 1000)
  const header = base64UrlEncodeString(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const payload = base64UrlEncodeString(JSON.stringify({
    iss: sa.client_email,
    scope: FCM_SCOPE,
    aud: TOKEN_URL,
    iat: now,
    exp: now + 3600,
  }))
  const unsignedJwt = `${header}.${payload}`

  const cryptoKey = await crypto.subtle.importKey(
    'pkcs8',
    base64UrlDecodeToBytes(pemToDerBase64(sa.private_key)),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  )
  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    cryptoKey,
    new TextEncoder().encode(unsignedJwt),
  )
  const jwt = `${unsignedJwt}.${base64UrlEncode(new Uint8Array(signature))}`

  const tokenResponse = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: jwt,
    }),
  })

  if (!tokenResponse.ok) {
    // Log only the status — never the assertion/JWT or response body secrets.
    console.error(`Google OAuth2 token exchange failed: HTTP ${tokenResponse.status}`)
    throw new Error(`Google OAuth2 token exchange failed (HTTP ${tokenResponse.status})`)
  }

  const tokenJson = await tokenResponse.json()
  const token = String(tokenJson.access_token ?? '')
  const expiresInSeconds = Number(tokenJson.expires_in ?? 3600)
  if (!token) throw new Error('Google OAuth2 response did not contain an access token')
  return { token, expiresAt: now + expiresInSeconds }
}

// ---------------------------------------------------------------------------
// FCM HTTP v1 send — token cache for the OAuth2 access token (per isolate)
// ---------------------------------------------------------------------------

let cachedAccessToken: { token: string; expiresAt: number } | null = null

async function getAccessToken(sa: ServiceAccount): Promise<string> {
  // Refresh 60s before expiry to avoid clock-skew failures mid-request.
  if (cachedAccessToken && cachedAccessToken.expiresAt > Math.floor(Date.now() / 1000) + 60) {
    return cachedAccessToken.token
  }
  cachedAccessToken = await getGoogleAccessToken(sa)
  console.log(`FCM access token issued, expires in ~${cachedAccessToken.expiresAt - Math.floor(Date.now() / 1000)}s`)
  return cachedAccessToken.token
}

interface PushPayload {
  title?: string
  body?: string
  /** Arbitrary key/value string pairs delivered to the app's onMessageReceived. */
  data?: Record<string, string>
  /** Android notification channel id, e.g. flareofficial_channel_incoming_calls. */
  channelId?: string
  priority?: 'HIGH' | 'NORMAL'
  collapseKey?: string
}

/**
 * Sends one message to a single FCM registration token via HTTP v1.
 * Returns the FCM message name on success, or throws with a safe description.
 */
async function sendFcmMessage(
  sa: ServiceAccount,
  registrationToken: string,
  payload: PushPayload,
): Promise<string> {
  const accessToken = await getAccessToken(sa)

  const message: Record<string, unknown> = {
    token: registrationToken,
    android: {
      priority: payload.priority ?? 'HIGH',
      // High priority messages should be delivered immediately.
      // TTL '0s' is for messages that should not be stored if the device is offline.
      // For calls, it's appropriate. For chat, maybe a longer TTL is better.
      ...(payload.priority === 'HIGH' && !payload.body ? { ttl: '0s' } : {}),
      ...(payload.collapseKey ? { collapse_key: payload.collapseKey } : {}),
      ...(payload.channelId ? { notification: { channel_id: payload.channelId } } : {}),
    },
  }
  if (payload.title || payload.body) {
    message.notification = {
      ...(payload.title ? { title: payload.title } : {}),
      ...(payload.body ? { body: payload.body } : {}),
    }
  }
  if (payload.data && Object.keys(payload.data).length > 0) {
    // FCM data values must be strings.
    message.data = Object.fromEntries(
      Object.entries(payload.data).map(([k, v]) => [k, String(v)]),
    )
  }

  const response = await fetch(FCM_SEND_URL(sa.project_id), {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message }),
  })

  if (!response.ok) {
    const errBody = await response.text()
    // Log status + a trimmed FCM error code only — never tokens or payloads.
    console.error(`FCM send failed: HTTP ${response.status}`)
    throw new FcmSendError(response.status, summarizeFcmError(response.status, errBody))
  }
  const result = await response.json()
  return String(result.name ?? 'sent')
}

export class FcmSendError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

const TOKEN_UNREGISTERED_STATUSES = new Set([404, 410])

/** Extracts a short, secret-free description from an FCM error response. */
function summarizeFcmError(status: number, body: string): string {
  try {
    const parsed = JSON.parse(body)
    const detail = String(parsed?.error?.details?.[0]?.errorCode ?? parsed?.error?.status ?? '')
    return detail ? `FCM error (HTTP ${status}): ${detail}` : `FCM error (HTTP ${status})`
  } catch (_) {
    return `FCM error (HTTP ${status})`
  }
}

// ---------------------------------------------------------------------------
// Request handler — SERVER-SIDE AUTHORIZATION
// ---------------------------------------------------------------------------
// POST /functions/v1/send-push-notification
//   Authorization: Bearer <supabase access token>
//   { "userId": "<uuid>", "body": "<message preview>", "data": { "type": "CALL"|"CHAT"|"FOLLOW"|"ADMIN", ... } }
//
// AUTHORIZATION RULES (strict; no client-trusted targets or payloads):
//   CALL   — client must pass channelName of a call_history row whose caller_handle
//            equals the authenticated caller and receiver_handle equals the
//            target user, with status RINGING/IN_PROGRESS. Payload (channel,
//            call_type, title/body) is derived from the DB row; client-supplied
//            agora_token is never forwarded (callee mints its own RTC token).
//   CHAT   — allowed only if caller and recipient mutually follow each other
//            OR share an existing chat_messages conversation.
//   FOLLOW — allowed only if the caller actually follows the recipient.
//   ADMIN  — caller must have app_users.role = 'SUPER_ADMIN'; only this type
//            may set its own title/body.
//   Any other type, a direct FCM token, or self-notification → rejected.
// Targets are always resolved server-side from device_tokens by userId.

const maskToken = (t: string) => `${t.slice(0, 6)}…${t.slice(-4)}`
const ciEq = (a: string, b: string) => a.toLowerCase() === b.toLowerCase()

/** Best-effort per-user rate limit (per isolate): max 20 notifications / 60s. */
const rateBuckets = new Map<string, number[]>()
function rateLimited(userId: string): boolean {
  const now = Date.now()
  const hits = (rateBuckets.get(userId) ?? []).filter((t) => now - t < 60_000)
  if (hits.length >= 20) return true
  hits.push(now)
  rateBuckets.set(userId, hits)
  return false
}

serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })

  try {
    // 1. Auth — validate the caller's Supabase session.
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) throw new Error('No authorization header')

    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? ''
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY') ?? Deno.env.get('SUPABASE_PUBLISHABLE_KEY') ?? ''
    const anonClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
    })

    const { data: { user }, error: authError } = await anonClient.auth.getUser()
    if (authError || !user) {
      console.error(`AUTH_FAILED: ${authError?.message ?? 'no user for the provided JWT'} (check the sender app's session token)`)
      throw new Error('Unauthorized')
    }
    if (rateLimited(user.id)) throw new Error('Rate limit exceeded; try again shortly')

    // 2. Service-role client for server-side authorization lookups + token
    //    routing. RLS blocks cross-user reads (follows, device_tokens, other
    //    users' call rows); every lookup here is parameterized by the VERIFIED
    //    caller id (from the validated JWT) and the explicit target userId.
    const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    if (!serviceRoleKey) throw new Error('Server is not configured (missing SUPABASE_SERVICE_ROLE_KEY)')
    const db = createClient(supabaseUrl, serviceRoleKey, { auth: { persistSession: false } })

    // 3. Firebase credentials — ONLY from Edge Function secrets, never clients.
    const serviceAccountJson = Deno.env.get('FIREBASE_SERVICE_ACCOUNT_JSON') ?? ''
    if (!serviceAccountJson) {
      console.error('FIREBASE_SERVICE_ACCOUNT_JSON secret is not configured')
      throw new Error('FCM is not configured on the server')
    }
    const sa = parseServiceAccount(serviceAccountJson)

    // 3. Parse & validate input (no client-trusted targets or payloads).
    let body: Record<string, unknown> = {}
    try { body = await req.json() } catch (_) { /* body-less calls are rejected below */ }

    const rawType = String(body.type ?? '').trim().toUpperCase()
    console.log(`REQ_RECEIVED type=${rawType || '(none)'}`)
    if (!['CALL', 'CHAT', 'FOLLOW', 'ADMIN'].includes(rawType)) {
      throw new Error('Invalid "type"; must be one of CALL, CHAT, FOLLOW, ADMIN')
    }
    const targetUserId = String(body.userId ?? '').trim()
    if (!targetUserId) throw new Error('"userId" is required (direct FCM tokens are not accepted)')

    // 4. Resolve the caller's and recipient's app identity (handles).
    const { data: callerRow, error: callerErr } = await db
      .from('app_users').select('name, handle, role').eq('uid', user.id).maybeSingle()
    if (callerErr) throw new Error('Failed to verify caller identity')
    if (!callerRow?.handle) throw new Error('Caller has no FlareOfficial profile')
    const callerHandle: string = callerRow.handle
    const callerName: string = callerRow.name || callerHandle

    const { data: targetRow, error: targetErr } = await db
      .from('app_users').select('handle').eq('uid', targetUserId).maybeSingle()
    if (targetErr) {
      // Do not swallow the Postgres error — it made recipient resolution
      // failures invisible (e.g. selecting a column that does not exist).
      console.error(`RESOLVE_RECIPIENT_DB_ERROR: ${targetErr.message}`)
      throw new Error('Failed to resolve recipient')
    }
    if (!targetRow?.handle) throw new Error('Recipient has no FlareOfficial profile')
    const recipientHandle: string = targetRow.handle

    if (ciEq(callerHandle, recipientHandle)) throw new Error('Self-notification is not allowed')

    // 5. Build the payload STRICTLY server-side per notification type.
    let payload: PushPayload
    // Set for CALL notifications; used by the missed-call safety net below.
    let fallbackChannelName: string | null = null

    if (rawType === 'CALL') {
      // Only the legitimate caller of a real, active call may trigger the ring.
      // The call is located by its unique Agora channel (the same value the app
      // stores in call_history.channel_name), NOT by a call_id column — the
      // production call_history schema has no call_id column.
      const channelName = String(body.channelName ?? body.agoraChannel ?? '').trim()
      const callId = String(body.callId ?? '').trim()
      if (!channelName) throw new Error('"channelName" is required for CALL notifications')
      const { data: call, error: callErr } = await db
        .from('call_history')
        .select('caller_handle, receiver_handle, call_type, channel_name, status')
        .eq('channel_name', channelName)
        .maybeSingle()
      if (callErr || !call) throw new Error('Call not found')
      if (!ciEq(call.caller_handle, callerHandle)) throw new Error('Not authorized for this call')
      if (!ciEq(call.receiver_handle, recipientHandle)) throw new Error('Recipient is not the callee of this call')
      if (!['RINGING', 'IN_PROGRESS'].includes(call.status)) throw new Error('Call is not active')

        // The missed-call safety net locates this call by its unique channel.
        fallbackChannelName = call.channel_name

      // DATA payload with standardized keys for MainActivity navigation.
      payload = {
        data: {
          type: 'call',
          extra_target_screen: 'screen_incoming_call',
          extra_call_id: callId,
          extra_caller_handle: call.caller_handle,
          extra_caller_name: callerName,
          extra_call_type: call.call_type,
          extra_agora_channel: call.channel_name,
          // Legacy keys for backward compatibility
          call_id: callId,
          caller_name: callerName,
          call_type: call.call_type,
          agora_channel: call.channel_name,
        },
        // We add a notification block even for calls.
        // 1. On standard devices, it ensures a system notification if onMessageReceived takes too long.
        // 2. On Vivo/Oppo, it ensures the user SEES something even if the process wake-up is blocked.
        title: `Incoming ${call.call_type} Call`,
        body: `${callerName} is calling you`,
        // RINGING channel — Play Services displays this while the callee's
        // process is dead, so it must carry the ringtone-bearing channel
        // ('calls' itself is silent; CallRingingService plays its own sound
        // only when the process is alive).
        channelId: 'calls_push',
        priority: 'HIGH',
      }
    } else if (rawType === 'CHAT') {
      // Sender must be authorized to message the recipient: caller follows the
      // recipient OR an existing conversation exists between the two handles.
      const { data: mutual, error: mErr } = await db
        .from('follows')
        .select('follower_uid')
        .eq('follower_uid', user.id).eq('following_uid', targetUserId).eq('is_following', true)
        .maybeSingle()
      if (mErr) throw new Error('Failed to verify relationship')
      let authorized = !!mutual
      if (!authorized) {
        const { data: convo } = await db
          .from('chat_messages')
          .select('id')
          .or(`and(sender_handle.ilike.${callerHandle},receiver_handle.ilike.${recipientHandle}),and(sender_handle.ilike.${recipientHandle},receiver_handle.ilike.${callerHandle})`)
          .limit(1)
          .maybeSingle()
        authorized = !!convo
      }
      if (!authorized) throw new Error('Not authorized to message this user')

      payload = {
        data: {
          type: 'chat',
          extra_target_screen: 'screen_chat',
          extra_chat_handle: callerHandle,
          extra_chat_name: callerName,
          body: String(body.body ?? '').slice(0, 1024),
          // Legacy keys
          sender_handle: callerHandle,
          sender_name: callerName,
        },
        title: callerName,
        body: String(body.body ?? 'New message').slice(0, 1024),
        channelId: 'messages',
        priority: 'HIGH',
      }
    } else if (rawType === 'FOLLOW') {
      // Only real follows may generate a follow notification.
      const { data: rel, error: rErr } = await db
        .from('follows')
        .select('follower_uid')
        .eq('follower_uid', user.id).eq('following_uid', targetUserId).eq('is_following', true)
        .maybeSingle()
      if (rErr) throw new Error('Failed to verify follow relationship')
      if (!rel) throw new Error('Not authorized: caller does not follow this user')

      payload = {
        data: {
          type: 'follow',
          extra_target_screen: 'screen_notifications',
          actor_handle: callerHandle
        },
        title: 'New follower',
        body: `${callerName} started following you 🤝`,
        channelId: 'messages',
        priority: 'HIGH',
      }
    } else {
      // ADMIN broadcast — caller must hold SUPER_ADMIN role in app_users.
      if (callerRow.role !== 'SUPER_ADMIN') throw new Error('Admin privileges required')
      payload = {
        data: {
          type: 'admin',
          extra_target_screen: 'screen_admin'
        },
        title: String(body.title ?? 'FlareOfficial announcement').slice(0, 512),
        body: String(body.body ?? '').slice(0, 1024),
        channelId: 'messages',
        priority: 'HIGH',
      }
    }

    // 6. Resolve recipient registration tokens SERVER-SIDE from device_tokens
    //    by user_id (the live table's column is `user_id`, not `uid`).
    //    Client-supplied FCM tokens are never used.
    const { data: rows, error: dbError } = await db
      .from('device_tokens')
      .select('id, token')
      .eq('user_id', targetUserId)
    if (dbError) throw new Error(`Failed to load device tokens: ${dbError.message}`)
    const targets = (rows ?? [])
      .map((r) => ({ token: String(r.token ?? ''), deviceId: String(r.id ?? '') }))
      .filter((t) => t.token)
    if (targets.length === 0) {
      console.log(`DEVICE_TOKEN_NOT_FOUND user_id=${targetUserId}`)
      return new Response(
        JSON.stringify({ success: true, results: [], invalidTokens: [], message: 'No registered devices for this user' }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 200 },
      )
    }
    console.log(`DEVICE_TOKEN_FOUND user_id=${targetUserId} devices=${targets.length}`)

    // 7. Send to every target; clean up tokens FCM reports as unregistered.
    const results: Array<Record<string, unknown>> = []
    const invalidTokens: Array<Record<string, unknown>> = []

    for (const target of targets) {
      try {
        console.log(`FCM_SEND_STARTED device=${target.deviceId || maskToken(target.token)}`)
        const messageName = await sendFcmMessage(sa, target.token, payload)
        console.log(`FCM_SEND_SUCCESS device=${target.deviceId || maskToken(target.token)} name=${messageName}`)
        results.push({ deviceId: target.deviceId, ok: true, messageName })
      } catch (sendErr) {
        const status = sendErr instanceof FcmSendError ? sendErr.status : 500
        const safeMessage = sendErr instanceof Error ? sendErr.message : 'Unknown send error'
        console.error(`FCM_SEND_FAILED device=${target.deviceId || 'unknown'}: ${safeMessage}`)
        if (TOKEN_UNREGISTERED_STATUSES.has(status)) {
          invalidTokens.push({ deviceId: target.deviceId, reason: safeMessage })
          if (target.deviceId) {
            await db.from('device_tokens').delete().eq('id', target.deviceId)
          }
        } else {
          results.push({ deviceId: target.deviceId, ok: false, error: safeMessage })
        }
      }
    }

    const delivered = results.filter((r) => r.ok).length

    // 8. CALL safety net: the data-only ring message above is required for
    //    the full-screen ringing UX (notification messages never reach
    //    onMessageReceived in background), but if the callee's process was
    //    frozen by an aggressive OEM battery manager, NO UI was produced at
    //    all. When the call is STILL RINGING ~20s later, send a
    //    notification-only "Missed call" message — Google Play Services
    //    displays those WITHOUT waking the app process, so the user at
    //    least learns about the call (same degraded path big apps get on
    //    Vivo/MIUI). Runs in the background via EdgeRuntime.waitUntil so the
    //    caller's HTTP response is not delayed; silently skipped if the
    //    runtime lacks waitUntil.
    if (rawType === 'CALL' && delivered > 0 && fallbackChannelName) {
      const missedCallTask = (async () => {
        try {
          await new Promise((resolve) => setTimeout(resolve, 20_000))
          const { data: callNow } = await db
            .from('call_history')
            .select('status')
            .eq('channel_name', fallbackChannelName)
            .maybeSingle()
          if (callNow?.status !== 'RINGING') {
            console.log(`MISSED_CALL_FALLBACK_SKIPPED status=${callNow?.status ?? 'unknown'}`)
            return
          }
          const missedPayload: PushPayload = {
            title: 'Missed call',
            body: `${callerName} called you`,
            channelId: 'messages',
            priority: 'HIGH',
            data: {
              type: 'missed_call',
              call_id: String(body.callId ?? ''),
              caller_name: callerName,
            },
          }
          for (const target of targets) {
            try {
              await sendFcmMessage(sa, target.token, missedPayload)
            } catch (_) {
              // Best-effort only — the ring already happened.
            }
          }
          console.log(`MISSED_CALL_FALLBACK_SENT channel=${fallbackChannelName}`)
        } catch (fallbackErr) {
          console.error(
            `MISSED_CALL_FALLBACK_FAILED: ${fallbackErr instanceof Error ? fallbackErr.message : 'unknown'}`,
          )
        }
      })()
      const waitUntil = (globalThis as {
        EdgeRuntime?: { waitUntil?: (task: Promise<unknown>) => void }
      }).EdgeRuntime?.waitUntil
      if (typeof waitUntil === 'function') {
        waitUntil(missedCallTask)
      } else {
        // Runtime without waitUntil: fire-and-forget; worst case the
        // isolate is reaped before the timer fires and no fallback is sent.
        void missedCallTask
      }
    }

    return new Response(
      JSON.stringify({ success: delivered > 0, results, invalidTokens }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 200 },
    )
  } catch (error) {
    // Every rejection MUST be visible in the dashboard logs — silent 400s made
    // push failures undiagnosable before.
    console.error(`REQUEST_REJECTED: ${error instanceof Error ? error.message : String(error)}`)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})