import { PGlite } from '@electric-sql/pglite';
import { readFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

export async function database() {
  const db = new PGlite();
  await db.exec(`
    create role anon; create role authenticated; create role service_role bypassrls;
    create schema auth;
    -- 贴近真实 Supabase 的 auth.users：管理后台需要 email / 昵称 / 时间戳
    create table auth.users(
      id uuid primary key,
      email text,
      raw_user_meta_data jsonb default '{}'::jsonb,
      created_at timestamptz default now(),
      last_sign_in_at timestamptz
    );
    create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    grant usage on schema auth to anon,authenticated,service_role;
    grant execute on function auth.uid() to anon,authenticated,service_role;
    create schema storage;
    create table storage.buckets(id text primary key,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]);
    create table storage.objects(id uuid default gen_random_uuid(),bucket_id text,name text,primary key(bucket_id,name));
    alter table storage.objects enable row level security;
    grant usage on schema storage to authenticated,service_role;
    grant select,insert,update,delete on storage.objects to authenticated,service_role;
    create function storage.foldername(name text) returns text[] language sql immutable as $$ select string_to_array(name,'/') $$;
    grant execute on function storage.foldername(text) to authenticated,service_role;
  `);
  const directory = fileURLToPath(new URL('../../supabase/migrations/', import.meta.url));
  for (const name of (await readdir(directory)).filter(name => name.endsWith('.sql')).sort()) {
    await db.exec(await readFile(`${directory}/${name}`, 'utf8'));
  }
  return db;
}
export async function asUser(db, id) {
  await db.exec('reset role');
  await db.query("select set_config('request.jwt.claim.sub',$1,false)", [id || '']);
  await db.exec(id ? 'set role authenticated' : 'set role anon');
}
export async function rpc(db, name, action, data = {}) {
  return (await db.query(`select public.${name}($1,$2::jsonb) as value`, [action,JSON.stringify(data)])).rows[0].value;
}
