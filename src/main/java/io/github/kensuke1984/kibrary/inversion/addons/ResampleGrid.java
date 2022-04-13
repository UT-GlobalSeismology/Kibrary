package io.github.kensuke1984.kibrary.inversion.addons;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.Physical3DParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Resample the inputed unknowns file.
 * <p>
 * New horizontal points files are half and quarter of origin one.
 * horizontal mapping files are half, quarter, and half of half of origin one.
 * <p>
 * TODO output resampled unkowns file
 *
 * @author Unkown (anselme??)
 * @author rei changed file name
 *
 */

public class ResampleGrid {

    private List<UnknownParameter> targetUnknowns;

    private List<UnknownParameter> resampledUnknowns;

    int samplingRate;

    private double dlat;

    private double dlon;

    private double newDlat;

    private double newDlon;

    private List<List<UnknownParameter>> mapping;

    private int[][] iTargetToResampled;

    private int[] iResampledToTarget;

    public static void main(String[] args) {
        if (args.length != 1) {

        }
//		List<UnknownParameter> parameters = new ArrayList<>();
//		parameters.add(new Physical3DParameter(PartialType.MU, new Location(-2, -2, 6371), 1.));
//		parameters.add(new Physical3DParameter(PartialType.MU, new Location(-2, 2, 6371), 1.));
//		parameters.add(new Physical3DParameter(PartialType.MU, new Location(2, -2, 6371), 1.));
//		parameters.add(new Physical3DParameter(PartialType.MU, new Location(2, 2, 6371), 1.));
//		parameters.add(new Physical3DParameter(PartialType.LAMBDA, new Location(-2, -2, 6371), 1.));
//		parameters.add(new Physical3DParameter(PartialType.LAMBDA, new Location(-2, 2, 6371), 1.));
//		parameters.add(new Physical3DParameter(PartialType.LAMBDA, new Location(2, -2, 6371), 1.));
//		parameters.add(new Physical3DParameter(PartialType.LAMBDA, new Location(2, 2, 6371), 1.));
//
//		ResampleGrid sampler = new ResampleGrid(parameters, 4, 4, 2);
//
////		List<UnknownParameter> resampledOnce = sampler.upsample(1);
////		List<List<UnknownParameter>> mapping = sampler.getMapping();
////
////		for (int i = 0; i < parameters.size(); i++) {
////			System.out.println(parameters.get(i));
////			mapping.get(i).forEach(p -> System.out.print(p + ", "));
////			System.out.println();
////		}
//
//
//		List<UnknownParameter> resampledTwice = sampler.getResampledUnkowns();
//		List<List<UnknownParameter>> mapping = sampler.getMapping();
//		int[][] iTargetToResampled = sampler.getiTargetToResampled();
//
//		for (int i = 0; i < parameters.size(); i++) {
//			System.out.println(parameters.get(i));
//			mapping.get(i).forEach(p -> System.out.print(p + ", "));
//			System.out.println();
//		}
//
//		System.out.println();
//		for (int i = 0; i < parameters.size(); i++) {
//			System.out.println(parameters.get(i));
////			mapping.get(i).forEach(p -> System.out.print(p + ", "));
//			for (int j : iTargetToResampled[i])
//				System.out.print(resampledTwice.get(j) + ", ");
//			System.out.println();
//		}

        Path parameterPath = Paths.get(args[0]);
        try {
            List<UnknownParameter> allParameters = UnknownParameterFile.read(parameterPath);
            double r0 = allParameters.stream().map(u -> u.getPosition().getR()).findFirst().get();
            PartialType type = allParameters.stream().map(UnknownParameter::getPartialType).findFirst().get();
            List<UnknownParameter> parameters = allParameters.stream()
                    .filter(u -> u.getPosition().getR() == r0
                            && u.getPartialType().equals(type))
                    .collect(Collectors.toList());

            double dl = allParameters.stream().mapToDouble(p -> Math.abs(p.getPosition().getLatitude() - allParameters.get(0).getPosition().getLatitude())).distinct().sorted().toArray()[1];

            double dlat = dl;
            double dlon = dl;

            System.err.println("dLat = dLon = " + dl);

            ResampleGrid sampler2 = new ResampleGrid(parameters, dlat, dlon, 2);
            List<UnknownParameter> parameters2 = sampler2.getResampledUnkowns();
            List<HorizontalPosition> horizontalPositions2 = sampler2.getResampledPositions();
            int[] iResampledToTarget2 = sampler2.getiResampledToTarget();

            ResampleGrid sampler3 = new ResampleGrid(parameters, dlat, dlon, 3);
            List<UnknownParameter> parameters3 = sampler3.getResampledUnkowns();
            List<HorizontalPosition> horizontalPositions3 = sampler3.getResampledPositions();
            int[] iResampledToTarget3 = sampler3.getiResampledToTarget();

            ResampleGrid sampler22 = new ResampleGrid(parameters2, sampler2.getNewDlat(), sampler2.getNewDlon(), 2);
            List<UnknownParameter> parameters22 = sampler22.getResampledUnkowns();
            List<HorizontalPosition> horizontalPositions22 = sampler22.getResampledPositions();
            int[] iResampledToTarget22 = sampler22.getiResampledToTarget();

            String tmpString = GadgetAid.getTemporaryString();
            Path parameters2Path = Paths.get("newHorizontalPoints_halfSampled" + tmpString + ".inf");
            Path parameters3Path = Paths.get("newHorizontalPoints_quarterSampled" + tmpString + ".inf");
            Path mapping2Path = Paths.get("horizontalMapping_halfSampled" + tmpString + ".inf");
            Path mapping3Path = Paths.get("horizontalMapping_quarterSampled" + tmpString + ".inf");
            Path mapping22Path = Paths.get("horizontalMapping_halfSampled_halfSampled" + tmpString + ".inf");

            PrintWriter pw_hp2 = new PrintWriter(parameters2Path.toFile());
            PrintWriter pw_hp3 = new PrintWriter(parameters3Path.toFile());

            PrintWriter pw_mp2 = new PrintWriter(mapping2Path.toFile());
            PrintWriter pw_mp3 = new PrintWriter(mapping3Path.toFile());
            PrintWriter pw_mp22 = new PrintWriter(mapping22Path.toFile());

            for (int i = 0; i < horizontalPositions2.size(); i++) {
                pw_hp2.println(horizontalPositions2.get(i).getLatitude() + " " + horizontalPositions2.get(i).getLongitude());
                pw_mp2.println(horizontalPositions2.get(i).getLatitude() + " " + horizontalPositions2.get(i).getLongitude() + " " + "0." + " " + iResampledToTarget2[i]);
            }
            for (int i = 0; i < horizontalPositions3.size(); i++) {
                pw_hp3.println(horizontalPositions3.get(i).getLatitude() + " " + horizontalPositions3.get(i).getLongitude());
                pw_mp3.println(horizontalPositions3.get(i).getLatitude() + " " + horizontalPositions3.get(i).getLongitude() + " " + "0." + " " + iResampledToTarget3[i]);
            }
            for (int i = 0; i < horizontalPositions22.size(); i++)
                pw_mp22.println(horizontalPositions22.get(i).getLatitude() + " " + horizontalPositions22.get(i).getLongitude() + " " + "0." + " " + iResampledToTarget22[i]);

            pw_hp2.close();
            pw_hp3.close();
            pw_mp2.close();
            pw_mp3.close();
            pw_mp22.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public ResampleGrid(List<UnknownParameter> unknowns, double dlat, double dlon, int samplingRate) {
        this.targetUnknowns = unknowns;
        this.dlat = dlat;
        this.dlon = dlon;
        this.samplingRate = samplingRate;

        resampledUnknowns = upsample(samplingRate);
    }

    private List<UnknownParameter> upsample(int nfolds) {
        mapping = new ArrayList<>();

        List<UnknownParameter> resampled = new ArrayList<>();

        double dlonTmp = dlon;
        double dlatTmp = dlat;

        resampled = targetUnknowns;
        for (int i = 1; i < nfolds; i++) {
            resampled = upsampleOnce(resampled, dlatTmp, dlonTmp);
            dlonTmp /= 2.;
            dlatTmp /= 2.;
        }

        newDlat = dlatTmp;
        newDlon = dlonTmp;

        int n = 1;
        for (int i = 1; i < nfolds; i++)
            n *= 4;

        iTargetToResampled = new int[targetUnknowns.size()][n];
        iResampledToTarget = new int[resampled.size()];

        for (int i = 0; i < targetUnknowns.size(); i++) {
            List<UnknownParameter> tmpmap = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                int iResampled = i * n + j;

                tmpmap.add(resampled.get(iResampled));

                iTargetToResampled[i][j] = iResampled;
                iResampledToTarget[iResampled] = i;
            }
            mapping.add(tmpmap);
        }

        return resampled;
    }

    private List<UnknownParameter> upsampleOnce(List<UnknownParameter> parameters, double dlon, double dlat) {
        List<UnknownParameter> resampled = new ArrayList<>();

        for (UnknownParameter p : parameters) {
            FullPosition loc = p.getPosition();
            double lat = loc.getLatitude();
            double lon = loc.getLongitude();
            double r = loc.getR();
            FullPosition loc1 = new FullPosition(lat - dlat/4., lon - dlon/4., r);
            FullPosition loc2 = new FullPosition(lat - dlat/4., lon + dlon /4., r);
            FullPosition loc3 = new FullPosition(lat + dlat/4., lon - dlon /4., r);
            FullPosition loc4 = new FullPosition(lat + dlat/4., lon + dlon /4., r);

            resampled.add(new Physical3DParameter(p.getPartialType(), loc1, p.getWeighting() / 4.));
            resampled.add(new Physical3DParameter(p.getPartialType(), loc2, p.getWeighting() / 4.));
            resampled.add(new Physical3DParameter(p.getPartialType(), loc3, p.getWeighting() / 4.));
            resampled.add(new Physical3DParameter(p.getPartialType(), loc4, p.getWeighting() / 4.));
        }

        return resampled;
    }

    public List<List<UnknownParameter>> getMapping() {
        return mapping;
    }

    public int[][] getiTargetToResampled() {
        return iTargetToResampled;
    }

    public int[] getiResampledToTarget() {
        return iResampledToTarget;
    }

    public List<UnknownParameter> getTargetUnknowns() {
        return targetUnknowns;
    }

    public List<UnknownParameter> getResampledUnkowns() {
        return resampledUnknowns;
    }

    public List<HorizontalPosition> getResampledPositions() {
        return resampledUnknowns.stream().map(u -> u.getPosition().toHorizontalPosition()).distinct().collect(Collectors.toList());
    }

    public double getNewDlat() {
        return newDlat;
    }

    public double getNewDlon() {
        return newDlon;
    }

}
