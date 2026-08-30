import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { DeleteObjectCommand, HeadObjectCommand, S3Client } from "https://esm.sh/@aws-sdk/client-s3@3"

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
}

serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders })
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405)

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

    const { data: actor } = await supabase
      .from("app_users")
      .select("role,can_delete_posts,can_manage_users")
      .eq("uid", user.id)
      .maybeSingle()
    const isAdmin = actor?.role === "SUPER_ADMIN" || actor?.role === "ADMIN" ||
      actor?.can_delete_posts === true || actor?.can_manage_users === true

    const body = await req.json()
    const path = typeof body.path === "string" ? body.path : ""
    const ownerMatch = path.match(/^users\/([\w-]+)\/(profile|cover|post|reel|story|media)\/[\w.\-]+$/)
    if (!ownerMatch || path.includes("..") || (!isAdmin && ownerMatch[1] !== user.id)) {
      throw new Error("Invalid or unauthorized object path")
    }

    const client = new S3Client({
      region: requiredSecret("B2_REGION"),
      endpoint: `https://${requiredSecret("B2_ENDPOINT").replace(/^https?:\/\//, "").replace(/\/$/, "")}`,
      credentials: {
        accessKeyId: requiredSecret("B2_KEY_ID"),
        secretAccessKey: requiredSecret("B2_APPLICATION_KEY"),
      },
    })
    const object = {
      Bucket: requiredSecret("B2_BUCKET"),
      Key: path,
    }
    try {
      await client.send(new HeadObjectCommand(object))
    } catch (error) {
      if (error instanceof Error && (error.name === "NotFound" || error.name === "NoSuchKey")) {
        return json({ deleted: true, alreadyAbsent: true, path })
      }
      throw error
    }
    await client.send(new DeleteObjectCommand(object))
    try {
      await client.send(new HeadObjectCommand(object))
      throw new Error("B2 object still exists after deletion")
    } catch (error) {
      if (error instanceof Error && error.name !== "NotFound" && error.name !== "NoSuchKey") throw error
    }
    return json({ deleted: true, path })
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : "Delete failed" }, 400)
  }
})

function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  })
}

function requiredSecret(name: string): string {
  const value = Deno.env.get(name)?.trim()
  if (!value) throw new Error(`Missing Edge Function secret: ${name}`)
  return value
}
