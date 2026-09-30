import { readdir, readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import path from 'node:path';

const files = [];
async function walk(dir) {
  for (const item of await readdir(dir, { withFileTypes: true })) {
    const full = path.join(dir, item.name);
    if (item.isDirectory()) await walk(full);
    else if (!['sw.js', '_headers'].includes(item.name)) files.push(full);
  }
}
await walk('dist');
const hash = createHash('sha256');
for (const file of files.sort()) hash.update(await readFile(file));
const urls = files.map((file) => './' + path.relative('dist', file).replaceAll('\\', '/'));
const template = await readFile('src/service-worker.js', 'utf8');
hash.update(template);
await writeFile('dist/sw.js', template.replace('__CACHE_VERSION__', hash.digest('hex').slice(0, 16)).replace('__ASSET_URLS__', JSON.stringify(urls)));
console.log(`Offline cache includes ${urls.length} files, including all OCR assets.`);
