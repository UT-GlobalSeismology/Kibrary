package io.github.afeborgeaud.tomotool.raytheory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.afeborgeaud.tomotool.raytheory.TauPUtils.Ray;
import io.github.afeborgeaud.tomotool.topoModel.Seismic3Dmodel;
import io.github.kensuke1984.kibrary.dsmsetup.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

public class Traveltime {

    private List<RaypathInformation> raypathInformations;

    private String modelName;

    private String phaseList;

    private PolynomialStructure structure;

    private Kernel kernel;

    private List<List<TraveltimeData>> measurements;

    private Seismic3Dmodel seismic3Dmodel;

    /**
     *
     */
    private boolean ignoreMantle;

    /**
     *
     */
    private boolean ignoreCMBElevation;

    public Traveltime(List<RaypathInformation> raypathInformations, String modelName, Seismic3Dmodel seismic3Dmodel, String phaseList) {
        this.raypathInformations = raypathInformations;
        this.modelName = modelName;
        this.phaseList = phaseList;
        this.measurements = new ArrayList<>();
        this.ignoreMantle = false;
        this.ignoreCMBElevation = false;
        this.seismic3Dmodel = seismic3Dmodel;

        switch (modelName) {
        case "prem":
            structure = PolynomialStructure.ISO_PREM;
            break;
        case "ak135":
            structure = PolynomialStructure.AK135;
            break;
        default:
            throw new RuntimeException("Model not implemented yet " + modelName);
        }

        kernel = new Kernel(structure);
    }

    private List<int[]> create_batches(int maxNinBatch) {
        List<int[]> batches = new ArrayList<>();
        int n_raypaths = raypathInformations.size();
        if (maxNinBatch > n_raypaths)
            batches.add(new int[] {0, n_raypaths});
        else {
            int n_batches = n_raypaths / maxNinBatch;
            if (n_batches % maxNinBatch != 0) n_batches += 1;
            for (int i = 0; i < n_batches - 1; i++)
                batches.add(new int[] {i * maxNinBatch, (i+1) * maxNinBatch});
            batches.add(new int[] {(n_batches-1) * maxNinBatch, n_raypaths});
        }
        return batches;
    }

    public void run() {
        measurements.clear();
        System.err.println("Computing " + raypathInformations.size() + " raypaths");

        AtomicInteger count = new AtomicInteger();
        TauPUtils taupUtils = new TauPUtils(modelName);
        taupUtils.parsePhasesList(phaseList);
        List<int[]> batches = create_batches((int) (1e5));

        for (RaypathInformation raypathInformation : raypathInformations) {
            taupUtils.setCMTLocation(raypathInformation.getCmtLocation());

            // Compute ray path (also contains scatter points) using TauP
            try {
                taupUtils.calculate(raypathInformation.getObserverPosition());
            } catch (Exception e) {
                System.err.println(e.getMessage());
                continue;
            }

            double distance = raypathInformation.getDistanceDegree();

            List<Ray> rays = taupUtils.getRays();

            double dH = 0.;
            List<TraveltimeData> thisRecordList = new ArrayList<>();
            for (Ray ray : rays) {
                String phaseName = ray.getPhase();

                double[] traveltimes = new double[3];
                boolean addPoint = true;

                List<ScatterPoint> scatterPoints = ray.getScatterPoints();

                if (scatterPoints != null) {
                    // Effect of CMB topo
                    if (!ignoreCMBElevation) {
                        for (ScatterPoint sp : scatterPoints) {
                            dH = seismic3Dmodel.getCMBElevation(sp.getPosition());
                            try {
                                double[] dts = calculate(sp, dH);
                                traveltimes = add(traveltimes, dts);
                            } catch (IllegalArgumentException e) {
                                System.err.println(sp + " " + raypathInformation.getObserverPosition() + " " + raypathInformation.getCmtLocation() + " " + raypathInformation.getDistanceDegree());
                                addPoint = false;
                            }
                        }
                    }
                }

                // Effect of 3-D mantle velocity
                if (!ignoreMantle) {
                  FullPosition[] raypath = ray.getRaypath();
                    if (raypath != null)
                        if (addPoint) {
                            traveltimes = add(traveltimes, calculateV(raypath, phaseName));
                        }
                }

                if (addPoint)
                    thisRecordList.add(new TraveltimeData(raypathInformation.getObserver(), raypathInformation.getEvent()
                            , phaseName, scatterPoints, traveltimes));
                else
                    thisRecordList.add(new TraveltimeData(raypathInformation.getObserver(), raypathInformation.getEvent()
                            , phaseName, scatterPoints, new double[] {Double.NaN, Double.NaN, Double.NaN}));
            }
            measurements.add(thisRecordList);

            int step = Math.max(1, raypathInformations.size() / 10);
            if (count.incrementAndGet() % step == 0)
                System.err.print(count.get() * 10 / step + "%...");
//		});
        }

        System.err.println();
        System.err.println("Run finished!");
    }

    public List<List<TraveltimeData>> getMeasurements() {
        return measurements;
    }

    private double[] calculate(ScatterPoint scatterPoint, double dH) throws IllegalArgumentException {
        double[] times = new double[3];
        switch (scatterPoint.getType()) {
        case transmission:
            try {
                times[0] = kernel.transmission(scatterPoint.getRayparameter(), scatterPoint.getWaveType()) * dH;
                return times;
            } catch (IllegalArgumentException e) {
                throw e;
            }
        case reflection_top:
            try {
                times[0] = kernel.topReflection(scatterPoint.getRayparameter(), scatterPoint.getWaveType()) * dH;
                return times;
            } catch (IllegalArgumentException e) {
                throw e;
            }
        case reflection_under:
            try {
                times[0] = kernel.undersideReflection(scatterPoint.getRayparameter()) * dH;
                return times;
            } catch (IllegalArgumentException e) {
                throw e;
            }
        default:
            throw new RuntimeException("Unexpected");
        }
    }

    // [0]: dt [1]: t
    private double[] calculateV(FullPosition[] raypath, String phaseName) {
        double[] traveltime = new double[3];
        double d0 = raypath[0].getDistance(raypath[1]);
        traveltime = add(traveltime, calculateOnePointV(d0, raypath[0], raypath[1], phaseName));
        for (int i = 1; i < raypath.length - 1; i++) {
            d0 = raypath[i].getDistanceGeographical(raypath[i+1]);
            traveltime = add(traveltime, calculateOnePointV(d0, raypath[i], raypath[i+1], phaseName));
        }

        return traveltime;
    }


    private double[] calculateOnePointV(double l, FullPosition loc, String phaseName) {
        double[] times = new double[3];
        if (phaseName.equals("SKS") || phaseName.equals("SKKS") || phaseName.equals("SKKKS") || phaseName.equals("SKKKS") || phaseName.equals("S") || phaseName.equals("ScS")
                || phaseName.equals("ScSScS") || phaseName.equals("ScSScSScS")) {
            if (loc.getR() < 3480. && loc.getR() >= 1221.5) {
                times[0] = 0;
                times[1] = 0;
                times[2] = 0;
            }
            else {
                times[0] = -l / seismic3Dmodel.getVs(loc.getR()) * seismic3Dmodel.getdlnVs(loc);
                times[1] = l / seismic3Dmodel.getVs(loc.getR());
                times[2] = l / PolynomialStructure.ISO_PREM.getVshAt(loc.getR());
            }
        }
        else if (phaseName.equals("P") || phaseName.equals("PcP") || phaseName.equals("PKP") || phaseName.equals("PKKP") || phaseName.equals("PKKKP") || phaseName.equals("PKKKKP")
                || phaseName.equals("PKPm") || phaseName.equals("PKKPm") || phaseName.equals("PKKKPm") || phaseName.equals("PKKKKPm")) {
            times[0] = -l / seismic3Dmodel.getVp(loc.getR()) * seismic3Dmodel.getdlnVp(loc);
            times[1] = l / seismic3Dmodel.getVp(loc.getR());
            times[2] = l / PolynomialStructure.ISO_PREM.getVphAt(loc.getR());
        }
        else
            throw new RuntimeException("Phase not implemented yet " + phaseName);
        return times;
    }

    private double[] calculateOnePointV(double l, FullPosition loc1, FullPosition loc2, String phaseName) {
        double[] times = new double[3];
        if (phaseName.equals("SKS") || phaseName.equals("SKKS") || phaseName.equals("SKKKS") || phaseName.equals("SKKKS") || phaseName.equals("S") || phaseName.equals("ScS")
                || phaseName.equals("ScSScS") || phaseName.equals("ScSScSScS")
                || phaseName.equals("SKKSm") || phaseName.equals("SKSm")) {
            double r1, r2;
            if (loc1.getR() > loc2.getR()) {
                r1 = loc1.getR() - 1e-7;
                r2 = loc2.getR() + 1e-7;
            }
            else {
                r1 = loc1.getR() + 1e-7;
                r2 = loc2.getR() - 1e-7;
            }
            //fix
            if (loc1.getR() >= 3480 && r1 < 3480) r1 = 3480;
            if (loc2.getR() >= 3480 && r2 < 3480) r2 = 3480;
            //
            if (r1 < 3480. && r1 >= 1221.5) {
                times[0] = -l / (seismic3Dmodel.getVp(r1) + seismic3Dmodel.getVp(r2)) * (seismic3Dmodel.getdlnVp(loc1.toFullPosition(r1)) + seismic3Dmodel.getdlnVp(loc2.toFullPosition(r2)));
                times[1] = 2 * l / (seismic3Dmodel.getVp(r1) + seismic3Dmodel.getVp(r2));
                times[2] = 2 * l / (PolynomialStructure.ISO_PREM.getVphAt(r1) + PolynomialStructure.ISO_PREM.getVphAt(r2));
            }
            else {
                times[0] = -l / (seismic3Dmodel.getVs(r1) + seismic3Dmodel.getVs(r2)) * (seismic3Dmodel.getdlnVs(loc1.toFullPosition(r1)) + seismic3Dmodel.getdlnVs(loc2.toFullPosition(r2)));
                times[1] = 2 * l / (seismic3Dmodel.getVs(r1) + seismic3Dmodel.getVs(r2));
                times[2] = 2 * l / (PolynomialStructure.ISO_PREM.getVshAt(r1) + PolynomialStructure.ISO_PREM.getVshAt(r2));
            }
        }
        else if (phaseName.equals("PcS")) {
            double r1, r2;
            if (loc1.getR() > loc2.getR()) {
                r1 = loc1.getR() - 1e-7;
                r2 = loc2.getR() + 1e-7;
            }
            else {
                r1 = loc1.getR() + 1e-7;
                r2 = loc2.getR() - 1e-7;
            }
            //fix
            if (loc1.getR() >= 3480 && r1 < 3480) r1 = 3480;
            if (loc2.getR() >= 3480 && r2 < 3480) r2 = 3480;
            //
            if (r1 < r2) {
                times[0] = -l / (seismic3Dmodel.getVp(r1) + seismic3Dmodel.getVp(r2)) * (seismic3Dmodel.getdlnVp(loc1.toFullPosition(r1)) + seismic3Dmodel.getdlnVp(loc2.toFullPosition(r2)));
                times[1] = 2 * l / (seismic3Dmodel.getVp(r1) + seismic3Dmodel.getVp(r2));
                times[2] = 2 * l / (PolynomialStructure.ISO_PREM.getVphAt(r1) + PolynomialStructure.ISO_PREM.getVphAt(r2));
            }
            else {
                times[0] = -l / (seismic3Dmodel.getVs(r1) + seismic3Dmodel.getVs(r2)) * (seismic3Dmodel.getdlnVs(loc1.toFullPosition(r1)) + seismic3Dmodel.getdlnVs(loc2.toFullPosition(r2)));
                times[1] = 2 * l / (seismic3Dmodel.getVs(r1) + seismic3Dmodel.getVs(r2));
                times[2] = 2 * l / (PolynomialStructure.ISO_PREM.getVshAt(r1) + PolynomialStructure.ISO_PREM.getVshAt(r2));
            }
        }
        else if (phaseName.equals("P") || phaseName.equals("PcP") || phaseName.equals("PKP") || phaseName.equals("PKKP") || phaseName.equals("PKKKP") || phaseName.equals("PKKKKP")
                || phaseName.equals("PKPm") || phaseName.equals("PKKPm") || phaseName.equals("PKKKPm") || phaseName.equals("PKKKKPm")
                || phaseName.equals("PKiKP")) {
            double r1, r2;
            if (loc1.getR() > loc2.getR()) {
                r1 = loc1.getR() - 1e-5;
                r2 = loc2.getR() + 1e-5;
            }
            else {
                r1 = loc1.getR() + 1e-5;
                r2 = loc2.getR() - 1e-5;
            }
            times[0] = -l / (seismic3Dmodel.getVp(r1) + seismic3Dmodel.getVp(r2)) * (seismic3Dmodel.getdlnVp(loc1.toFullPosition(r1)) + seismic3Dmodel.getdlnVp(loc2.toFullPosition(r2)));
            times[1] = 2 * l / (seismic3Dmodel.getVp(r1) + seismic3Dmodel.getVp(r2));
            times[2] = 2 * l / (PolynomialStructure.ISO_PREM.getVphAt(r1) + PolynomialStructure.ISO_PREM.getVphAt(r2));
        }
        else
            throw new RuntimeException("Phase not implemented yet " + phaseName);
        return times;
    }

    private double[] add(double[] d1, double[] d2) {
        double[] res = new double[d1.length];
        for (int i = 0; i < d1.length; i++)
            res[i] = d1[i] + d2[i];
        return res;
    }

    public Seismic3Dmodel getSeismic3Dmodel() {
        return seismic3Dmodel;
    }

    public void writeEventInformation(Path outpath) throws IOException {
        PrintWriter pw = new PrintWriter(outpath.toFile());
        raypathInformations.stream().map(r -> r.getEvent()).distinct().forEach(id -> pw.println(id + " " + id.getEvent().getCmtLocation()));
        pw.close();
    }

    public void writeStationInformation(Path outpath) throws IOException {
        PrintWriter pw = new PrintWriter(outpath.toFile());
        raypathInformations.stream().map(r -> r.getObserver()).distinct().forEach(station -> pw.println(station + " " + station.getPosition()));
        pw.close();
    }

    public void setIgnoreMantle(boolean value) {
        ignoreMantle = value;
    }

    public void setIgnoreCMBElevation(boolean value) {
        ignoreCMBElevation = value;
    }
}
