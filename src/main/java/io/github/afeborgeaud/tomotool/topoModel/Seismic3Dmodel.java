package io.github.afeborgeaud.tomotool.topoModel;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

public interface Seismic3Dmodel {

    public double getCMBElevation(HorizontalPosition position);

    public void writeCMBElevationMap(Path outpath, StandardOpenOption... options) throws IOException;

    public double getdlnVp(FullPosition loc);

    public double getdlnVs(FullPosition loc);

    public double getVs(double radius);

    public double getVp(double radius);

    public void setTruncationRange(double rmin, double rmax);

    public void outputLayer(Path outputpath, double r) throws IOException;

    public String getName();

    public void initVelocityGrid();

}
