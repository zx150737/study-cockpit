begin;
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('study-images','study-images',false,5242880,array['image/jpeg','image/png','image/webp']);
-- Upload/delete go through the Edge Function, which checks ownership and file signatures.
revoke insert,delete on public.sc_images from authenticated;
create policy sc_images_download on storage.objects for select to authenticated using (
  bucket_id='study-images'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists(select 1 from public.sc_images i join public.sc_tasks t on t.id=i.task_id
    where i.path=name and i.user_id=(select auth.uid()) and i.expire_time>now() and not t.deleted)
);
commit;
