package com.example.excelparser;

import com.example.excelparser.utils.excel.DatabaseWriter;
import com.example.excelparser.utils.excel.QueryPropertyHolder;
import com.example.excelparser.utils.excel.ExcelBookReader;
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
        try (FileInputStream inputStream = new FileInputStream(new File("/home/dmitry/Загрузки/analize_data_2.xlsx"))) {

            var propertyParser = ExcelProcessorPropertyParser.builder()
                    .sheetNames("База, Код НП, Код станции")
                    .dbTableNames("Base, CodeNP, StationCode")
                    .firstDataRows("111554, 3, 3")
                    .dbColumnNames("; np, name, normativ, eco_class, code_np; prod, station, station_code")
                    .build();
//            System.out.println("propertyParser:\n" + propertyParser);

            List<QueryPropertyHolder> queryPropertyHolders = propertyParser.buildQueryPropertyHolders();
            System.out.println("queryPropertyHolders:\n" + queryPropertyHolders);

            ExcelBookReader bookReader = new ExcelBookReader(inputStream);

            QueryPropertyHolder holder = queryPropertyHolders.get(0);

            DatabaseWriter databaseWriter = DatabaseWriter.builder()
//                    .connection(connection)
                    .bookReader(bookReader)
                    .schemeName("data_mart")
                    .overwrite(true)
//                    .logger(getLogger())
                    .build();

            databaseWriter.write(holder);

//            Sheet sheet = bookReader.getWorkbook().getSheet(holder.getSheetName());

//            Map<String, String> postgresTypes =
//                    holder.getDbColumnNames().isEmpty()
//                            // имена и типы данных для postgres (по строке данных)
//                            ? bookReader.getPostgresTypes(sheet, holder.getFirstDataRow(), 0)
//                            // имена и типы данных для postgres (по списку имен столбцов)
//                            : bookReader.getPostgresTypes(sheet, holder.getFirstDataRow(), holder.getDbColumnNames());
//            System.out.println("postgresTypes:\n" + postgresTypes);

//            String fieldsDefinition = bookReader.toFieldsDefinitionString(postgresTypes);
//            System.out.println("fieldsDefinition:\n" + fieldsDefinition);

//            // значения для postgres
//            String values = bookReader.toPostgresTableValues(sheet, postgresTypes.size(), 111554);
//            System.out.println("values:\n" + values);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
