import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const idPattern = /^[A-Za-z0-9._-]{1,12}$/;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRoleKey) {
    return json({ error: "Supabase secrets are not configured" }, 500);
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  const token = authHeader.replace("Bearer ", "");
  if (!token) {
    return json({ error: "ログインが必要です" }, 401);
  }

  const admin = createClient(supabaseUrl, serviceRoleKey);
  const {
    data: { user: actor },
    error: actorError,
  } = await admin.auth.getUser(token);

  if (actorError || !actor) {
    return json({ error: "ログインを確認できませんでした" }, 401);
  }

  const { data: actorProfile, error: profileError } = await admin
    .from("profiles")
    .select("role,is_active")
    .eq("id", actor.id)
    .single();

  if (profileError || actorProfile?.role !== "admin" || actorProfile?.is_active !== true) {
    return json({ error: "管理者のみ実行できます" }, 403);
  }

  const body = await req.json().catch(() => ({}));
  const loginId = String(body.login_id ?? "").trim();
  const password = String(body.password ?? "");
  const displayName = String(body.display_name ?? "").trim();
  const householdName = String(body.household_name ?? "").trim();

  if (!idPattern.test(loginId)) {
    return json({ error: "IDは12文字以内の英数字と . _ - で入力してください" }, 400);
  }
  if (password.length < 8) {
    return json({ error: "パスワードは8文字以上にしてください" }, 400);
  }
  if (!displayName || !householdName) {
    return json({ error: "表示名と家庭名を入力してください" }, 400);
  }

  const { data: existing } = await admin
    .from("profiles")
    .select("id")
    .eq("login_id", loginId)
    .maybeSingle();

  if (existing) {
    return json({ error: "同じIDが既に登録されています" }, 409);
  }

  const email = loginIdToEmail(loginId);
  const { data: created, error: createError } = await admin.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { login_id: loginId, display_name: displayName },
  });

  if (createError || !created.user) {
    return json({ error: createError?.message ?? "ユーザ作成に失敗しました" }, 400);
  }

  const { data: household, error: householdError } = await admin
    .from("households")
    .insert({ name: householdName })
    .select()
    .single();

  if (householdError || !household) {
    await admin.auth.admin.deleteUser(created.user.id);
    return json({ error: householdError?.message ?? "家庭作成に失敗しました" }, 400);
  }

  const { error: insertProfileError } = await admin.from("profiles").insert({
    id: created.user.id,
    login_id: loginId,
    display_name: displayName,
    role: "user",
    household_id: household.id,
    is_active: true,
  });

  if (insertProfileError) {
    await admin.auth.admin.deleteUser(created.user.id);
    await admin.from("households").delete().eq("id", household.id);
    return json({ error: insertProfileError.message }, 400);
  }

  await admin.from("audit_logs").insert({
    actor_id: actor.id,
    action: "admin_create_user",
    detail: { login_id: loginId, household_id: household.id },
  });

  return json({
    ok: true,
    user_id: created.user.id,
    household_id: household.id,
  });
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json; charset=utf-8",
    },
  });
}

function loginIdToEmail(loginId: string) {
  const bytes = new TextEncoder().encode(loginId);
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  const encoded = btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/g, "");
  return `u-${encoded}@baby-allergy.local`;
}
