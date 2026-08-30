import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { S3Client, PutObjectCommand } from "https://esm.sh/@aws-sdk/client-s3@3"

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
    // 2. Validate Authentication
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
      throw new Error('Unauthorized')
    }

    // 3. Parse Metadata & File
    const formData = await req.formData()
    const file = formData.get('file') as File
    const type = formData.get('type') as string // profile, cover, post, reel, story

    if (!file || !type) {
      throw new Error('Missing file or type')
    }

    // Basic validation. Keep the limit below the Edge Function request limit.
    const allowedTypes = ['profile', 'cover', 'post', 'reel', 'story', 'media']
    if (!allowedTypes.includes(type)) {
      throw new Error('Invalid upload type')
    }
    if (file.size <= 0 || file.size > 50 * 1024 * 1024) {
      throw new Error('File must be between 1 byte and 50 MB')
    }

    // 4. Configure B2 S3 Client
    // These are stored in Supabase Edge Function Secrets
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

    // 5. Generate Deterministic Object Path
    // users/{uid}/{type}/{timestamp}_{filename}
    const timestamp = Date.now()
    const sanitizedFilename = file.name.replace(/[^a-z0-9.]/gi, '_').toLowerCase()
    const objectPath = `users/${user.id}/${type}/${timestamp}_${sanitizedFilename}`

    // 6. Upload to B2
    const arrayBuffer = await file.arrayBuffer()
    const uint8Array = new Uint8Array(arrayBuffer)

    await s3Client.send(new PutObjectCommand({
      Bucket: b2Bucket,
      Key: objectPath,
      Body: uint8Array,
      ContentType: file.type,
    }))

    // 7. Return storage references.
    // - `url`: STABLE gateway URL handed to clients/DB. The Backblaze bucket stays
    //   fully PRIVATE — downloads go through the b2-download Edge Function which
    //   authenticates the viewer and 302-redirects to a short-lived pre-signed URL.
    // - `direct_url`: raw bucket URL, kept for server-side debugging only (401 for
    //   anonymous callers while the bucket is private).
    // Virtual-hosted-style hosts must be lowercase.
    const publicBucketName = b2Bucket.toLowerCase()
    const directUrl = `https://${publicBucketName}.${b2Endpoint}/${objectPath}`
    const gatewayUrl = `${supabaseUrl}/functions/v1/b2-download?path=${encodeURIComponent(objectPath)}`

    return new Response(
      JSON.stringify({
        path: objectPath,
        url: gatewayUrl,
        direct_url: directUrl,
        type: file.type,
        size: file.size
      }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200
      }
    )

  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 400
      }
    )
  }
})

function requiredSecret(name: string): string {
  const value = Deno.env.get(name)?.trim()
  if (!value) throw new Error(`Missing Edge Function secret: ${name}`)
  return value
}
