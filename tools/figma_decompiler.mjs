#!/usr/bin/env bun
import fs from 'fs';
import path from 'path';
import * as pako from 'pako';
import * as fzstd from 'fzstd';
import * as kiwi from 'kiwi-schema';
import * as zip from '@zip.js/zip.js';

const inputFig = process.argv[2] || '/home/artemmkr/Завантажене/Untitled (2).fig';
const outputDir = process.argv[3] || path.join(process.cwd(), 'build', 'figma_decompiled');

console.log('====================================================');
console.log('   Figma .fig Decompiler for Minar Client');
console.log('====================================================');
console.log(`Input:  ${inputFig}`);
console.log(`Output: ${outputDir}\n`);

if (!fs.existsSync(inputFig)) {
  console.error(`Error: File not found: ${inputFig}`);
  process.exit(1);
}

fs.mkdirSync(outputDir, { recursive: true });
const imagesDir = path.join(outputDir, 'images');
fs.mkdirSync(imagesDir, { recursive: true });

const fileBuffer = fs.readFileSync(inputFig);
const bytes = new Uint8Array(fileBuffer);

let canvasBytes = null;
let metaJson = null;
let thumbnailBytes = null;
const extractedImages = [];

if (String.fromCharCode(...bytes.slice(0, 2)) === 'PK') {
  console.log('[1/4] Unpacking .fig ZIP archive container...');
  const zipReader = new zip.ZipReader(new zip.Uint8ArrayReader(bytes));
  const entries = await zipReader.getEntries();
  for (const entry of entries) {
    if (entry.filename === 'canvas.fig') {
      canvasBytes = await entry.getData(new zip.Uint8ArrayWriter());
    } else if (entry.filename === 'meta.json') {
      const metaData = await entry.getData(new zip.Uint8ArrayWriter());
      metaJson = JSON.parse(new TextDecoder().decode(metaData));
      fs.writeFileSync(path.join(outputDir, 'meta.json'), JSON.stringify(metaJson, null, 2));
    } else if (entry.filename === 'thumbnail.png') {
      thumbnailBytes = await entry.getData(new zip.Uint8ArrayWriter());
      fs.writeFileSync(path.join(outputDir, 'thumbnail.png'), thumbnailBytes);
    } else if (entry.filename.startsWith('images/') && !entry.directory) {
      const imgBytes = await entry.getData(new zip.Uint8ArrayWriter());
      const hash = path.basename(entry.filename);
      const outImgPath = path.join(imagesDir, hash + '.png');
      fs.writeFileSync(outImgPath, imgBytes);
      extractedImages.push({ hash, size: imgBytes.length, path: outImgPath });
    }
  }
} else {
  console.log('[1/4] Direct .fig binary detected...');
  canvasBytes = bytes;
}

if (!canvasBytes) {
  console.error('Error: canvas.fig not found in file!');
  process.exit(1);
}

console.log(`[2/4] Decompressing Kiwi binary schema and scene data (${canvasBytes.length} bytes)...`);
const view = new DataView(canvasBytes.buffer);
const version = view.getUint32(8, true);
const schemaLen = view.getUint32(12, true);

const chunk0 = canvasBytes.slice(16, 16 + schemaLen);
const chunk1 = canvasBytes.slice(16 + schemaLen);

const uncompressChunk = (b) => {
  try {
    return pako.inflateRaw(b);
  } catch {
    if (b[0] !== 0x28 || b[1] !== 0xb5) {
      return fzstd.decompress(b.slice(4));
    }
    return fzstd.decompress(b);
  }
};

const encodedSchema = uncompressChunk(chunk0);
const encodedData = uncompressChunk(chunk1);

console.log(`      Schema: ${encodedSchema.length} bytes uncompressed`);
console.log(`      Data:   ${encodedData.length} bytes uncompressed`);

console.log('[3/4] Compiling Kiwi schema and decoding AST nodes...');
const compiledSchema = kiwi.compileSchema(kiwi.decodeBinarySchema(encodedSchema));
const { nodeChanges, blobs } = compiledSchema.decodeMessage(encodedData);

const nodes = [];
const colorToHex = (c) => {
  if (!c) return null;
  const r = Math.round((c.r || 0) * 255).toString(16).padStart(2, '0');
  const g = Math.round((c.g || 0) * 255).toString(16).padStart(2, '0');
  const b = Math.round((c.b || 0) * 255).toString(16).padStart(2, '0');
  return `#${r}${g}${b}`;
};

let summaryText = '=== DECOMPILED FIGMA NODES SUMMARY ===\n\n';

for (const n of nodeChanges) {
  if (!n.name || n.type === 'DOCUMENT' || n.type === 'CANVAS') continue;

  const nodeInfo = {
    id: `${n.guid.sessionID}:${n.guid.localID}`,
    parentId: n.parentIndex ? `${n.parentIndex.guid.sessionID}:${n.parentIndex.guid.localID}` : null,
    name: n.name,
    type: n.type,
    x: n.transform ? Math.round(n.transform.m02) : null,
    y: n.transform ? Math.round(n.transform.m12) : null,
    width: n.size ? Math.round(n.size.x) : null,
    height: n.size ? Math.round(n.size.y) : null,
    cornerRadius: n.cornerRadius || null,
    characters: n.characters || null,
    fontSize: n.fontSize || null,
    fontFamily: n.fontName?.family || null,
    fontStyle: n.fontName?.style || null,
    fills: n.fillPaints?.map(f => ({
      type: f.type,
      hex: colorToHex(f.color),
      opacity: f.opacity ?? 1,
      imageHash: f.image?.hash ? Buffer.from(f.image.hash).toString('hex') : null
    })) || []
  };

  nodes.push(nodeInfo);

  const fillDesc = nodeInfo.fills.map(f => f.hex || f.imageHash || f.type).join(', ');
  summaryText += `[${nodeInfo.type}] "${nodeInfo.name}"\n`;
  summaryText += `  Pos: (${nodeInfo.x}, ${nodeInfo.y})  Size: ${nodeInfo.width}x${nodeInfo.height}  Radius: ${nodeInfo.cornerRadius ?? 0}\n`;
  if (nodeInfo.characters) {
    summaryText += `  Text: "${nodeInfo.characters}" (${nodeInfo.fontFamily} ${nodeInfo.fontSize}px)\n`;
  }
  if (fillDesc) {
    summaryText += `  Fills: ${fillDesc}\n`;
  }
  summaryText += '\n';
}

fs.writeFileSync(path.join(outputDir, 'decompiled_nodes.json'), JSON.stringify(nodes, null, 2));
fs.writeFileSync(path.join(outputDir, 'decompiled_summary.txt'), summaryText);

console.log(`[4/4] Extracted ${extractedImages.length} images, decoded ${nodes.length} UI nodes.`);
console.log(`      Saved JSON:    ${path.join(outputDir, 'decompiled_nodes.json')}`);
console.log(`      Saved Summary: ${path.join(outputDir, 'decompiled_summary.txt')}`);
console.log('\nDecompilation finished successfully!');
