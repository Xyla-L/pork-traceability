const BASES = [['gateway', 'http://127.0.0.1:8080/api/v1'], ['nginx', 'http://127.0.0.1/api/v1']];
const paths = [
  '/slaughter/entries?pageNum=1&pageSize=5',
  '/slaughter/inspections?pageNum=1&pageSize=5',
  '/slaughter/stamps?pageNum=1&pageSize=5',
  '/slaughter/ractopamine?pageNum=1&pageSize=5',
  '/slaughter/carcasses?pageNum=1&pageSize=5',
  '/breeding/pigs?pageNum=1&pageSize=5',
];
let token = null;
async function login(base) {
  const r = await fetch(base + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: '123456' }) });
  const j = await r.json().catch(() => ({}));
  token = j.data?.token || j.data?.accessToken || null;
  return { status: r.status, token: !!token, msg: j.message };
}
(async () => {
  for (const [label, base] of BASES) {
    const l = await login(base);
    console.log(`[${label}] login: HTTP ${l.status} token=${l.token} ${l.msg || ''}`);
    if (!token) continue;
    for (const p of paths) {
      try {
        const r = await fetch(base + p, { headers: { Authorization: `Bearer ${token}` } });
        const text = await r.text();
        let summary = text.slice(0, 120).replace(/\s+/g, ' ');
        try { const j = JSON.parse(text); summary = `code=${j.code} total=${j.data?.total ?? j.data?.totalElements ?? '?'} msg=${j.message || ''}`; } catch {}
        console.log(`  ${p} -> HTTP ${r.status} ${summary}`);
      } catch (e) { console.log(`  ${p} -> ERR ${e.message}`); }
    }
  }
})();
