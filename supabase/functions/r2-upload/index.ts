import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { S3Client, PutObjectCommand } from "https://esm.sh/@aws-sdk/client-s3@3"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })

  try {
    // 1. Auth Validation
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) throw new Error('No authorization header')

    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? ''
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY') ?? Deno.env.get('SUPABASE_PUBLISHABLE_KEY') ?? ''
    const supabaseClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
    })

    const { data: { user }, error: authError } = await supabaseClient.auth.getUser()
    if (authError || !user) throw new Error('Unauthorized')

    // 2. Parse FormData
    const formData = await req.formData()
    const file = formData.get('file') as File
    const type = formData.get('type') as string

    if (!file || !type) throw new Error('Missing file or type')

    // 3. Path Mapping & Validation
    let objectPath = ""
    const uniqueName = `${Date.now()}_${file.name.replace(/[^a-z0-9.]/gi, '_').toLowerCase()}`

    switch (type) {
      case 'profile': objectPath = `profiles/${user.id}/${uniqueName}`; break;
      case 'cover': objectPath = `covers/${user.id}/${uniqueName}`; break;
      case 'post_image': objectPath = `posts/images/${user.id}/${uniqueName}`; break;
      case 'post_video': objectPath = `posts/videos/${user.id}/${uniqueName}`; break;
      case 'reel': objectPath = `reels/${user.id}/${uniqueName}`; break;
      case 'story': objectPath = `stories/${user.id}/${uniqueName}`; break;
      case 'chat': objectPath = `chat/${user.id}/${uniqueName}`; break;
      case 'media': objectPath = `media/${user.id}/${uniqueName}`; break;
      default: throw new Error('Invalid upload type')
    }

    if (file.size > 50 * 1024 * 1024) throw new Error('File size exceeds 50MB limit')

    // 4. Configure R2 S3 Client
    const r2Client = new S3Client({
      region: Deno.env.get('R2_REGION') ?? 'auto',
      endpoint: Deno.env.get('R2_ENDPOINT'),
      credentials: {
        accessKeyId: Deno.env.get('R2_ACCESS_KEY_ID') ?? '',
        secretAccessKey: Deno.env.get('R2_SECRET_ACCESS_KEY') ?? '',
      },
    })

    // 5. Upload to R2
    const arrayBuffer = await file.arrayBuffer()
    await r2Client.send(new PutObjectCommand({
      Bucket: Deno.env.get('R2_BUCKET_NAME'),
      Key: objectPath,
      Body: new Uint8Array(arrayBuffer),
      ContentType: file.type,
    }))

    const gatewayUrl = `${supabaseUrl}/functions/v1/r2-download?path=${encodeURIComponent(objectPath)}`

    // RETURN ALL POSSIBLE PATH KEYS FOR BACKWARD COMPATIBILITY
    return new Response(
      JSON.stringify({
        key: objectPath,
        path: objectPath,
        storage_path: objectPath,
        url: gatewayUrl,
        type: file.type,
        size: file.size
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 200 }
    )

  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400
    })
  }
})
