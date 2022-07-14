package io.github.kensuke1984.kibrary.dsminformation;

import java.io.IOException;
import java.nio.file.Paths;

import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure_old;

/**
 * @author Kensuke Konishi
 * @version 0.0.1
 */
class PolynomialStructureTest {
    public static void main(String[] args) throws IOException {
        PolynomialStructure_old.PREM.writePSV(Paths.get("/tmp/hoge"));
    }
}
