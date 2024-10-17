package com.example.excelparser;

import com.example.excelparser.utils.excel.ExcelProcessorPropertyParser;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class ExcelParserApplication {

//    private static final String COLUMN_NAMES_STRING = "code, name, basis; np, name, normativ, eco_class, code_np; prod, station, station_code";
    private static final List<String> HEADER_NAMES = List.of("code", "name", "basis");

    public static void main(String[] args) {
        try (FileInputStream inputStream = new FileInputStream(new File("/home/dmitry/Загрузки/analize_data.xlsx"))) {
//            ExcelBookReader bookReader = new ExcelBookReader(inputStream);
//            Sheet sheet = bookReader.getWorkbook().getSheetAt(0);
//
//            // имена и типы данных для postgres (по строке данных)
////            Map<String, String> postgresTypes = bookReader.getPostgresTypes(sheet, 1, 0);
//            // имена и типы данных для postgres (по списку имен столбцов)
//            Map<String, String> postgresTypes = bookReader.getPostgresTypes(sheet, 1, HEADER_NAMES);
//            String postgresTypesString = bookReader.toPostgresTypesString(postgresTypes);
//
//            System.out.println(postgresTypesString);
//
//            // значения для postgres
//            String values = bookReader.toPostgresTableValues(sheet, postgresTypes.size(), 111554);
//
//            System.out.println(values);

            var propertyParser = ExcelProcessorPropertyParser.builder()
                    .sheetNames("База, Код НП, Код станции")
                    .dbTableNames("Base, CodeNP, StationCode")
                    .firstDataRows("3, 3")
                    .dbColumnNames("; np, name, normativ, eco_class, code_np; prod, station, station_code")
                    .build();

            System.out.println(propertyParser);
            System.out.println(propertyParser.buildPropertyHolders());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
