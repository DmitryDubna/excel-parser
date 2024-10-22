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
                    .sheetNames("База")
//                    .dbTableNames("Base")
//                    .firstDataRows("2, 315, 104")
//                    .dbColumnNames("; np, name, normativ, eco_class, code_np; prod, station, station_code")
                    .build();
            System.out.println("propertyParser:\n" + propertyParser);

            List<QueryPropertyHolder> queryPropertyHolders = propertyParser.buildQueryPropertyHolders();
            System.out.println("queryPropertyHolders:\n" + queryPropertyHolders);

            ExcelBookReader bookReader = new ExcelBookReader(inputStream);

            DatabaseWriter databaseWriter = DatabaseWriter.builder()
//                    .connection(connection)
                    .bookReader(bookReader)
//                    .schemeName("data_mart")
                    .overwrite(true)
//                    .logger(getLogger())
                    .build();

            QueryPropertyHolder holder = queryPropertyHolders.get(0);
            databaseWriter.write(holder);

//            databaseWriter.write(queryPropertyHolders);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
