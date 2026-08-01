package com.ruoyi.system.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.utils.StringUtils;

/**
 * 视频导入行为日志前的事件过滤：
 * <ul>
 *   <li>同视频先按 trackId 分组，跨 track 仅用人脸相似度合并同人（不用体态）</li>
 *   <li>某人若有 enter/exit，则丢弃其全部 pass（路过视为误判）</li>
 *   <li>enter/exit 全部保留，不用 AI 人数截断，也不用 pass 凑数</li>
 *   <li>pass 直接保留，不以距门线远近作为过滤或排序条件</li>
 * </ul>
 */
public final class VideoEventImportFilter
{
    private VideoEventImportFilter()
    {
    }

    public static final class FilterResult
    {
        private final List<JsonNode> events;
        private final int rawCount;
        private final int droppedPassCount;
        private final int personGroupCount;

        public FilterResult(List<JsonNode> events, int rawCount, int droppedPassCount, int personGroupCount)
        {
            this.events = events;
            this.rawCount = rawCount;
            this.droppedPassCount = droppedPassCount;
            this.personGroupCount = personGroupCount;
        }

        public List<JsonNode> getEvents()
        {
            return events;
        }

        public int getRawCount()
        {
            return rawCount;
        }

        public int getDroppedPassCount()
        {
            return droppedPassCount;
        }

        public int getPersonGroupCount()
        {
            return personGroupCount;
        }
    }

    public static FilterResult filter(List<JsonNode> rawEvents,
            Map<Integer, List<Double>> faceEmbeddings,
            double faceMatchThreshold)
    {
        return filter(rawEvents, faceEmbeddings, faceMatchThreshold, null);
    }

    public static FilterResult filter(List<JsonNode> rawEvents,
            Map<Integer, List<Double>> faceEmbeddings,
            double faceMatchThreshold,
            Integer aiPersonCount)
    {
        if (rawEvents == null || rawEvents.isEmpty())
        {
            return new FilterResult(Collections.emptyList(), 0, 0, 0);
        }

        List<JsonNode> events = new ArrayList<>();
        for (JsonNode event : rawEvents)
        {
            if (event == null || event.isNull())
            {
                continue;
            }
            String type = event.path("eventType").asText("");
            if ("enter".equals(type) || "exit".equals(type) || "pass".equals(type))
            {
                events.add(event);
            }
        }
        int rawCount = events.size();
        if (events.isEmpty())
        {
            return new FilterResult(Collections.emptyList(), 0, 0, 0);
        }

        Set<Integer> trackIds = new HashSet<>();
        for (JsonNode event : events)
        {
            trackIds.add(event.path("trackId").asInt(0));
        }
        List<Integer> tracks = new ArrayList<>(trackIds);
        Collections.sort(tracks);

        UnionFind uf = new UnionFind(tracks);
        Map<Integer, List<Double>> faces = faceEmbeddings == null ? Map.of() : faceEmbeddings;
        // 体态相似度不参与跨 track 同人合并（同行两人易误并）；仅用人脸
        double faceTh = faceMatchThreshold > 0 ? faceMatchThreshold : 0.35;

        for (int i = 0; i < tracks.size(); i++)
        {
            for (int j = i + 1; j < tracks.size(); j++)
            {
                int a = tracks.get(i);
                int b = tracks.get(j);
                if (similar(faces.get(a), faces.get(b), faceTh))
                {
                    uf.union(a, b);
                }
            }
        }

        Map<Integer, Boolean> groupHasDoorEvent = new HashMap<>();
        for (JsonNode event : events)
        {
            int tid = event.path("trackId").asInt(0);
            int root = uf.find(tid);
            String type = event.path("eventType").asText("");
            if ("enter".equals(type) || "exit".equals(type))
            {
                groupHasDoorEvent.put(root, true);
            }
            else
            {
                groupHasDoorEvent.putIfAbsent(root, false);
            }
        }

        List<JsonNode> kept = new ArrayList<>();
        int droppedPass = 0;
        for (JsonNode event : events)
        {
            String type = event.path("eventType").asText("");
            int tid = event.path("trackId").asInt(0);
            int root = uf.find(tid);
            boolean door = Boolean.TRUE.equals(groupHasDoorEvent.get(root));
            if ("pass".equals(type) && door)
            {
                droppedPass++;
                continue;
            }
            kept.add(event);
        }

        kept.sort(Comparator
                .comparingInt((JsonNode e) -> eventPriority(e.path("eventType").asText("")))
                .thenComparing(VideoEventImportFilter::eventScore, Comparator.reverseOrder())
                .thenComparingDouble(e -> e.path("timeSec").asDouble(0.0)));

        // AI 人数截断：先对 pass 每人只留最高分一条（去重同人），再按 aiPersonCount 截断
        if (aiPersonCount != null && aiPersonCount > 0)
        {
            // Step 1: pass 事件按 person group 去重，每组只留得分最高的一条
            List<JsonNode> doorEvents = new ArrayList<>();
            List<JsonNode> passEvents = new ArrayList<>();
            for (JsonNode e : kept)
            {
                String t = e.path("eventType").asText("");
                if ("enter".equals(t) || "exit".equals(t))
                {
                    doorEvents.add(e);
                }
                else
                {
                    passEvents.add(e);
                }
            }
            // 每个 person group 只保留得分最高的 pass
            Map<Integer, JsonNode> bestPassPerGroup = new LinkedHashMap<>();
            for (JsonNode e : passEvents)
            {
                int tid = e.path("trackId").asInt(0);
                int groupRoot = uf.find(tid);
                JsonNode existing = bestPassPerGroup.get(groupRoot);
                if (existing == null || eventScore(e) > eventScore(existing))
                {
                    bestPassPerGroup.put(groupRoot, e);
                }
            }
            // 按得分排序去重后的 pass
            List<JsonNode> dedupedPass = new ArrayList<>(bestPassPerGroup.values());
            dedupedPass.sort(Comparator.comparing(VideoEventImportFilter::eventScore, Comparator.reverseOrder()));

            // Step 2: 按 aiPersonCount 截断 pass（enter/exit 全部保留）
            int passSlots = Math.max(0, aiPersonCount - doorEvents.size());
            if (dedupedPass.size() > passSlots)
            {
                droppedPass += (passEvents.size() - passSlots);
                dedupedPass = dedupedPass.subList(0, passSlots);
            }
            else
            {
                droppedPass += (passEvents.size() - dedupedPass.size());
            }

            kept = new ArrayList<>(doorEvents);
            kept.addAll(dedupedPass);
            // 重新排序
            kept.sort(Comparator
                    .comparingInt((JsonNode e) -> eventPriority(e.path("eventType").asText("")))
                    .thenComparing(VideoEventImportFilter::eventScore, Comparator.reverseOrder())
                    .thenComparingDouble(e -> e.path("timeSec").asDouble(0.0)));
        }

        return new FilterResult(kept, rawCount, droppedPass, uf.componentCount());
    }

    private static int eventPriority(String type)
    {
        if ("enter".equals(type) || "exit".equals(type))
        {
            return 0;
        }
        if ("pass".equals(type))
        {
            return 1;
        }
        return 2;
    }

    /** 置信度 + 脸质量，越大越好（路过不看距门线远近） */
    private static double eventScore(JsonNode event)
    {
        double conf = event.path("confidence").asDouble(0.0);
        double face = event.path("bestFaceScore").asDouble(0.0);
        if (face < 0)
        {
            face = 0.0;
        }
        return conf * 0.55 + face * 0.45;
    }

    private static boolean similar(List<Double> a, List<Double> b, double threshold)
    {
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.size() != b.size())
        {
            return false;
        }
        return cosine(a, b) >= threshold;
    }

    private static double cosine(List<Double> a, List<Double> b)
    {
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.size(); i++)
        {
            double x = a.get(i) == null ? 0.0 : a.get(i);
            double y = b.get(i) == null ? 0.0 : b.get(i);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na <= 1e-12 || nb <= 1e-12)
        {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static final class UnionFind
    {
        private final Map<Integer, Integer> parent = new LinkedHashMap<>();

        UnionFind(List<Integer> ids)
        {
            for (Integer id : ids)
            {
                parent.put(id, id);
            }
        }

        int find(int x)
        {
            Integer p = parent.get(x);
            if (p == null)
            {
                parent.put(x, x);
                return x;
            }
            if (p != x)
            {
                int root = find(p);
                parent.put(x, root);
                return root;
            }
            return x;
        }

        void union(int a, int b)
        {
            int ra = find(a);
            int rb = find(b);
            if (ra != rb)
            {
                parent.put(ra, rb);
            }
        }

        int componentCount()
        {
            Set<Integer> roots = new HashSet<>();
            for (Integer id : parent.keySet())
            {
                roots.add(find(id));
            }
            return roots.size();
        }
    }

    public static String readAiSummary(JsonNode root)
    {
        if (root == null)
        {
            return "";
        }
        JsonNode arr = root.path("aiAnalysis");
        if (!arr.isArray())
        {
            return "";
        }
        for (JsonNode item : arr)
        {
            if (item == null || item.isNull())
            {
                continue;
            }
            String summary = item.path("summary").asText("");
            if (!StringUtils.isEmpty(summary))
            {
                return summary;
            }
        }
        return "";
    }

    public static Integer readAiPersonCount(JsonNode root)
    {
        if (root == null)
        {
            return null;
        }
        JsonNode arr = root.path("aiAnalysis");
        if (!arr.isArray())
        {
            return null;
        }
        for (JsonNode item : arr)
        {
            if (item != null && item.has("personCount") && !item.get("personCount").isNull())
            {
                return item.get("personCount").asInt();
            }
        }
        return null;
    }
}
