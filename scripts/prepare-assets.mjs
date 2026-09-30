import { mkdir, copyFile, readdir, writeFile, readFile } from 'node:fs/promises';
import path from 'node:path';

const root = path.resolve('public/ocr');
await mkdir(path.join(root, 'core'), { recursive: true });
await mkdir(path.join(root, 'lang'), { recursive: true });
await copyFile('node_modules/tesseract.js/dist/worker.min.js', path.join(root, 'worker.min.js'));
// OEM is fixed to LSTM: include SIMD, relaxed SIMD and the non-SIMD build.
for (const name of await readdir('node_modules/tesseract.js-core')) {
  if (name.endsWith('-lstm.wasm.js')) await copyFile(path.join('node_modules/tesseract.js-core', name), path.join(root, 'core', name));
}
await copyFile('node_modules/@tesseract.js-data/chi_sim/4.0.0_best_int/chi_sim.traineddata.gz', path.join(root, 'lang/chi_sim.traineddata.gz'));
await copyFile('node_modules/@tesseract.js-data/eng/4.0.0_best_int/eng.traineddata.gz', path.join(root, 'lang/eng.traineddata.gz'));
const licenses = await Promise.all(['tesseract.js', 'tesseract.js-core', 'pdf-lib'].map(async (pkg) => {
  const dir = `node_modules/${pkg}`;
  const name = (await readdir(dir)).find((n) => /^LICENSE(?:\.md|\.txt)?$/i.test(n));
  return `${pkg}\n${await readFile(`${dir}/${name}`, 'utf8')}`;
}));
await writeFile('public/THIRD-PARTY-LICENSES.txt', licenses.join('\n\n'));

console.log('Local OCR models and browser cores ready.');
