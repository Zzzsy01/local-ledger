import { defineConfig } from 'vite';
import { readFileSync } from 'node:fs';

const csp = readFileSync('public/_headers', 'utf8').match(/Content-Security-Policy: (.+)/)[1];
export default defineConfig({ preview: { headers: { 'Content-Security-Policy': csp, 'X-Content-Type-Options': 'nosniff' } } });
