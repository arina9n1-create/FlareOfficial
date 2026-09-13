import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { S3Client, GetObjectCommand } from "https://esm.sh/@aws-sdk/client-s3@3"
import { getSignedUrl } from "https://esm.sh/@aws-sdk/s3-request-presigner@3"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })

  try {
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) throw new Error('No authorization header')

    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? ''
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY') ?? Deno.env.get('SUPABASE_PUBLISHABLE_KEY') ?? ''
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

    const requestedPath = new URL(req.url).searchParams.get('path') ?? ''
    const normalizedPath = decodeURIComponent(requestedPath).replace(/\\/g, '/')

    // Basic path safety check
    if (normalizedPath.includes('..')) throw new Error('Invalid media path')

    const r2Client = new S3Client({
      region: Deno.env.get('R2_REGION') ?? 'auto',
      endpoint: Deno.env.get('R2_ENDPOINT'),
      credentials: {
        accessKeyId: Deno.env.get('R2_ACCESS_KEY_ID') ?? '',
        secretAccessKey: Deno.env.get('R2_SECRET_ACCESS_KEY') ?? '',
      },
    })

    const command = new GetObjectCommand({
      Bucket: Deno.env.get('R2_BUCKET_NAME'),
      Key: normalizedPath,
    })

    const presignedUrl = await getSignedUrl(r2Client, command, { expiresIn: 1800 })

    return new Response(null, {
      status: 302,
      headers: {
        ...corsHeaders,
        Location: presignedUrl,
        'Cache-Control': 'no-store',
      },
    })
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
