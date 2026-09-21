let token = null;
async function login(base) {
  const r = await fetch(base + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: '123456' }) });
  const j = await r.json().catch(() => ({}));
  token = j.data?.token || j.data?.accessToken || null;
}
const base = 'http://127.0.0.1/api/v1';
(async () => {
  await login(base);
  for (const p of ['/slaughter/slaughterhouses', '/distribution/carcasses?pageNum=1&pageSize=5', '/distribution/splits?pageNum=1&pageSize=5', '/distribution/transports?pageNum=1&pageSize=5']) {
    try {
      const r = await fetch(base + p, { headers: { Authorization: `Bearer ${token}` } });
      const text = await r.text();
      let s = text.slice(0, 150).replace(/\s+/g, ' ');
      try { const j = JSON.parse(text); s = `code=${j.code} total=${j.data?.total ?? '?'} msg=${(j.message||'').slice(0,80)}`; } catch {}
      console.log(`${p} -> HTTP ${r.status} ${s}`);
    } catch (e) { console.log(`${p} -> ERR ${e.message}`); }
  }
})();
