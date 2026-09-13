import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { S3Client, DeleteObjectCommand } from "https://esm.sh/@aws-sdk/client-s3@3"

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
}

serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders })
  if (req.method !== "POST") return new Response(JSON.stringify({ error: "Method not allowed" }), { status: 405, headers: corsHeaders })

  try {
    const authorization = req.headers.get("Authorization")
    if (!authorization) throw new Error("Unauthorized")

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_ANON_KEY") ?? "",
      { global: { headers: { Authorization: authorization } } },
    )
    const { data: { user }, error } = await supabase.auth.getUser()
    if (error || !user) throw new Error("Unauthorized")

    // Admin Check
    const { data: actor } = await supabase
      .from("app_users")
      .select("role,can_delete_posts,can_manage_users")
      .eq("uid", user.id)
      .maybeSingle()
    const isAdmin = actor?.role === "SUPER_ADMIN" || actor?.role === "ADMIN" ||
      actor?.can_delete_posts === true || actor?.can_manage_users === true

    const body = await req.json()
    const path = typeof body.path === "string" ? body.path : ""

    // Authorization: Must be owner or admin.
    // Object keys follow structure: {type}/{userId}/{filename}
    const pathParts = path.split('/')
    const ownerId = pathParts[1]

    if (!isAdmin && ownerId !== user.id) {
      throw new Error("Forbidden: You do not have permission to delete this object")
    }

    const r2Client = new S3Client({
      region: Deno.env.get('R2_REGION') ?? 'auto',
      endpoint: Deno.env.get('R2_ENDPOINT'),
      credentials: {
        accessKeyId: Deno.env.get('R2_ACCESS_KEY_ID') ?? '',
        secretAccessKey: Deno.env.get('R2_SECRET_ACCESS_KEY') ?? '',
      },
    })

    await r2Client.send(new DeleteObjectCommand({
      Bucket: Deno.env.get('R2_BUCKET_NAME'),
      Key: path,
    }))

    return new Response(JSON.stringify({ deleted: true, path }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 200
    })
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 400
    })
  }
})
