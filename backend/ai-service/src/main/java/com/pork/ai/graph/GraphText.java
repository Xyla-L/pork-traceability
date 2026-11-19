package com.pork.ai.graph;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 检索用的轻量分词。
 *
 * <p>为什么不上 HanLP / jieba：</p>
 * <ul>
 *   <li>这个场景的语料只有「接口摘要 + 参数名」这么点字，词库分词反而会因为切错边界丢召回；</li>
 *   <li>中文用 <b>2-gram</b> 命中率更稳：「退货单」切成「退货 / 货单」，无论接口写的是
 *       「退货」还是「退货单查询」都能对上；</li>
 *   <li>不引依赖 = 打包不变大、启动不变慢，这个模块本来就没打算常驻重型 NLP。</li>
 * </ul>
 *
 * <p>英文走正常切词 + camelCase 拆分子词：接口方法名 {@code safeBuy} 拆成
 * {@code safe / buy}，用户说「安全购买」时靠同义词表把中文映射到英文就能对上。</p>
 */
public final class GraphText {

    private static final Pattern ASCII_WORD = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern CJK_RUN = Pattern.compile("[\\u4e00-\\u9fa5]+");
    /** 中文 2-gram：整段过长时不加入整段，避免把长句当成一个词 */
    private static final int MAX_CJK_PHRASE = 8;

    private GraphText() {
    }

    public static List<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();

        Matcher e = ASCII_WORD.matcher(text);
        while (e.find()) {
            String raw = e.group();
            String w = raw.toLowerCase(Locale.ROOT);
            out.add(w);
            // 英文复数归一：接口路径写 /warnings，用户和摘要里说的是 warning。
            // 加一条去掉词尾 s 的 token，索引和查询两侧都加，才能对上。
            if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) {
                out.add(w.substring(0, w.length() - 1));
            }
            if (w.length() > 4 || Character.isUpperCase(raw.charAt(0))) {
                out.addAll(splitCamel(raw));
            }
        }

        Matcher c = CJK_RUN.matcher(text);
        while (c.find()) {
            String run = c.group();
            if (run.length() == 1) {
                out.add(run);
                continue;
            }
            for (int i = 0; i + 2 <= run.length(); i++) {
                out.add(run.substring(i, i + 2));
            }
            if (run.length() <= MAX_CJK_PHRASE) {
                out.add(run);
            }
        }
        return new ArrayList<>(out);
    }

    /** safeBuy → [safe, buy]；已全小写的词原样返回自身，避免重复 */
    public static List<String> splitCamel(String s) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (Character.isUpperCase(ch) && cur.length() > 0) {
                parts.add(cur.toString().toLowerCase(Locale.ROOT));
                cur.setLength(0);
            }
            cur.append(Character.toLowerCase(ch));
        }
        if (cur.length() > 0) {
            parts.add(cur.toString());
        }
        return parts;
    }

    /** 从路径里取词：/distribution/batches/{id} → [distribution, batches]，跳过占位符 */
    public static List<String> pathTokens(String path) {
        List<String> out = new ArrayList<>();
        if (path == null || path.isBlank()) {
            return out;
        }
        for (String seg : path.split("/")) {
            if (seg.isBlank() || seg.startsWith("{") || seg.endsWith("}")) {
                continue;
            }
            out.addAll(tokens(seg.replace('-', ' ').replace('_', ' ')));
        }
        return out;
    }
}
