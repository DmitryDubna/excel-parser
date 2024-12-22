package com.example.excelparser.utils.excel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StringUtils {

    public static List<String> toStringList(@NonNull String s, String delimiter) {
        if (s.isBlank())
            return List.of();

        return Arrays.stream(s.split(delimiter))
                .map(String::trim)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<Integer> toIntList(@NonNull String s,
                                          String delimiter,
                                          Function<String, Integer> mapper) {
        List<String> strings = StringUtils.toStringList(s, delimiter);
        return (strings.isEmpty())
                ? List.of()
                : strings.stream()
                    .map(mapper)
                    .collect(Collectors.toList());
    }
}
