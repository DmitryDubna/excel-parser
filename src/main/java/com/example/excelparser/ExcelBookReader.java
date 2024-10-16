package com.example.excelparser;

import com.ibm.icu.text.Transliterator;
import lombok.Getter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ExcelBookReader {
    private static final Transliterator TRANSLITERATOR = Transliterator.getInstance("Russian-Latin/BGN");
    @Getter
    private final XSSFWorkbook workbook;
    private final FormulaEvaluator formulaEvaluator;

    {
        IOUtils.setByteArrayMaxOverride(1000000000);
    }

    public ExcelBookReader(InputStream inputStream) throws IOException {
        workbook = new XSSFWorkbook(inputStream);
        formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
    }

    /// Формирует коллекцию типов данных по индексу строки заголовка
    public Map<String, String> getPostgresTypes(Sheet sheet, int dataIndex, int headerIndex) {
        var result = new LinkedHashMap<String, String>();

        sheet.getRow(headerIndex).forEach(cell -> {
            int column = cell.getAddress().getColumn();
            String fieldName = TRANSLITERATOR
                    .transliterate(cell.getStringCellValue())
                    .strip()
                    .replaceAll("\\W+", "_");
            String fieldType = toPostgresType(sheet.getRow(dataIndex).getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
            result.put(fieldName, fieldType);
        });
        return result;
    }

    /// Формирует коллекцию типов данных по списку имен колонок
    public Map<String, String> getPostgresTypes(Sheet sheet, int dataIndex, List<String> headerNames) {
        var result = new LinkedHashMap<String, String>();

        for (int i = 0; i < headerNames.size(); i++) {
            String fieldName = headerNames.get(i)
                    .strip()
                    .replaceAll("\\W+", "_");;
            String fieldType = toPostgresType(sheet.getRow(dataIndex).getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
            result.put(fieldName, fieldType);
        }
        return result;
    }

    private String toPostgresType(Cell cell) {
        switch (cell.getCellType()) {
            case NUMERIC -> {
                return DateUtil.isCellDateFormatted(cell) ? "TIMESTAMP" : "DOUBLE PRECISION";
            }
            case BOOLEAN -> {
                return "BOOLEAN";
            }
            default -> {
                return "TEXT";
            }
        }
    }

    public String toPostgresTypesString(Map<String, String> postgresTypes) {
        return postgresTypes.entrySet()
                .stream()
                .map(entry -> "%s %s".formatted(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    public String toPostgresTableValues(Sheet sheet, int columnCount, int rowFrom) {
        var result = new ArrayList<String>();

        for (int i = rowFrom; i <= sheet.getLastRowNum(); i++) {
            String rowValues = toPostgresRowValues(sheet.getRow(i), columnCount);
            result.add(rowValues);
        }
        return result.stream().collect(Collectors.joining(", ", "(", ")"));
    }

    private String toPostgresRowValues(Row row, int columnCount) {
        var values = new ArrayList<String>();

        for (int i = 0; i < columnCount; i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            values.add(Objects.isNull(cell) ? "null" : "'%s'".formatted(toValue(cell)));
        }
        return String.join(", ", values);
    }

    private String toValue(Cell cell) {
        switch (cell.getCellType()) {
            case _NONE -> {
                return "NONE";
            }
            case NUMERIC -> {
                return DateUtil.isCellDateFormatted(cell)
                        ? cell.getLocalDateTimeCellValue().format(DateTimeFormatter.ISO_DATE)
                        : Double.toString(cell.getNumericCellValue());
            }
            case STRING -> {
                return cell.getStringCellValue();
            }
            case FORMULA -> {
                Optional<Object> value = evaluateFormula(cell);
                return value.map(String::valueOf).orElse("null");
            }
            case BLANK -> {
                return "";
            }
            case BOOLEAN -> {
                return Boolean.toString(cell.getBooleanCellValue());
            }
            case ERROR -> {
                return Byte.toString(cell.getErrorCellValue());
            }
            default -> {
                return "-";
            }
        }
    }

    private Optional<Object> evaluateFormula(Cell cell) {
        CellType cellType = formulaEvaluator.evaluateFormulaCell(cell);

        switch (cellType) {
            case NUMERIC -> {
                return Optional.of(cell.getNumericCellValue());
            }
            case STRING -> {
                return Optional.ofNullable(cell.getStringCellValue());
            }
            case BOOLEAN -> {
                return Optional.of(cell.getBooleanCellValue());
            }
            default -> {
                break;
            }
        }
        return Optional.empty();
    }
}
