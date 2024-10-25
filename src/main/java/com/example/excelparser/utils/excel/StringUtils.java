package com.example.excelparser.utils.excel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StringUtils {

    public static List<String> toStringList(String s, String delimiter) {
        if (s.isBlank())
            return List.of();

        return Arrays.stream(s.split(delimiter))
                .map(String::trim)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
