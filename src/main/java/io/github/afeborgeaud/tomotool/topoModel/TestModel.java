package io.github.afeborgeaud.tomotool.topoModel;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure_old;

public class TestModel implements Seismic3Dmodel {

    @Override
    public double getCMBElevation(HorizontalPosition position) {
        // TODO Auto-generated method stub
        return 1.;
    }

    @Override
    public void writeCMBElevationMap(Path outpath, StandardOpenOption... options) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public double getdlnVp(FullPosition loc) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getdlnVs(FullPosition loc) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getVs(double radius) {
        return PolynomialStructure_old.PREM.getVshAt(radius);
    }

    @Override
    public double getVp(double radius) {
        return PolynomialStructure_old.PREM.getVphAt(radius);
    }

    @Override
    public void setTruncationRange(double rmin, double rmax) {
        // TODO Auto-generated method stub

    }

    @Override
    public void outputLayer(Path outputpath, double r) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return "TestModel";
    }

    public void initVelocityGrid() {
    }

}
