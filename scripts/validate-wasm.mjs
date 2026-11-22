import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';

const directory = process.argv[2];
if (!directory) throw new Error('Usage: node validate-wasm.mjs <linked-wasm-directory>');

const files = (await readdir(directory)).filter((name) => name.endsWith('.wasm'));
if (files.length === 0) throw new Error(`No linked Wasm binaries found in ${directory}`);

for (const file of files) {
    // Compile rather than instantiate: checks GC/reference types without browser
    // imports or running account/storage code, and preserves the engine's error.
    await WebAssembly.compile(await readFile(join(directory, file)));
    console.log(`Validated ${file}`);
}
