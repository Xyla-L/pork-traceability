/**
 * AI 输出用的轻量 Markdown 渲染。
 *
 * 为什么不引第三方库：
 *   模型只会输出标题、表格、列表、粗体这四种结构，引一个 markdown-it 等于为了
 *   四种语法背上完整解析器和一堆 HTML 白名单规则。这里全部先做 HTML 转义，
 *   再拼固定标签，天然不会执行模型输出里的任何标签。
 *
 * ⚠️ 两个调用方（AI 监管简报、AI 问答抽屉）必须共用这一份：
 *   以前项目里同一套渲染写两遍，改一处漏一处，界面上就会出现「简报好看、问答难看」。
 */

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function inline(s: string): string {
  return escapeHtml(s).replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
}

/** 把模型输出的文本转成可安全 v-html 的 HTML 片段 */
export function renderMarkdownLite(src: string): string {
  if (!src) return ''

  const lines = src.split('\n')
  const out: string[] = []
  let i = 0

  while (i < lines.length) {
    const line = lines[i]

    // 表格：连续以 | 开头的行
    if (line.trim().startsWith('|')) {
      const rows: string[] = []
      while (i < lines.length && lines[i].trim().startsWith('|')) {
        rows.push(lines[i].trim())
        i++
      }
      const cells = rows.map((r) => r.replace(/^\||\|$/g, '').split('|').map((c) => c.trim()))
      // 丢掉 |---|---| 这种分隔行
      const body = cells.filter((row) => !row.every((c) => /^:?-{2,}:?$/.test(c)))
      if (body.length) {
        out.push('<table class="md-table"><thead><tr>')
        body[0].forEach((c) => out.push(`<th>${inline(c)}</th>`))
        out.push('</tr></thead><tbody>')
        body.slice(1).forEach((row) => {
          out.push('<tr>')
          row.forEach((c) => out.push(`<td>${inline(c)}</td>`))
          out.push('</tr>')
        })
        out.push('</tbody></table>')
      }
      continue
    }

    if (/^####\s+/.test(line)) out.push(`<h4>${inline(line.replace(/^####\s+/, ''))}</h4>`)
    else if (/^###\s+/.test(line)) out.push(`<h3>${inline(line.replace(/^###\s+/, ''))}</h3>`)
    else if (/^##\s+/.test(line)) out.push(`<h2>${inline(line.replace(/^##\s+/, ''))}</h2>`)
    else if (/^#\s+/.test(line)) out.push(`<h1>${inline(line.replace(/^#\s+/, ''))}</h1>`)
    else if (/^\s*[-*]\s+/.test(line)) out.push(`<li>${inline(line.replace(/^\s*[-*]\s+/, ''))}</li>`)
    else if (/^\s*\d+\.\s+/.test(line)) out.push(`<li>${inline(line.replace(/^\s*\d+\.\s+/, ''))}</li>`)
    else if (line.trim() === '') out.push('<div class="gap"></div>')
    else out.push(`<p>${inline(line)}</p>`)

    i++
  }

  // 把连续 li 包进 ul
  return out
    .join('')
    .replace(/(<li>[\s\S]*?<\/li>)(?!\s*<li>)/g, '<ul>$1</ul>')
}
