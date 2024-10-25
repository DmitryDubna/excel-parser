package com.example.excelparser.utils.excel;

import com.ibm.icu.text.Transliterator;
import lombok.Getter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ValueRange;
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

    // Проверяет, не выходит ли индекс строки за пределы данных листа
    private boolean checkRowIndexValid(Sheet sheet, int rowIndex) {
        return ValueRange.of(sheet.getFirstRowNum(), sheet.getLastRowNum()).isValidValue(rowIndex);
    }

    // Проверяет, не выходит ли диапазон индексов колонок за пределы размера списка полей
    private boolean checkColumnIndexesValid(int columnFromIndex, int columnToIndex, int fieldCount) {
        return (columnToIndex - columnFromIndex + 1) <= fieldCount;
    }

    // Формирует коллекцию типов данных по индексу строки заголовка
    public Map<String, String> getPostgresTypesByHeaderIndex(Sheet sheet,
                                                             int dataRowIndex,
                                                             int headerRowIndex,
                                                             Optional<Integer> columnFromIndex,
                                                             Optional<Integer> columnToIndex) {
        var result = new LinkedHashMap<String, String>();

        // проверка индексов строк
        if (!checkRowIndexValid(sheet, dataRowIndex) || ! checkRowIndexValid(sheet, headerRowIndex))
            return result;

        // если индексы колонок не заданы, берем диапазон ячеек строки заголовка
        Row headerRow = sheet.getRow(headerRowIndex);
        int indexFrom = columnFromIndex.orElseGet(() -> Integer.valueOf(headerRow.getFirstCellNum()));
        int indexTo = columnToIndex.orElseGet(() -> Integer.valueOf(headerRow.getLastCellNum() - 1));

        // проверка индексов колонок
        if (!checkColumnIndexesValid(indexFrom, indexTo, headerRow.getPhysicalNumberOfCells()))
            return result;

        for (int i = indexFrom; i <= indexTo; i++) {
            Cell cell = headerRow.getCell(i);
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

    // Формирует коллекцию типов данных по списку имен колонок
    public Map<String, String> getPostgresTypesByFieldNames(Sheet sheet,
                                                            int dataRowIndex,
                                                            List<String> fieldNames,
                                                            Optional<Integer> columnFromIndex,
                                                            Optional<Integer> columnToIndex) {
        var result = new LinkedHashMap<String, String>();

        // проверка индексов строк
        if (!checkRowIndexValid(sheet, dataRowIndex))
            return result;

        // если индексы колонок не заданы, берем диапазон списка имен полей
        int indexFrom = columnFromIndex.orElse(0);
        int indexTo = columnToIndex.orElseGet(() -> fieldNames.size() - 1);

        // проверка индексов колонок
        if (!checkColumnIndexesValid(indexFrom, indexTo, fieldNames.size()))
            return result;

        for (int i = 0; i < fieldNames.size(); i++) {
            // берем значение ячейки, смщенное на indexFrom относительно начальной колонки
            int cellIndex = indexFrom + i;
            // формируем данные до columnToIndex
            if (cellIndex > indexTo)
                break;

            String fieldName = fieldNames.get(i)
                    .strip()
                    .replaceAll("\\W+", "_");
            String fieldType = toPostgresType(sheet.getRow(dataRowIndex).getCell(i + indexFrom, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
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

//    public String toPostgresTableValues(Sheet sheet,
//                                        List<String> fieldTypes,
//                                        int rowFrom) {
//        return toPostgresTableValues(sheet, fieldTypes, rowFrom, Optional.empty(), Optional.empty(), Optional.empty());
//    }

    public String toPostgresTableValues(Sheet sheet,
                                        List<String> fieldTypes,
                                        int rowFromIndex,
                                        Optional<Integer> rowToIndex,
                                        Optional<Integer> columnFromIndex,
                                        Optional<Integer> columnToIndex) {
        var result = new ArrayList<String>();

        // если индекс конечной строки не задан, берем индекс последней строки данных
        int rowToIndexValue = rowToIndex.orElseGet(() -> sheet.getLastRowNum());

        // проверка индексов строк
        if (!checkRowIndexValid(sheet, rowFromIndex) || ! checkRowIndexValid(sheet, rowToIndexValue))
            return "";

        // если индексы колонок не заданы, берем диапазон списка имен полей
        int columnNumFrom = columnFromIndex.orElse(0);
        int columnNumTo = columnToIndex.orElseGet(() -> fieldTypes.size() - 1);

        // проверка индексов колонок
        if (!checkColumnIndexesValid(columnNumFrom, columnNumTo, fieldTypes.size()))
            return "";

        for (int i = rowFromIndex; i <= rowToIndexValue; i++) {
            String rowValues = toPostgresRowValues(sheet.getRow(i), fieldTypes, columnNumFrom, columnNumTo);
            result.add(rowValues);
        }
        return String.join(", ", result);
    }

    private String toPostgresRowValues(Row row,
                                       List<String> fieldTypes,
                                       int columnFromIndex,
                                       int columnToIndex) {
        var values = new ArrayList<String>();

        for (int i = 0; i < fieldTypes.size(); i++) {
            // берем значение ячейки, смщенное на columnFromIndex относительно начальной колонки
            int cellIndex = columnFromIndex + i;
            // формируем данные до columnToIndex
            if (cellIndex > columnToIndex)
                break;

            Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
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
