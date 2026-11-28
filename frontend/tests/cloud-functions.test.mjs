// Exercises the shipped Edge Function handlers with a simulated Supabase transport.
// Hosted Auth/Storage still require deployment acceptance checks.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import ts from 'typescript';
import { webcrypto } from 'node:crypto';

const owner='11111111-1111-4111-8111-111111111111';
let authenticated=true, uploadError=false, removeError=false, nextId=0;
const images=new Map(), files=new Map(), events=[];
function query(table, admin) {
  let operation='select', input, filters=[];
  const q={
    select(){return q;},insert(value){operation='insert';input=value;return q;},delete(){operation='delete';return q;},
    eq(k,v){filters.push(row=>row[k]===v);return q;},lte(k,v){filters.push(row=>row[k]<=v);return q;},
    in(k,v){filters.push(row=>v.includes(row[k]));return q;},order(){return q;},limit(){return q;},
    single(){return execute(true);},then(resolve,reject){return execute(false).then(resolve,reject);},
  };
  async function execute(single) {
    if(table==='sc_tasks')return{data:filters.every(f=>f({id:10,user_id:owner,deleted:false}))?{id:10}:null,error:null};
    if(operation==='insert'){const row={...input,id:++nextId,archived:false,expire_time:new Date(Date.now()+15*86400000).toISOString()};images.set(row.id,row);events.push('metadata-insert');return{data:row,error:null};}
    const rows=[...images.values()].filter(row=>(admin||row.user_id===owner)&&filters.every(f=>f(row)));
    if(operation==='delete'){rows.forEach(row=>images.delete(row.id));events.push('metadata-delete');return{error:null};}
    return {data:single?rows[0]||null:rows,error:null};
  }
  return q;
}
function createClient(_url,key){return{
  auth:{getUser:async()=>({data:{user:authenticated?{id:owner}:null},error:authenticated?null:{message:'Invalid token'}})},
  from:table=>query(table,key==='service-test'),
  storage:{from:()=>({
    upload:async(path,bytes,options)=>{events.push('storage-upload');if(uploadError)return{error:{message:'failed'}};files.set(path,{bytes,options});return{error:null};},
    remove:async(paths)=>{events.push('storage-delete');if(removeError)return{error:{message:'failed'}};paths.forEach(path=>files.delete(path));return{error:null};},
  })},
};}
async function handler(name){
  let result;
  const source=(await readFile(new URL(`../../supabase/functions/${name}/index.ts`,import.meta.url),'utf8')).replace(/^import .*createClient.*\n/,'');
  const code=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext}}).outputText;
  vm.runInNewContext(code,{createClient,Request,Response,FormData,File,Uint8Array,crypto:webcrypto,
    Deno:{env:{get:k=>({SUPABASE_URL:'http://local.test',SUPABASE_ANON_KEY:'anon-test',SUPABASE_SERVICE_ROLE_KEY:'service-test',CLEANUP_SECRET:'private-cleanup-test'})[k]},serve:fn=>{result=fn;}}});
  return result;
}
const upload=await handler('task-images'), cleanup=await handler('cleanup-images');
function imageRequest(taskId=10,bytes=new Uint8Array([137,80,78,71,13,10,26,10,0]),name='image.png'){
  const form=new FormData();form.set('taskId',String(taskId));form.set('file',new File([bytes],name,{type:'image/png'}));
  return new Request('http://local.test',{method:'POST',headers:{Authorization:'Bearer user-test'},body:form});
}
const deleteRequest=id=>new Request('http://local.test',{method:'POST',headers:{Authorization:'Bearer user-test','Content-Type':'application/json'},body:JSON.stringify({operation:'delete',id})});
const cleanupRequest=secret=>new Request('http://local.test',{method:'POST',headers:{'x-cleanup-secret':secret}});
let count=0;
async function check(name,fn){await fn();count++;console.log(`PASS ${name}`);}
await check('anonymous upload rejected before storage writes',async()=>{authenticated=false;assert.equal((await upload(imageRequest())).status,401);assert.equal(files.size,0);authenticated=true;});
await check('foreign task upload rejected',async()=>{assert.equal((await upload(imageRequest(999))).status,404);});
await check('spoofed image signatures rejected',async()=>{assert.equal((await upload(imageRequest(10,new TextEncoder().encode('<script>bad</script>')))).status,400);});
await check('oversized file rejected',async()=>{assert.equal((await upload(imageRequest(10,new Uint8Array(5242881)))).status,400);});
await check('valid image saved using owned random path and detected MIME',async()=>{const r=await upload(imageRequest());assert.equal(r.status,200);assert.equal(files.size,1);const row=[...images.values()][0];assert.ok(row.path.startsWith(owner+'/10/'));assert.equal(files.get(row.path).options.contentType,'image/png');});
await check('foreign image deletion rejected',async()=>{images.set(999,{id:999,user_id:'foreign-user',path:'foreign/path'});assert.equal((await upload(deleteRequest(999))).status,404);assert.ok(images.has(999));images.delete(999);});
await check('owned deletion removes storage before metadata',async()=>{events.length=0;assert.equal((await upload(deleteRequest([...images.keys()][0]))).status,200);assert.deepEqual(events,['storage-delete','metadata-delete']);});
await check('failed upload rolls back metadata',async()=>{uploadError=true;assert.equal((await upload(imageRequest())).status,500);assert.equal(images.size,0);uploadError=false;});
await check('cleanup rejects public or absent secret',async()=>{assert.equal((await cleanup(cleanupRequest('sb_publishable_test'))).status,401);});
images.set(1,{id:1,user_id:owner,path:'expired',archived:false,expire_time:'2020-01-01T00:00:00.000Z'});files.set('expired',{});
images.set(2,{id:2,user_id:owner,path:'active',archived:false,expire_time:'2099-01-01T00:00:00.000Z'});files.set('active',{});
// 已归档图片即便到期也不应被清理
images.set(3,{id:3,user_id:owner,path:'archived-old',archived:true,expire_time:'2020-01-01T00:00:00.000Z'});files.set('archived-old',{});
await check('cleanup preserves metadata when storage fails',async()=>{removeError=true;assert.equal((await cleanup(cleanupRequest('private-cleanup-test'))).status,500);assert.ok(images.has(1));removeError=false;});
await check('cleanup retry deletes expired files and preserves active images',async()=>{assert.equal((await cleanup(cleanupRequest('private-cleanup-test'))).status,200);assert.ok(!images.has(1)&&!files.has('expired'));assert.ok(images.has(2)&&files.has('active'));});
await check('cleanup never deletes archived images',async()=>{const r=await cleanup(cleanupRequest('private-cleanup-test'));assert.equal(r.status,200);assert.ok(images.has(3)&&files.has('archived-old'));});
console.log(`PASS ${count} Edge Function handler checks with simulated service transport.`);
