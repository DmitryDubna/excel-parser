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
import java.util.logging.Logger;
import java.util.regex.Pattern;

@SpringBootApplication
public class ExcelParserApplication {

//    private static final String COLUMN_NAMES_STRING = "code, name, basis; np, name, normativ, eco_class, code_np; prod, station, station_code";
    private static final List<String> HEADER_NAMES = List.of("code", "name", "basis");
    private static final ExcelProcessorPropertyParser.ParseMode PARSE_MODE = ExcelProcessorPropertyParser.ParseMode.ONE_TABLE_PER_SHEET;
    private static String SHEET_NAMES_STRING = "";

    public static void main(String[] args) {

//        String s = "   4  - 61,  43 -69,    18-   111   ";
//        System.out.println(Pattern.matches("(\\s*\\d+\\s*-\\s*\\d+\\s*)+(,\\s*\\d+\\s*-\\s*\\d+\\s*)*", s));

//        try (FileInputStream inputStream = new FileInputStream(new File("/home/dmitry/Загрузки/analize_data_2.xlsx"))) {
        try (FileInputStream inputStream = new FileInputStream(new File("/home/dmitry/Загрузки/formy_otcheta_s_uchastka_2.xlsx"))) {

            ExcelBookReader bookReader = new ExcelBookReader(inputStream);

            var propertyParser = ExcelProcessorPropertyParser.builder()
//                    .sheetNames(SHEET_NAMES_STRING.isBlank() ? bookReader.getFirstSheetName().orElse("") : SHEET_NAMES_STRING)
                    .sheetNames("Трудовые_Технические_ресурсы")
//                    .dbTableNames("Base, Dict")
//                    .dataColumns("4-6, 8-11")
//                    .dataColumns("")
                    .firstDataRows("2")
//                    .lastDataRows("3, 3")
//                    .dbFieldNames("; np, name, normativ, eco_class, code_np; prod, station, station_code, test1, test2, test3")
                    .build();
            System.out.println("propertyParser:\n" + propertyParser);

            List<QueryPropertyHolder> queryPropertyHolders = propertyParser.buildQueryPropertyHolders(PARSE_MODE);
            System.out.println("queryPropertyHolders:\n" + queryPropertyHolders);

            DatabaseWriter databaseWriter = DatabaseWriter.builder()
//                    .connection(connection)
                    .bookReader(bookReader)
//                    .schemeName("")
                    .overwrite(false)
//                    .logger(getLogger())
                    .build();

//            QueryPropertyHolder holder = queryPropertyHolders.get(0);
//            databaseWriter.write(holder);

            databaseWriter.write(queryPropertyHolders);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
