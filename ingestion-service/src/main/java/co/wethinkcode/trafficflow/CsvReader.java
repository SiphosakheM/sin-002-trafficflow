package co.wethinkcode.trafficflow;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the old csv file and gives back the rows, still messy.
 * No cleaning happens here, that is the job of the other classes.
 */
public final class CsvReader {

    private CsvReader() {
    }

    /**
     * Reads the rows of a csv file.
     * The first line is the header with the column names, so we skip it and
     * only return the real data rows.
     */
    public static List<String[]> read(InputStream csv) throws IOException {
        try (Reader file = new InputStreamReader(csv, StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(file)) {

            List<String[]> rows = new ArrayList<>();
            try {
                String[] row = reader.readNext();   // this is the header line
                while ((row = reader.readNext()) != null) {
                    if (isEmptyRow(row)) {
                        continue;
                    }
                    rows.add(row);
                }
            } catch (CsvValidationException e) {
                // opencsv does not like the shape of the file, so we stop here.
                throw new IOException("The csv file is not readable: " + e.getMessage(), e);
            }
            return rows;
        }
    }

    /**
     * Reads a csv file that sits inside the project resources.
     */
    public static List<String[]> readFromResource(String fileName) throws IOException {
        InputStream file = CsvReader.class.getClassLoader().getResourceAsStream(fileName);
        if (file == null) {
            throw new IOException("Cannot find the file " + fileName);
        }
        return read(file);
    }

    // A line with nothing on it is not data, so we leave it out.
    private static boolean isEmptyRow(String[] row) {
        if (row.length == 0) {
            return true;
        }
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
