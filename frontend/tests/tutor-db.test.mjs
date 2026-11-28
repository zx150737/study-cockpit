import assert from 'node:assert/strict';
import { database, asUser, rpc } from './cloud-db.mjs';

const db = await database();
const a = '11111111-1111-4111-8111-111111111111', b = '22222222-2222-4222-8222-222222222222';
await db.query('insert into auth.users(id) values($1),($2)', [a, b]);
let count = 0;
async function check(name, fn) { await fn(); count++; console.log(`PASS ${name}`); }
const read = (action, data) => rpc(db, 'sc_ai_read', action, data);
const write = (action, data) => rpc(db, 'sc_ai_write', action, data);
const today = (await db.query('select cockpit_private.sc_today()::text as d')).rows[0].d;

// 边缘函数写入答复：以 service_role 身份更新（守卫只放行 service_role）。
async function reply(threadId, payload) {
  await db.exec('reset role');
  await db.query('update public.sc_ai_threads set reply=$1::jsonb, replied_at=now() where id=$2',
    [JSON.stringify(payload), threadId]);
}
const sample = {
  answer: '选 B',
  steps: [{ title: '识别条件', detail: '已知 a>0' }, { title: '代入', detail: '得 b=2' }],
  keyPoints: ['一元二次'],
  pitfalls: ['漏判判别式'],
  practice: [{ stem: '若 a>1，求 b', options: ['1', '2'], answer: '2', explain: '代入即可' }],
};

const project = (action, data) => rpc(db, 'sc_write', action, data);
await asUser(db, a);
const goal = await project('createGoal', { name: '期末' });
const subj = await project('createSubject', { goalId: goal.id, name: '数学', defaultWeight: 1 });

await asUser(db, a);
const t1 = await write('createThread', { question: '求 a+b 的值', subjectId: subj.id, mode: 'explain' });
await check('创建提问并自动生成标题', async () => {
  assert.ok(t1.id > 0);
  assert.equal(t1.title, '求 a+b 的值');
  assert.equal(t1.reply, null);
  assert.equal(t1.subjectName, '数学');
});
await check('未答复的提问不能加入错题本', async () => {
  await assert.rejects(write('addCard', { threadId: t1.id }), /还没有 AI 答复/);
});
await check('客户端不能直接写入或伪造答复', async () => {
  await assert.rejects(
    db.query('update public.sc_ai_threads set reply=$1::jsonb where id=$2', ['{"answer":"假"}', t1.id]),
    /答复只能由服务端写入/);
  await assert.rejects(
    db.query('insert into public.sc_ai_threads(title,question,reply) values($1,$2,$3::jsonb)',
      ['伪造', '伪造', '{"answer":"假"}']),
    /答复只能由服务端写入/);
});
await reply(t1.id, sample);
await asUser(db, a);
await check('答复可读回且结构完整', async () => {
  const v = await read('thread', { id: t1.id });
  assert.equal(v.reply.answer, '选 B');
  assert.equal(v.reply.steps.length, 2);
  assert.equal(v.reply.practice[0].answer, '2');
  assert.ok(v.repliedAt);
});
await check('加入错题本并按答复生成卡背', async () => {
  const card = await write('addCard', { threadId: t1.id });
  const v = await read('cards', { page: 1, size: 20 });
  assert.equal(v.total, 1);
  assert.equal(v.dueTotal, 1);
  const row = v.list[0];
  assert.equal(row.id, card.id);
  assert.equal(row.front, '求 a+b 的值');
  assert.match(row.back, /答案：选 B/);
  assert.match(row.back, /1\. 识别条件：已知 a>0/);
  assert.match(row.back, /易错：漏判判别式/);
  assert.equal(row.subjectName, '数学');
  assert.equal(row.box, 1);
  assert.equal(row.dueDate, today);
});
await check('同一提问不能重复加入错题本', async () => {
  await assert.rejects(write('addCard', { threadId: t1.id }), /已经在错题本里/);
});
const cards = await read('cards', {});
const cardId = cards.list[0].id;
await check('自评 2 升级并推后到期', async () => {
  const r = await write('reviewCard', { id: cardId, grade: 2 });
  assert.equal(r.box, 2);
  const v = await read('cards', {});
  assert.equal(v.list[0].box, 2);
  assert.equal(v.list[0].reviewCount, 1);
  assert.equal(v.list[0].correctCount, 1);
  assert.notEqual(v.list[0].dueDate, today);
  assert.equal(v.dueTotal, 0, '推后后今天不再到期');
});
await check('自评 0 重置为 1 并回到今天到期', async () => {
  const r = await write('reviewCard', { id: cardId, grade: 0 });
  assert.equal(r.box, 1);
  const v = await read('cards', { dueOnly: true });
  assert.equal(v.dueTotal, 1);
  assert.equal(v.list[0].reviewCount, 2);
  assert.equal(v.list[0].correctCount, 1);
});
await check('自评 1 保持盒子并把到期推后一天', async () => {
  const r = await write('reviewCard', { id: cardId, grade: 1 });
  assert.equal(r.box, 1);
  const tomorrow = (await db.query('select (cockpit_private.sc_today()+1)::text as d')).rows[0].d;
  assert.equal(r.dueDate, tomorrow);
});
await check('非法自评与非法入参被拒绝', async () => {
  await assert.rejects(write('reviewCard', { id: cardId, grade: 5 }), /自评取值无效/);
  await assert.rejects(write('createThread', { question: '   ' }), /请输入题目内容/);
  await assert.rejects(write('createThread', { question: '合法', mode: 'hack' }), /模式取值无效/);
  await assert.rejects(read('cards', { size: 101 }), /分页参数无效/);
  await assert.rejects(write('createCard', { front: '', back: 'x' }), /请输入题面/);
});
await check('手动建卡与删除', async () => {
  const c = await write('createCard', { front: '手动题', back: '手动答案', subjectName: '物理' });
  assert.ok(c.id > 0);
  await write('deleteCard', { id: c.id });
  const v = await read('cards', {});
  assert.equal(v.total, 1);
  await assert.rejects(write('deleteCard', { id: c.id }), /复习卡不存在/);
});
await check('统计接口口径正确', async () => {
  const s = await read('stats', {});
  assert.equal(s.threadTotal, 1);
  assert.equal(s.cardTotal, 1);
  assert.equal(s.threadsToday, 1);
  assert.equal(s.dueTotal, 0, '三张卡都已推后，今天不再到期');
  assert.equal(s.mastered, 0);
  assert.equal(s.accuracy, 66.7);
});
const t2 = await write('createThread', { question: '第二题', mode: 'practice' });
await reply(t2.id, { answer: '42', steps: [], keyPoints: [], pitfalls: [] });
await asUser(db, a);
await check('会话列表分页与答复标记', async () => {
  const v = await read('threads', { page: 1, size: 1 });
  assert.equal(v.total, 2);
  assert.equal(v.list.length, 1);
  assert.equal(v.list[0].id, t2.id);
  assert.equal(v.list[0].hasReply, true);
  assert.equal(v.list[0].mode, 'practice');
});

await asUser(db, b);
await check('第二个账号看不到他人的提问与错题', async () => {
  assert.equal((await read('threads', {})).total, 0);
  assert.equal((await read('cards', {})).total, 0);
  assert.equal((await read('stats', {})).threadTotal, 0);
  await assert.rejects(read('thread', { id: t1.id }), /提问不存在/);
});
await check('第二个账号不能越权改他人数据', async () => {
  await assert.rejects(write('deleteThread', { id: t1.id }), /提问不存在/);
  await assert.rejects(write('reviewCard', { id: cardId, grade: 2 }), /复习卡不存在/);
  await assert.rejects(write('deleteCard', { id: cardId }), /复习卡不存在/);
  await assert.rejects(write('createThread', { question: '越权', subjectId: subj.id }), /科目不存在/);
  assert.equal((await db.query('select * from public.sc_ai_threads')).rows.length, 0);
  assert.equal((await db.query('select * from public.sc_ai_cards')).rows.length, 0);
});

await asUser(db, a);
await check('删除提问同时归档其错题卡', async () => {
  await write('deleteThread', { id: t1.id });
  assert.equal((await read('threads', {})).total, 1);
  assert.equal((await read('cards', {})).total, 0);
  await assert.rejects(write('addCard', { threadId: t1.id }), /提问不存在/);
  await assert.rejects(db.query('update public.sc_ai_threads set deleted=false where id=$1', [t1.id]),
    /不能恢复已删除记录/);
});
await asUser(db, null);
await check('匿名用户无法读写任何助手数据', async () => {
  await assert.rejects(read('threads', {}));
  await assert.rejects(write('createThread', { question: '匿名' }));
  await assert.rejects(db.query('select * from public.sc_ai_threads'));
});
await db.close();
console.log(`PASS ${count} tutor checks using PostgreSQL/PGlite.`);
