package com.example.excelparser.utils.excel;

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

    public Optional<String> getFirstSheetName() {
        return (workbook.getNumberOfSheets() > 0)
                ? Optional.of(workbook.getSheetAt(0).getSheetName())
                : Optional.empty();
    }

    /// Формирует коллекцию типов данных по индексу строки заголовка
//    public Map<String, String> getPostgresTypes(Sheet sheet, int dataIndex, int headerIndex) {
//        var result = new LinkedHashMap<String, String>();
//
//        sheet.getRow(headerIndex).forEach(cell -> {
//            int column = cell.getAddress().getColumn();
//            String fieldName = TRANSLITERATOR
//                    .transliterate(cell.getStringCellValue())
//                    .strip()
//                    .replaceAll("\\W+", "_");
//            String fieldType = toPostgresType(sheet.getRow(dataIndex).getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
//            result.put(fieldName, fieldType);
//        });
//        return result;
//    }

    public Map<String, String> getPostgresTypesByHeaderIndex(Sheet sheet, int dataRowIndex, int headerIndex) {
        Row row = sheet.getRow(headerIndex);
        return getPostgresTypesByHeaderIndex(sheet, dataRowIndex, headerIndex, row.getFirstCellNum(), row.getLastCellNum());
    }


    public Map<String, String> getPostgresTypesByHeaderIndex(
            Sheet sheet,
            int dataRowIndex,
            int headerIndex,
            int fromColumnIndex,
            int toColumnIndex) {
        var result = new LinkedHashMap<String, String>();

        Row row = sheet.getRow(headerIndex);
        int fromIndex = (fromColumnIndex >= row.getFirstCellNum()) ? fromColumnIndex : row.getFirstCellNum();
        int toIndex = (toColumnIndex < row.getLastCellNum()) ? toColumnIndex : row.getLastCellNum() - 1;

        for (int i = fromIndex; i <= toIndex; i++) {
            Cell cell = row.getCell(i);
            int column = cell.getAddress().getColumn();
            String fieldName = TRANSLITERATOR
                    .transliterate(cell.getStringCellValue())
                    .strip()
                    .replaceAll("\\W+", "_");
            String fieldType = toPostgresType(sheet.getRow(dataRowIndex).getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
            result.put(fieldName, fieldType);
        };
        return result;
    }

    /// Формирует коллекцию типов данных по списку имен колонок
//    public Map<String, String> getPostgresTypes(Sheet sheet, int dataIndex, List<String> headerNames) {
//        var result = new LinkedHashMap<String, String>();
//
//        for (int i = 0; i < headerNames.size(); i++) {
//            String fieldName = headerNames.get(i)
//                    .strip()
//                    .replaceAll("\\W+", "_");;
//            String fieldType = toPostgresType(sheet.getRow(dataIndex).getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
//            result.put(fieldName, fieldType);
//        }
//        return result;
//    }

    public Map<String, String> getPostgresTypesByFieldNames(Sheet sheet, int dataRowIndex, List<String> fieldNames) {
        return getPostgresTypesByFieldNames(sheet, dataRowIndex, fieldNames, 0, fieldNames.size() - 1);
    }

    public Map<String, String> getPostgresTypesByFieldNames(
            Sheet sheet,
            int dataRowIndex,
            List<String> fieldNames,
            int fromColumnIndex,
            int toColumnIndex) {
        var result = new LinkedHashMap<String, String>();

        int fromIndex = (fromColumnIndex >= 0) ? fromColumnIndex : 0;
        int toIndex = (toColumnIndex < fieldNames.size()) ? toColumnIndex : fieldNames.size() - 1;

        for (int i = fromIndex; i <= toIndex; i++) {
            String fieldName = fieldNames.get(i)
                    .strip()
                    .replaceAll("\\W+", "_");;
            String fieldType = toPostgresType(sheet.getRow(dataRowIndex).getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
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

    public String toPostgresTableValues(Sheet sheet, List<String> fieldTypes, int rowFrom) {
        var result = new ArrayList<String>();

        for (int i = rowFrom; i <= sheet.getLastRowNum(); i++) {
            String rowValues = toPostgresRowValues(sheet.getRow(i), fieldTypes);
            result.add(rowValues);
        }
        return String.join(", ", result);
    }

    private String toPostgresRowValues(Row row, List<String> fieldTypes) {
        var values = new ArrayList<String>();

        for (int i = 0; i < fieldTypes.size(); i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            values.add(Objects.isNull(cell) ? "null" : toPostgresString(cell, fieldTypes.get(i)));
        }
        return values.stream().collect(Collectors.joining(", ", "(", ")"));
    }

    private String toPostgresString(Cell cell, String fieldType) {
        return Optional.ofNullable(toValue(cell, fieldType))
                .map(value -> "'%s'".formatted(value))
                .orElse("null");
    }

    private String toValue(Cell cell, String fieldType) {
        switch (cell.getCellType()) {
            case NUMERIC -> {
                // дата
                if (DateUtil.isCellDateFormatted(cell)) {
                    return (fieldType.equals("TIMESTAMP"))
                            ? cell.getLocalDateTimeCellValue().format(DateTimeFormatter.ISO_DATE)
                            : null;
                }
                // число
                return (fieldType.equals("DOUBLE PRECISION"))
                        ? Double.valueOf(cell.getNumericCellValue()).toString()
                        : null;
            }
            case BOOLEAN -> {
                return (fieldType.equals("BOOLEAN"))
                        ? Boolean.toString(cell.getBooleanCellValue())
                        : null;
            }
            case FORMULA -> {
                Optional<Object> value = evaluateFormula(cell);
                return value.map(String::valueOf).orElse("null");
            }
            case STRING -> {
                return (fieldType.equals("TEXT"))
                        ? cell.getStringCellValue()
                        : null;
            }
            default -> {
                return null;
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
