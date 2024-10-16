package com.example.excelparser;

import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

@SpringBootApplication
public class ExcelParserApplication {

    public static void main(String[] args) {
        try (FileInputStream inputStream = new FileInputStream(new File("/home/dmitry/Загрузки/analize_data.xlsx"))) {
            ExcelBookReader bookReader = new ExcelBookReader(inputStream);
            Sheet sheet = bookReader.getWorkbook().getSheetAt(0);

            // имена и типы данных для postgres
            Map<String, String> postgresTypes = bookReader.getPostgresTypes(sheet, 0, 1);
            String postgresTypesString = bookReader.toPostgresTypesString(postgresTypes);
            System.out.println(postgresTypesString);

            // значения для postgres
            String values = bookReader.toPostgresTableValues(sheet, 111554);
            System.out.println(values);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
