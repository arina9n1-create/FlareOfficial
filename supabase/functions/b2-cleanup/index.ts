import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { DeleteObjectCommand, ListObjectsV2Command, S3Client } from "https://esm.sh/@aws-sdk/client-s3@3"

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
}

// Matches every object this system ever created:
// users/{uid}/{profile|cover|post|reel|story|media}/{timestamp}_{filename}
const PATH_RE = /users\/[A-Za-z0-9_\-]+\/(?:profile|cover|post|reel|story|media)\/[A-Za-z0-9._\-]+/g

function extractPaths(value: unknown, out: Set<string>) {
  if (value === null || value === undefined) return
  const str = typeof value === "string" ? value : JSON.stringify(value)
  if (!str || str === "null" || str === "default") return
  // 1) Gateway URLs: .../b2-download?path=users%2F...
  try {
    if (str.includes("b2-download?path=")) {
      const q = str.split("b2-download?")[1] ?? ""
      const raw = q.split("&").find((p) => p.startsWith("path="))
      if (raw) out.add(decodeURIComponent(raw.slice(5)))
    }
  } catch { /* ignore */ }
  // 2) Raw bucket URLs and plain stored paths
  for (const m of str.matchAll(PATH_RE)) out.add(m[0])
}

function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } })
}

function requiredSecret(name: string): string {
  const value = Deno.env.get(name)?.trim()
  if (!value) throw new Error(`Missing Edge Function secret: ${name}`)
  return value
}

serve(async (req) => {
  if (req.method === "OPTIONS") return json({ ok: true }, 200)
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405)

  try {
    const authorization = req.headers.get("Authorization")
    if (!authorization) throw new Error("Unauthorized")

    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? ""
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? ""
    const userClient = createClient(supabaseUrl, anonKey, { global: { headers: { Authorization: authorization } } })
    const { data: { user }, error } = await userClient.auth.getUser()
    if (error || !user) throw new Error("Unauthorized")

    // Admin-only: SUPER_ADMIN / ADMIN / can_clean_storage
    const { data: actor } = await userClient.from("app_users")
      .select("role,can_clean_storage").eq("uid", user.id).maybeSingle()
    const isAdmin = actor?.role === "SUPER_ADMIN" || actor?.role === "ADMIN" || actor?.can_clean_storage === true
    if (!isAdmin) throw new Error("Forbidden: admin only")

    const body = await req.json().catch(() => ({}))
    const mode = body.mode === "purge" ? "purge" : "scan"

    // Service-role client: bypass RLS to read every media reference
    const db = createClient(supabaseUrl, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "")

    const referenced = new Set<string>()
    const sources: Array<[string, string[]]> = [
      ["posts", ["post_image_res", "storage_path", "thumbnail_path", "user_avatar_path"]],
      ["reels", ["video_url", "image_res", "storage_path", "thumbnail_path", "user_avatar_path"]],
      ["stories", ["media_url", "image_res", "storage_path", "user_avatar_path"]],
      ["chat_messages", ["media_url", "storage_path", "sender_avatar_path", "sender_avatar"]],
      ["app_users", ["avatar_type", "cover_type", "avatar_path", "cover_path"]],
    ]
    for (const [table, cols] of sources) {
      let from = 0
      for (;;) {
        const { data, error: dbErr } = await db.from(table).select(cols.join(",")).range(from, from + 999)
        if (dbErr) throw new Error(`DB read failed (${table}): ${dbErr.message}`)
        if (!data || data.length === 0) break
        for (const row of data) for (const c of cols) extractPaths(row[c], referenced)
        if (data.length < 1000) break
        from += 1000
      }
    }

    // List every object actually stored in B2 under users/
    const client = new S3Client({
      region: requiredSecret("B2_REGION"),
      endpoint: `https://${requiredSecret("B2_ENDPOINT").replace(/^https?:\/\//, "").replace(/\/$/, "")}`,
      credentials: {
        accessKeyId: requiredSecret("B2_KEY_ID"),
        secretAccessKey: requiredSecret("B2_APPLICATION_KEY"),
      },
    })
    const bucket = requiredSecret("B2_BUCKET")

    const inB2: Array<{ key: string; size: number }> = []
    let token: string | undefined = undefined
    do {
      const res = await client.send(new ListObjectsV2Command({ Bucket: bucket, Prefix: "users/", ContinuationToken: token }))
      for (const obj of res.Contents ?? []) if (obj.Key) inB2.push({ key: obj.Key, size: obj.Size ?? 0 })
      token = res.IsTruncated ? res.NextContinuationToken : undefined
    } while (token)

    const orphans = inB2.filter((o) => !referenced.has(o.key))

    if (mode === "scan") {
      return json({
        mode, b2Objects: inB2.length, referenced: referenced.size,
        orphans: orphans.length,
        orphanKeys: orphans.slice(0, 500),
        objects: inB2.map((o) => ({ key: o.key, size: o.size })).slice(0, 500),
      })
    }

    // purge: hard-delete every orphan in chunks
    const deleted: string[] = []
    const failed: Array<{ key: string; error: string }> = []
    for (let i = 0; i < orphans.length; i += 10) {
      const chunk = orphans.slice(i, i + 10).map((o) => o.key)
      const results = await Promise.allSettled(chunk.map((key) =>
        client.send(new DeleteObjectCommand({ Bucket: bucket, Key: key }))
      ))
      results.forEach((r, idx) => {
        if (r.status === "fulfilled") deleted.push(chunk[idx])
        else failed.push({ key: chunk[idx], error: String(r.reason) })
      })
    }
    return json({
      mode, b2Objects: inB2.length, referenced: referenced.size,
      deletedCount: deleted.length, failedCount: failed.length,
      failed: failed.slice(0, 100), remainingOrphans: orphans.length - deleted.length,
    })
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : "Cleanup failed" }, 400)
  }
})

