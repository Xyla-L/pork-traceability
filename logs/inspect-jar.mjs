import fs from 'fs'
import zlib from 'zlib'

const buf = fs.readFileSync(process.argv[2])
let eocd = -1
for (let i = buf.length - 22; i >= 0 && i > Math.max(0, buf.length - 70000); i--) {
  if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break }
}
if (eocd < 0) throw new Error('不是合法的 zip/jar')
const cdCount = buf.readUInt16LE(eocd + 10)
let off = buf.readUInt32LE(eocd + 16)
const names = []
for (let n = 0; n < cdCount; n++) {
  const nameLen = buf.readUInt16LE(off + 28)
  const extraLen = buf.readUInt16LE(off + 30)
  const commentLen = buf.readUInt16LE(off + 32)
  const method = buf.readUInt16LE(off + 10)
  const compSize = buf.readUInt32LE(off + 20)
  const localOff = buf.readUInt32LE(off + 42)
  const name = buf.toString('utf8', off + 46, off + 46 + nameLen)
  names.push(name)
  if (/DeviceIngestService\.class$/.test(name)) {
    const lnameLen = buf.readUInt16LE(localOff + 26)
    const lextraLen = buf.readUInt16LE(localOff + 28)
    const dataStart = localOff + 30 + lnameLen + lextraLen
    const raw = buf.subarray(dataStart, dataStart + compSize)
    const content = method === 8 ? zlib.inflateRawSync(raw) : raw
    const text = content.toString('latin1')
    console.log('条目:', name, '| 解压后', content.length, '字节')
    console.log('含 ingest_duplicate_payload 常量:', text.includes('ingest_duplicate_payload'))
    console.log('含 PAYLOAD 级去重的 SQL 语义（duplicate 相关常量）:',
      [...text.matchAll(/[\x20-\x7e]{5,}/g)].map((m) => m[0]).filter((s) => /duplicate/i.test(s)).slice(0, 8))
  }
  off += 46 + nameLen + extraLen + commentLen
}
console.log('\n条目总数:', names.length)
console.log('顶层结构样例:', names.filter((n) => n.split('/').length <= 2).slice(0, 8))
console.log('是否含 BOOT-INF:', names.some((n) => n.startsWith('BOOT-INF/')))
