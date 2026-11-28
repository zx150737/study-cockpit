import assert from 'node:assert/strict';
import { database, asUser } from './cloud-db.mjs';

// 后台管理能力专项测试：管理员白名单、跨用户只读查询、图片归档隔离。
const db = await database();
const a = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'; // 普通用户 A
const b = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'; // 普通用户 B
const m = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'; // 管理员
await db.query('insert into auth.users(id) values($1),($2),($3)', [a, b, m]);

let count = 0;
async function check(name, fn) {
  await fn();
  count++;
  console.log(`PASS ${name}`);
}

const today = (await db.query('select cockpit_private.sc_today()::text as d')).rows[0].d;

// A 造一条业务数据
await asUser(db, a);
const goal = (await db.query("select public.sc_write('createGoal',$1::jsonb) as v", [JSON.stringify({ name: 'A目标' })]))
  .rows[0].v;
const subject = (
  await db.query("select public.sc_write('createSubject',$1::jsonb) as v", [
    JSON.stringify({ goalId: goal.id, name: 'A科目', defaultWeight: 2 }),
  ])
).rows[0].v;
const task = (
  await db.query("select public.sc_write('createTask',$1::jsonb) as v", [
    JSON.stringify({ goalId: goal.id, subjectId: subject.id, title: 'A任务', taskDate: today }),
  ])
).rows[0].v;

const imagePath = `${a}/${task.id}/dddddddd-dddd-4ddd-8ddd-dddddddddddd.png`;
// 归档桶沿用相同路径（桶本身即隔离层），这样所有者策略仍然成立
const archivePath = imagePath;
await db.exec('reset role');
await db.query(
  'insert into public.sc_images(user_id,task_id,original_name,path,mime_type,file_size) values($1,$2,$3,$4,$5,100)',
  [a, task.id, '课堂照.png', imagePath, 'image/png'],
);
await db.query('insert into storage.objects(bucket_id,name) values($1,$2)', ['study-images', imagePath]);

// ---------- 1. 未加入白名单时，谁都不是管理员 ----------
await asUser(db, a);
await check('普通用户不是管理员，且管理函数一律被拒', async () => {
  assert.equal((await db.query('select public.sc_is_admin() as v')).rows[0].v, false);
  await assert.rejects(db.query('select public.sc_admin_users()'));
  await assert.rejects(db.query('select public.sc_admin_tasks($1,1,10)', [b]));
  await assert.rejects(db.query('select public.sc_admin_images($1,true)', [b]));
  await assert.rejects(db.query('select public.sc_admin_add($1,null)', ['x@example.com']));
});

// ---------- 2. 白名单生效 ----------
await db.exec('reset role');
await db.query('insert into public.sc_admins(user_id,note) values($1,$2)', [m, '站长']);
await asUser(db, m);
await check('白名单中的用户被识别为管理员', async () => {
  assert.equal((await db.query('select public.sc_is_admin() as v')).rows[0].v, true);
});

// ---------- 3. 管理员可跨用户只读 ----------
await check('管理员可读全部用户总览并含统计数据', async () => {
  const users = (await db.query('select public.sc_admin_users() as v')).rows[0].v;
  assert.equal(users.length, 3);
  const ua = users.find((u) => u.userId === a);
  assert.equal(ua.taskTotal, 1);
  assert.equal(ua.taskFinished, 0);
  assert.equal(ua.goalCount, 1);
  assert.equal(ua.imageCount, 1);
  assert.equal(ua.archivedCount, 0);
  // 未设置昵称时返回空串而不是 null
  assert.equal(typeof ua.nickname, 'string');
});

await check('管理员可读指定用户的任务明细', async () => {
  const page = (await db.query('select public.sc_admin_tasks($1,1,50) as v', [a])).rows[0].v;
  assert.equal(page.total, 1);
  assert.equal(page.list[0].title, 'A任务');
  assert.equal(page.list[0].goalName, 'A目标');
  // 越界分页被拒
  await assert.rejects(db.query('select public.sc_admin_tasks($1,1,999)', [a]));
});

await check('管理员可读指定用户的图片清单', async () => {
  const imgs = (await db.query('select public.sc_admin_images($1,true) as v', [a])).rows[0].v;
  assert.equal(imgs.length, 1);
  assert.equal(imgs[0].path, imagePath);
  assert.equal(imgs[0].archived, false);
  assert.equal(imgs[0].taskTitle, 'A任务');
});

// ---------- 4. 管理员能读存储对象，普通用户仍被隔离 ----------
await check('管理员可读图片桶对象，他人不可', async () => {
  assert.equal((await db.query('select * from storage.objects')).rows.length, 1);

  await asUser(db, b);
  assert.equal((await db.query('select * from storage.objects')).rows.length, 0);

  await asUser(db, a);
  assert.equal((await db.query('select * from storage.objects')).rows.length, 1);
});

// ---------- 5. 归档：清单标记 + 长期桶隔离 ----------
await db.exec('reset role');
await db.query('insert into storage.objects(bucket_id,name) values($1,$2)', ['study-archive', archivePath]);
await db.query('update public.sc_images set archived=true, archived_at=now(), archived_by=$1, path=$2 where path=$3', [
  m,
  archivePath,
  imagePath,
]);
await db.query('delete from storage.objects where bucket_id=$1 and name=$2', ['study-images', imagePath]);

await asUser(db, m);
await check('归档图片在管理清单中被正确标记', async () => {
  const imgs = (await db.query('select public.sc_admin_images($1,true) as v', [a])).rows[0].v;
  assert.equal(imgs.length, 1);
  assert.equal(imgs[0].archived, true);
  assert.equal(imgs[0].path, archivePath);
  const users = (await db.query('select public.sc_admin_users() as v')).rows[0].v;
  const ua = users.find((u) => u.userId === a);
  assert.equal(ua.imageCount, 0); // 有效（未归档）图片归零
  assert.equal(ua.archivedCount, 1);
});

await check('用户仍可读自己已归档的图片，他人不可', async () => {
  await asUser(db, a);
  const own = (await db.query("select * from storage.objects where bucket_id='study-archive'")).rows;
  assert.equal(own.length, 1);

  await asUser(db, b);
  const other = (await db.query("select * from storage.objects where bucket_id='study-archive'")).rows;
  assert.equal(other.length, 0);
});

// ---------- 6. 白名单维护的安全边界 ----------
await db.exec('reset role');
await db.query("insert into auth.users(id) values('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee')");
await asUser(db, m);
await check('管理员可按邮箱增删白名单，且不能移除最后一个管理员', async () => {
  await assert.rejects(db.query("select public.sc_admin_add('nobody@example.com',null)"));
  const list0 = (await db.query('select public.sc_admin_list() as v')).rows[0].v;
  assert.equal(list0.length, 1);
  await assert.rejects(db.query('select public.sc_admin_remove($1)', [m]));

  // 普通用户不能借管理函数自提权
  await asUser(db, a);
  await assert.rejects(db.query('select public.sc_admin_add($1,null)', ['a@example.com']));
  await assert.rejects(db.query('select public.sc_admin_remove($1)', [m]));
});

console.log(`PASS ${count} admin checks (whitelist, cross-user read, archive isolation)`);
