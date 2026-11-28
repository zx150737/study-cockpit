// AI 学习助手：服务端调用模型，密钥只存在于边缘函数环境变量中。
// 前端只负责建提问（sc_ai_write）和读答复（sc_ai_read），答复列由本函数用 service_role 写入。
import { createClient } from 'npm:@supabase/supabase-js@2.119.2';

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};
const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { ...cors, 'Content-Type': 'application/json' } });

const DAILY_LIMIT = 60;
const MAX_QUESTION = 4000;

const SYSTEM = `你是一位耐心的一对一家教，面向中国的中学生与大学生。
要求：
1. 先讲清思路再给结论，步骤要可复现，不要跳步，也不要只给答案。
2. 不代替学生思考：作文、论述、开放题给出结构、要点和评判标准，不要写成品；编程题给思路与关键片段，不要整篇代写。
3. 数学与公式用纯文本或 LaTeX 行内写法，不要使用 Markdown 代码块。
4. 只输出一个 JSON 对象，不要任何额外说明文字。

JSON 结构：
{"title":"不超过20字的标题","answer":"最终答案或结论","steps":[{"title":"步骤标题","detail":"这一步做什么、为什么"}],"keyPoints":["考点"],"pitfalls":["易错点"],"practice":[{"stem":"变式题题干","options":["A. 选项一","B. 选项二"],"answer":"正确答案","explain":"解析"}]}
约束：steps 2~6 条；keyPoints 1~4 条；pitfalls 0~3 条；practice 恰好 2 条，填空题或解答题把 options 设为空数组。`;

interface Reply {
  title: string;
  answer: string;
  steps: { title: string; detail: string }[];
  keyPoints: string[];
  pitfalls: string[];
  practice: { stem: string; options: string[]; answer: string; explain: string }[];
}

function text(value: unknown, max: number): string {
  return typeof value === 'string' ? value.trim().slice(0, max) : '';
}

function list(value: unknown, max: number): unknown[] {
  return Array.isArray(value) ? value.slice(0, max) : [];
}

/** 模型输出不可信：逐字段校验、裁剪长度，并丢掉结构不符的条目。 */
function normalize(raw: unknown): Reply {
  const o = (raw ?? {}) as Record<string, unknown>;
  const reply: Reply = {
    title: text(o.title, 80),
    answer: text(o.answer, 4000),
    steps: list(o.steps, 6)
      .map((step) => {
        const s = (step ?? {}) as Record<string, unknown>;
        return { title: text(s.title, 60), detail: text(s.detail, 1200) };
      })
      .filter((step) => step.detail.length > 0),
    keyPoints: list(o.keyPoints, 6).map((v) => text(v, 200)).filter((v) => v.length > 0),
    pitfalls: list(o.pitfalls, 4).map((v) => text(v, 300)).filter((v) => v.length > 0),
    practice: list(o.practice, 2)
      .map((item) => {
        const p = (item ?? {}) as Record<string, unknown>;
        return {
          stem: text(p.stem, 1200),
          options: list(p.options, 8).map((v) => text(v, 300)).filter((v) => v.length > 0),
          answer: text(p.answer, 1200),
          explain: text(p.explain, 1600),
        };
      })
      .filter((p) => p.stem.length > 0),
  };
  if (!reply.answer && reply.steps.length === 0) throw new Error('模型未返回可用的讲解内容');
  return reply;
}

function parse(content: string): unknown {
  const cleaned = content.trim().replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '');
  try {
    return JSON.parse(cleaned);
  } catch {
    const start = cleaned.indexOf('{');
    const end = cleaned.lastIndexOf('}');
    if (start < 0 || end <= start) throw new Error('模型返回的不是合法 JSON');
    return JSON.parse(cleaned.slice(start, end + 1));
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response(null, { headers: cors });
  if (req.method !== 'POST') return json({ error: '请求方法不支持' }, 405);

  const url = Deno.env.get('SUPABASE_URL')!;
  const authorization = req.headers.get('Authorization') ?? '';
  const client = createClient(url, Deno.env.get('SUPABASE_ANON_KEY')!, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false },
  });
  const { data: { user }, error: authError } = await client.auth.getUser();
  if (authError || !user) return json({ error: '请先登录' }, 401);

  let threadId = 0;
  let regenerate = false;
  try {
    const body = await req.json();
    threadId = Number(body?.threadId);
    regenerate = body?.regenerate === true;
  } catch {
    return json({ error: '请求格式不正确' }, 400);
  }
  if (!Number.isSafeInteger(threadId) || threadId < 1) return json({ error: '提问编号无效' }, 400);

  const admin = createClient(url, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, { auth: { persistSession: false } });

  try {
    const thread = await admin
      .from('sc_ai_threads')
      .select('id,user_id,question,mode,reply,deleted')
      .eq('id', threadId)
      .eq('user_id', user.id)
      .eq('deleted', false)
      .single();
    if (thread.error || !thread.data) return json({ error: '提问不存在或已删除' }, 404);
    // 已有答复直接返回，避免刷新页面重复计费。
    if (thread.data.reply && !regenerate) return json({ reply: thread.data.reply, cached: true });

    const stats = await client.rpc('sc_ai_read', { action: 'stats' });
    if (!stats.error && Number(stats.data?.threadsToday ?? 0) > DAILY_LIMIT) {
      return json({ error: `今天已用满 ${DAILY_LIMIT} 次 AI 讲解额度，明天再来` }, 429);
    }

    const key = Deno.env.get('DEEPSEEK_API_KEY');
    if (!key) return json({ error: '管理员尚未配置 AI 密钥（DEEPSEEK_API_KEY）' }, 503);
    const model = Deno.env.get('DEEPSEEK_MODEL') || 'deepseek-chat';
    const question = String(thread.data.question ?? '').slice(0, MAX_QUESTION);
    const practice = thread.data.mode === 'practice';
    const prompt = practice
      ? `学生的知识点/薄弱点是：\n${question}\n\n请据此出 2 道同类变式练习，并补一段方法点拨（写在 steps 里）。`
      : `请讲解下面这道题：\n${question}`;

    let response: Response;
    try {
      response = await fetch('https://api.deepseek.com/chat/completions', {
        method: 'POST',
        headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model,
          messages: [
            { role: 'system', content: SYSTEM },
            { role: 'user', content: prompt },
          ],
          response_format: { type: 'json_object' },
          temperature: 0.3,
          max_tokens: 2400,
          stream: false,
        }),
        signal: AbortSignal.timeout(100000),
      });
    } catch {
      return json({ error: 'AI 服务连接超时，请稍后重试' }, 504);
    }

    if (!response.ok) {
      const detail = await response.text().catch(() => '');
      if (response.status === 401) return json({ error: '管理员配置的 AI 密钥无效' }, 502);
      if (response.status === 402) return json({ error: 'AI 账户余额不足，请联系管理员充值' }, 502);
      if (response.status === 429) return json({ error: 'AI 接口限流，请稍后重试' }, 429);
      console.error('deepseek error', response.status, detail.slice(0, 500));
      return json({ error: 'AI 服务暂时不可用，请稍后重试' }, 502);
    }

    const payload = await response.json();
    const content = payload?.choices?.[0]?.message?.content;
    if (typeof content !== 'string' || content.length < 2) return json({ error: 'AI 未返回内容，请重试' }, 502);

    let reply: Reply;
    try {
      reply = normalize(parse(content));
    } catch (e) {
      console.error('parse failed', String(e), content.slice(0, 500));
      return json({ error: 'AI 返回格式异常，请重试一次' }, 502);
    }

    const tokens = Number(payload?.usage?.total_tokens ?? 0);
    const saved = await admin
      .from('sc_ai_threads')
      .update({
        reply,
        replied_at: new Date().toISOString(),
        total_tokens: Number.isFinite(tokens) && tokens > 0 ? Math.round(tokens) : 0,
        title: reply.title ? reply.title.slice(0, 80) : undefined,
      })
      .eq('id', threadId)
      .eq('user_id', user.id);
    if (saved.error) {
      console.error('save failed', saved.error.message);
      return json({ error: 'AI 已生成但保存失败，请重试' }, 500);
    }
    return json({ reply });
  } catch (e) {
    console.error('ai-tutor failed', String(e));
    return json({ error: 'AI 讲解失败，请稍后重试' }, 500);
  }
});
