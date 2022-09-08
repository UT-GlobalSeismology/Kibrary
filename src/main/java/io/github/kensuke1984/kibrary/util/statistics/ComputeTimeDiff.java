package io.github.kensuke1984.kibrary.util.statistics;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.sc.seis.TauP.SphericalCoords;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import edu.sc.seis.TauP.TimeDist;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionData;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

public class ComputeTimeDiff {

	public static void main(String[] args) throws IOException, TauModelException {
		Path correctionsSPath = Paths.get(args[0]);
		Path correctionsScSPath = Paths.get(args[1]);
		
		Path outpath = Paths.get("differential_SScS.txt");
		
		Set<StaticCorrectionData> correctionsS = StaticCorrectionDataFile.read(correctionsSPath);
		Set<StaticCorrectionData> correctionsScS = StaticCorrectionDataFile.read(correctionsScSPath);

		TauP_Time timetool = new TauP_Time("prem");
		timetool.parsePhaseList("Scs");
		
		PrintWriter pw = new PrintWriter(outpath.toFile());
		for (StaticCorrectionData correctionScS : correctionsScS) {
			List<StaticCorrectionData> tmpList = correctionsS.stream().parallel().filter(c -> c.getGlobalCMTID().equals(correctionScS.getGlobalCMTID())
					&& c.getObserver().equals(correctionScS.getObserver())
					&& c.getComponent().equals(correctionScS.getComponent()))
					.collect(Collectors.toList());
			if (tmpList.size() != 1) {
				System.err.println("found more than one correction");
				continue;
			}
			StaticCorrectionData corrS = tmpList.get(0);
			
			double dT = correctionScS.getTimeshift() - corrS.getTimeshift();
			double dA = correctionScS.getAmplitudeRatio() / corrS.getAmplitudeRatio();
			
			double distance = Math.toDegrees(corrS.getGlobalCMTID().getEventData().getCmtPosition().computeEpicentralDistance(corrS.getObserver().getPosition()));
			timetool.setSourceDepth(6371. - corrS.getGlobalCMTID().getEventData().getCmtPosition().getR());
			timetool.calculate(distance);
			TimeDist[] pierces = timetool.getArrival(0).getPierce();
			double pierceDist = 0;;
			for (TimeDist p : pierces) {
				if (Math.abs(p.getDepth() - 2891.) < 1) {
					pierceDist = p.getDistDeg();
					break;
				}
			}
			
			double evtLat = corrS.getGlobalCMTID().getEventData().getCmtPosition().getLatitude();
			double evtLon = corrS.getGlobalCMTID().getEventData().getCmtPosition().getLongitude();
			double azimuth = Math.toDegrees(corrS.getGlobalCMTID().getEventData().getCmtPosition().computeAzimuth(corrS.getObserver().getPosition()));
			
			double lat = SphericalCoords.latFor(evtLat, evtLon, pierceDist, azimuth);
			double lon = SphericalCoords.lonFor(evtLat, evtLon, pierceDist, azimuth);
			
//			HorizontalPosition midPoint = corrS.getGlobalCMTID().getEvent().getCmtLocation().toHorizontalPosition().getMidpoint(corrS.getStation().getPosition());
			HorizontalPosition midPoint = new HorizontalPosition(lat, lon);
			
			pw.println(midPoint.getLatitude() + " " + midPoint.getLongitude() + " " + dT + " " + dA);
		}
		pw.close();
	}
	

}
