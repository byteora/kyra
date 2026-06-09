package org.byteora.kyra.excel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public final class KyraExcel {
    private KyraExcel() {
    }

    public static ExcelWorkbook create() {
        return new ExcelWorkbook();
    }

    public static ExcelWorkbook open(Path path) throws IOException {
        return XlsxReader.read(path);
    }

    public static ExcelWorkbook read(InputStream inputStream) throws IOException {
        return XlsxReader.read(inputStream);
    }
}
