// Exercise the shipped inline controller without requiring a browser or touching a phone.
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
const html = await readFile(new URL('../android-companion/app/src/main/assets/quick.html', import.meta.url), 'utf8');
const script = html.match(/<script>([\s\S]*)<\/script>/)[1];
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function page(storage, responses) {
  const elements = new Map(); const calls=[];
  const get=id=>{if(!elements.has(id))elements.set(id,{hidden:false,disabled:false,textContent:'',value:''});return elements.get(id);};
  const context={document:{getElementById:get,addEventListener(){}},localStorage:{getItem:k=>storage.get(k),setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},
    crypto:webcrypto,AbortController,setTimeout:()=>1,clearTimeout(){},fetch:async(path,options)=>{
      calls.push({path,options}); const next=responses.shift();if(next instanceof Error)throw next;
      if(!next)throw Error('unexpected fetch '+path);
      return {ok:next.code<300,status:next.code,json:async()=>next.body};
    }};
  vm.runInNewContext(script,context);
  return {get,calls};
}
const ready={running:false,state:'idle',message:'准备就绪',requestId:'',accessibility:true,installed:true};
const stored=new Map();
let p=page(stored,[{code:200,body:{token:'test-only-token'}},{code:200,body:ready}]);
assert.equal(p.get('pairPanel').hidden,false);
p.get('code').value='123456';
await p.get('pairForm').onsubmit({preventDefault(){}});
assert.equal(stored.get('remote-device-token'),'test-only-token');
assert.equal(p.get('actionPanel').hidden,false);
assert.equal(p.get('runButton').disabled,false);
// Existing credential is reused; a lost run response preserves the id for safe retry.
p=page(stored,[{code:200,body:ready},new Error('network lost')]);await tick();
await p.get('runButton').onclick();
const pending=stored.get('remote-pending-id');assert.ok(pending);
assert.equal(p.get('runButton').disabled,true);
p=page(stored,[{code:200,body:ready},{code:200,body:{...ready,requestId:pending,state:'done'}}]);await tick();
await p.get('runButton').onclick();
assert.equal(JSON.parse(p.calls[1].options.body).requestId,pending);
assert.equal(stored.has('remote-pending-id'),false);
// Revocation sends the next visit back to pairing.
p=page(stored,[{code:401,body:{error:'请先配对'}}]);await tick();
assert.equal(p.get('pairPanel').hidden,false);
assert.equal(stored.has('remote-device-token'),false);
console.log('PASS: pairing UI, persistent credential, reload, lost-response idempotent retry, revoked credential');
