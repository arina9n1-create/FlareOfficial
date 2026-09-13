import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { RtcTokenBuilder, RtcRole } from "https://esm.sh/agora-access-token@2.0.1"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

/**
 * generate-agora-token
 *
 * Issues an Agora RTC access token for a given channel + uid so the mobile app
 * can join a voice/video call WITHOUT ever seeing AGORA_APP_CERTIFICATE.
 *
 * The certificate lives only in the Supabase Edge Function secrets
 * (AGORA_APP_ID / AGORA_APP_CERTIFICATE); it is never shipped to clients.
 *
 * POST /functions/v1/generate-agora-token
 *   Authorization: Bearer <supabase access token>
 *   { "channelName": "<1..64 chars>", "uid": <uint32>, "expireInSeconds": 3600 }
 *
 * Response 200:
 *   { "token": "<rtc-token>", "appId": "<ANONYMOUS app id>", "channelName": "...", "uid": 123, "expiresAt": <unix-ts> }
 */
serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })

  try {
    // 1. Auth — every request must carry the user's Supabase session.
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) throw new Error('No authorization header')

    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? ''
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY') ?? Deno.env.get('SUPABASE_PUBLISHABLE_KEY') ?? ''
    const supabaseClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
    })

    const { data: { user }, error: authError } = await supabaseClient.auth.getUser()
    if (authError || !user) throw new Error('Unauthorized')

    // 2. Parse & validate input.
    let body: Record<string, unknown> = {}
    try { body = await req.json() } catch (_) { /* body-less calls are rejected below */ }

    const channelName = String(body.channelName ?? '').trim().slice(0, 64)
    if (!channelName) throw new Error('Missing channelName')

    // 0 means "Agora auto-assigns the uid" (App ID with variable-uid mode).
    const uid = Number(body.uid ?? 0)
    if (!Number.isInteger(uid) || uid < 0 || uid > 0xffffffff) {
      throw new Error('Invalid uid (expect uint32)')
    }

    const expireInSeconds = Math.max(300, Math.min(86400, Number(body.expireInSeconds ?? 3600)))

    // 3. Agora credentials live ONLY in Edge Function secrets.
    const appId = Deno.env.get('AGORA_APP_ID') ?? ''
    const appCertificate = Deno.env.get('AGORA_APP_CERTIFICATE') ?? ''
    if (!appId || !appCertificate) {
      console.error('AGORA_APP_ID or AGORA_APP_CERTIFICATE is not configured in function secrets')
      throw new Error('Agora is not configured on the server')
    }

    const expireTimestamp = Math.floor(Date.now() / 1000) + expireInSeconds
    // PUBLISHER role: the joiner may publish and subscribe (normal for 1:1 calls).
    const token = RtcTokenBuilder.buildTokenWithUid(
      appId, appCertificate, channelName, uid, RtcRole.PUBLISHER, expireTimestamp,
    )

    return new Response(
      JSON.stringify({
        token,
        appId,
        channelName,
        uid,
        expireInSeconds,
        expiresAt: expireTimestamp,
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 200 },
    )
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})