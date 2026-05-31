package com.ruoyi.system.util;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 将 Java 浮点列表格式化为 pgvector 字面量 {@code [x,y,...]}。
 */
public final class VectorLiteralUtil
{
    private VectorLiteralUtil()
    {
    }

    public static String toLiteral(List<Double> embedding)
    {
        if (embedding == null || embedding.isEmpty())
        {
            return null;
        }
        return "[" + embedding.stream()
                .map(v -> String.format(Locale.US, "%.8f", v))
                .collect(Collectors.joining(",")) + "]";
    }
}
