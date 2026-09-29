// Checks the real WebSocket-to-VNC path up to the password challenge; never submits a password.
import assert from 'node:assert/strict';
const endpoint = process.argv[2];
if (!endpoint) throw Error('Usage: node test/vnc_handshake_test.mjs ws://127.0.0.1:16081/websockify');
const ws = new WebSocket(endpoint);
ws.binaryType = 'arraybuffer';
let pending = Buffer.alloc(0), stage = 0, completed = false;
const timer = setTimeout(()=>{console.error('FAIL: handshake timed out');ws.close();process.exitCode=1;},10000);
ws.addEventListener('error',()=>{clearTimeout(timer);if(!completed){console.error('FAIL: WebSocket connection failed');process.exitCode=1;}});
ws.addEventListener('message',event=>{
  try {
    pending=Buffer.concat([pending,Buffer.from(event.data)]);
    if(stage===0 && pending.length>=12){
      assert.match(pending.subarray(0,12).toString(),/^RFB 003\.008\n$/);
      pending=pending.subarray(12);ws.send(Buffer.from('RFB 003.008\n'));stage=1;
    }
    if(stage===1 && pending.length>=1 && pending.length>=pending[0]+1){
      const count=pending[0];assert.ok(count>0);
      assert.ok(pending.subarray(1,count+1).includes(2),'VNC password authentication must be available');
      pending=pending.subarray(count+1);ws.send(Uint8Array.of(2));stage=2;
    }
    if(stage===2 && pending.length>=16){completed=true;clearTimeout(timer);console.log('PASS: WebSocket upgrade, RFB negotiation, VNC password challenge');ws.close();}
  } catch(error){clearTimeout(timer);console.error('FAIL:',error.message);process.exitCode=1;ws.close();}
});
ws.addEventListener('close',()=>{clearTimeout(timer);if(!completed && !process.exitCode){console.error('FAIL: closed before password challenge');process.exitCode=1;}});
