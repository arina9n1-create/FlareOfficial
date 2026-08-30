import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { S3Client, GetObjectCommand } from "https://esm.sh/@aws-sdk/client-s3@3"
import { getSignedUrl } from "https://esm.sh/@aws-sdk/s3-request-presigner@3"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  // 1. Handle CORS preflight
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    // 2. Validate Authentication: only signed-in app users may resolve media.
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) {
      throw new Error('No authorization header')
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? ''
    // Older projects inject SUPABASE_ANON_KEY; newer dashboards label it deprecated
    // and expose the same value as SUPABASE_PUBLISHABLE_KEY. Accept either.
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY')
        ?? Deno.env.get('SUPABASE_PUBLISHABLE_KEY') ?? ''

    const supabaseClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
    })

    const { data: { user }, error: authError } = await supabaseClient.auth.getUser()
    if (authError || !user) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 401,
      })
    }

    // 3. Resolve the requested object path. Only objects inside `users/<uid>/...`
    // can be served and traversal tricks are rejected.
    const requestedPath = new URL(req.url).searchParams.get('path') ?? ''
    const normalizedPath = decodeURIComponent(requestedPath).replace(/\\/g, '/')
    if (
      !normalizedPath.startsWith('users/') ||
      normalizedPath.includes('..') ||
      !/^users\/[\w-]+\/(profile|cover|post|reel|story|media)\/[\w.\-]+$/.test(normalizedPath)
    ) {
      throw new Error('Invalid media path')
    }

    // 4. Configure B2 S3 Client (same secrets as b2-upload).
    const b2KeyId = requiredSecret('B2_KEY_ID')
    const b2AppKey = requiredSecret('B2_APPLICATION_KEY')
    const b2Endpoint = requiredSecret('B2_ENDPOINT').replace(/^https?:\/\//, '').replace(/\/$/, '')
    const b2Region = requiredSecret('B2_REGION')
    const b2Bucket = requiredSecret('B2_BUCKET')

    const s3Client = new S3Client({
      region: b2Region,
      endpoint: `https://${b2Endpoint}`,
      credentials: {
        accessKeyId: b2KeyId,
        secretAccessKey: b2AppKey,
      },
    })

    // 5. Mint a short-lived pre-signed download URL and 302-redirect the caller.
    // The bucket stays fully PRIVATE; the pre-signed URL outlives this response by
    // only a few minutes and leaks no B2 credentials.
    const command = new GetObjectCommand({
      Bucket: b2Bucket,
      Key: normalizedPath,
    })
    const presignedUrl = await getSignedUrl(s3Client, command, { expiresIn: 1800 })

    return new Response(null, {
      status: 302,
      headers: {
        ...corsHeaders,
        Location: presignedUrl,
        'Cache-Control': 'no-store',
      },
    })
  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 400,
      }
    )
  }
})

function requiredSecret(name: string): string {
  const value = Deno.env.get(name)?.trim()
  if (!value) throw new Error(`Missing Edge Function secret: ${name}`)
  return value
}
