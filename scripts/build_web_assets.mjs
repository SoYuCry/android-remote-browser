import { build } from 'esbuild';
import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdir, readdir, rm } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { gzipSync } from 'node:zlib';

const root = fileURLToPath(new URL('../', import.meta.url));
const source = path.join(root, 'android-companion/app/src/main/assets/novnc');
const output = path.join(root, 'android-companion/app/build/generated/webAssets/novnc-fast');
await mkdir(output, { recursive: true });
// Android strips .gz from asset names, so store the precompressed variant as .bin.
await rm(path.join(output, 'ui.bundle.js.gz'), {force:true});
await build({ entryPoints: [path.join(source, 'app/ui.js')], bundle: true, minify: true,
  format: 'esm', target: ['safari15'], legalComments: 'eof', outfile: path.join(output, 'ui.bundle.js'),
  plugins: [{name:'cache-translations', setup(builder) {
    builder.onLoad({filter:/[\\/]app[\\/]ui\.js$/}, async args=>({
      contents:(await readFile(args.path,'utf8')).replace("fetch('app/locale/'", "fetch('/static/REMOTE_ASSET_REVISION/app/locale/'"), loader:'js'
    }));
  }}] });
const hash = createHash('sha256');
async function hashTree(directory) {
  for (const entry of (await readdir(directory, { withFileTypes: true })).sort((a,b)=>a.name.localeCompare(b.name))) {
    const filename = path.join(directory, entry.name);
    if (entry.isDirectory()) await hashTree(filename);
    else { hash.update(path.relative(source, filename).replaceAll('\\', '/')); hash.update(await readFile(filename)); }
  }
}
await hashTree(source);
hash.update(await readFile(path.join(output, 'ui.bundle.js')));
hash.update(await readFile(fileURLToPath(import.meta.url)));
const version = hash.digest('hex').slice(0, 20);
const bundle = (await readFile(path.join(output, 'ui.bundle.js'), 'utf8')).replaceAll('REMOTE_ASSET_REVISION', version);
await writeFile(path.join(output, 'ui.bundle.js'), bundle);
await writeFile(path.join(output, 'ui.bundle.gzip.bin'), gzipSync(bundle, {level:9}));
const html = (await readFile(path.join(source, 'vnc.html'), 'utf8'))
  .replace('src="app/ui.js"', 'src="app/ui.bundle.js"')
  .replace(/\b(src|href)="((?:app|core|vendor)\/[^"<>]+)"/g, `$1="/static/${version}/$2"`);
await writeFile(path.join(output, 'vnc.html'), html);
await writeFile(path.join(output, 'version.txt'), version);
console.log(`Built noVNC bundle and immutable asset URLs: ${version}`);
