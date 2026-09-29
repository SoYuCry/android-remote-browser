// Usage: node test/quick_http_test.mjs http://127.0.0.1:16081 /private/path/token.json [--live]
// token.json: {"token":"..."}; credentials are never logged. --live actually opens Feishu once.
import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
const [base, tokenFile, mode] = process.argv.slice(2);
if (!base || !tokenFile) throw Error('Provide base URL and a private credential JSON file');
const { token } = JSON.parse(await readFile(tokenFile, 'utf8'));
const headers = {'X-Remote-Client':'1', Authorization:`Bearer ${token}`};
async function request(path, body) {
  const response = await fetch(base + path, {headers: body ? {...headers,'Content-Type':'application/json'} : headers,
    method:body?'POST':'GET',body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(10000)});
  return {code:response.status, data:await response.json()};
}
const start = performance.now();
assert.equal((await fetch(base + '/quick')).status, 200);
console.log(`Quick page HTTP: ${Math.round(performance.now()-start)} ms (this test connection only)`);
const initial = await request('/api/status');
assert.equal(initial.code, 200);
assert.equal(initial.data.accessibility, true);
assert.equal(initial.data.installed, true);
assert.equal((await request('/api/run', {requestId:'invalid'})).code, 400);
if (mode === '--live') {
  const requestId = randomUUID();
  const accepted = await request('/api/run', {requestId});
  assert.equal(accepted.code, 202);
  assert.equal((await request('/api/run', {requestId})).code, 200);
  assert.equal((await request('/api/run', {requestId:randomUUID()})).code, 409);
  const deadline = performance.now()+25000;
  let state;
  do {
    await new Promise(resolve=>setTimeout(resolve,500));
    state = (await request('/api/status')).data;
  } while(state.running && performance.now()<deadline);
  assert.equal(state.state,'done',state.message);
  assert.equal(state.requestId,requestId);
  const replay=await request('/api/run',{requestId});
  assert.equal(replay.code,200);
  assert.equal(replay.data.running,false);
  console.log('PASS: live launch, Home confirmation, concurrent rejection, duplicate/replay suppression');
}
console.log('PASS: lightweight page, remembered credential, readiness, malformed action rejected');
