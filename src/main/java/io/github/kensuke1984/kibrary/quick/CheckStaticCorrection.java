package io.github.kensuke1984.kibrary.quick;

import io.github.kensuke1984.kibrary.correction.StaticCorrectionData;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.addons.Phases;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

public class CheckStaticCorrection {

	public static void main(String[] args) throws IOException {
		Path fujiStaticPath = Paths.get(args[0]);
		Path SEMStaticPath = Paths.get(args[1]);
		
		Set<StaticCorrectionData> fujiCorrections = StaticCorrectionDataFile.read(fujiStaticPath);
		Set<StaticCorrectionData> semCorrections = StaticCorrectionDataFile.read(SEMStaticPath);
		
		Set<StaticCorrectionData> ratios = new HashSet<>();
		Set<StaticCorrectionData> differences = new HashSet<>();
		Set<StaticCorrectionData> mixed = new HashSet<>();
		
		for (StaticCorrectionData corr : fujiCorrections) {
			StaticCorrectionData semCorr = null;
			try {
				semCorr = semCorrections.stream().filter(c -> corr.getGlobalCMTID().equals(c.getGlobalCMTID())
					&& corr.getObserver().equals(c.getObserver())
					&& corr.getComponent().equals(c.getComponent())
					&& new Phases(corr.getPhases()).equals(new Phases(c.getPhases()))).findFirst().get();
//					&& corr.getSynStartTime() == c.getSynStartTime()).findFirst().get();
			} catch (NoSuchElementException e) {
				continue;
			}
			double ratio = Math.abs(semCorr.getTimeshift() - corr.getTimeshift()) / Math.abs(corr.getTimeshift());
			double difference = corr.getTimeshift() - semCorr.getTimeshift();
//			ratio = Math.log(ratio);
			StaticCorrectionData tmpCorr = new StaticCorrectionData(corr.getObserver(), corr.getGlobalCMTID(), corr.getComponent()
					, corr.getSynStartTime(), ratio, corr.getAmplitudeRatio(), corr.getPhases());
			StaticCorrectionData tmpCorr2 = new StaticCorrectionData(corr.getObserver(), corr.getGlobalCMTID(), corr.getComponent()
					, corr.getSynStartTime(), difference, corr.getAmplitudeRatio(), corr.getPhases());
			StaticCorrectionData zeroCorr = new StaticCorrectionData(corr.getObserver(), corr.getGlobalCMTID(), corr.getComponent()
					, corr.getSynStartTime(), 0., corr.getAmplitudeRatio(), corr.getPhases());
			StaticCorrectionData mix = Math.abs(difference) <= Math.abs(corr.getTimeshift()) ? semCorr : zeroCorr;
			ratios.add(tmpCorr);
			differences.add(tmpCorr2);
			mixed.add(mix);
		}
		
		Path outmix = Paths.get("staticCorrection" + GadgetAid.getTemporaryString() + ".dat");
		StaticCorrectionDataFile.write(mixed, outmix);
		
		Path outpath0 = Paths.get("corrections_each_record_difference.txt");
		PrintWriter pw0 = new PrintWriter(outpath0.toFile());
		for (StaticCorrectionData corr : differences)
			pw0.println(corr);
		pw0.close();
		
		double[][] map = averageMap(ratios);
		double[][] map2 = averageMap(differences);
		double[][] mapCorr = averageMap(fujiCorrections);
		
		Path outpath = Paths.get("corrections_ratio.txt");
		Path outpath2 = Paths.get("corrections_difference.txt");
		Path outpath3 = Paths.get("corrections_map.txt");
		
		PrintWriter pw = new PrintWriter(outpath.toFile());
		PrintWriter pw2 = new PrintWriter(outpath2.toFile());
		PrintWriter pw3 = new PrintWriter(outpath3.toFile());
		for (int i = 0; i < 360; i++) {
			for (int j = 0; j < 180; j++) {
				pw.println(i + " " + (j - 90) + " " + map[i][j]);
				pw2.println(i + " " + (j - 90) + " " + map2[i][j]);
				pw3.println(i + " " + (j - 90) + " " + mapCorr[i][j]);
			}
		}
		pw.close();
		pw2.close();
		pw3.close();
		
		Path outpath4 = Paths.get("correctionDifferenceStation.txt");
		PrintWriter pw4 = new PrintWriter(outpath4.toFile());
		for (StaticCorrectionData corr : differences) {
			pw4.println(corr.getObserver() + " " + corr.getObserver().getPosition() + " " + corr.getTimeshift());
		}
		pw4.close();
	}
	
	public static double[][] averageMap(Set<StaticCorrectionData> ratios) {
		double[][] map = new double[360][180];
		int[][] count = new int[360][180];
		for (StaticCorrectionData corr : ratios) {
			double lon = corr.getObserver().getPosition().getLongitude();
			if (lon < 0)
				lon += 360;
			double lat = corr.getObserver().getPosition().getLatitude() + 90;
			int ilon = (int) lon;
			int ilat = (int) lat;
			if (ilon == 360)
				ilon = 359;
			if (ilat == 180)
				ilat = 179;
			if (corr.getTimeshift() < 100.) {
				map[ilon][ilat] += corr.getTimeshift();
				count[ilon][ilat] += 1;
			}
		}
		
		for (int i = 0; i < 360; i++) {
			for (int j = 0; j < 180; j++) {
				if (count[i][j] > 0)
					map[i][j] /= count[i][j];
			}
		}
		
		return map;
	}
}
