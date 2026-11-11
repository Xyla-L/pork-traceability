import { onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'

/**
 * 顶部通知跳过来时会带 `?keyword=<业务编号>`，页面据此把编号填进自己的搜索框再查一次 ——
 * 「跳到对应页面」之后还得是那条记录，否则等于让人在列表里自己翻。
 * <p>
 * 监听而不只在 onMounted 读一次：同一类通知连着点两条，路由不变、只有 query 变，
 * 只读挂载那一次的话，第二条点了没反应。
 */
export function useKeywordQuery(apply: (keyword: string) => void) {
  const route = useRoute()

  const run = () => {
    const keyword = typeof route.query.keyword === 'string' ? route.query.keyword.trim() : ''
    if (keyword) apply(keyword)
  }

  onMounted(run)
  watch(() => route.query.keyword, run)
}
