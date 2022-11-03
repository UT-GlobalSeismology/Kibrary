package io.github.kensuke1984.kibrary.timewindow.addons;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.correction.FujiStaticCorrection;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.addons.Phases;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.Trace;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

public class MakeWindowPcP {

	public static void main(String[] args) throws IOException, TauModelException {
//		Path workdir = Paths.get("/work/anselme/CA_ANEL_NEW/syntheticPREM/filtered_stf_12.5-200s");
//		workdir = Paths.get("/work/anselme/CA_ANEL_NEW/synthetic_s0/filtered_stf_12.5-200s");
//		workdir = Paths.get("/work/anselme/CA_ANEL_NEW/synthetic_s0_it1/filtered_stf_12.5-200s");
//		workdir = Paths.get("/work/anselme/CA_ANEL_NEW/synthetic_s5/filtered_stf_12.5-200s");
		Path workdir = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/syntheticPREM_Q165/filtered_nostf_6-200s");
//		Path workdir = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/cluster34/synthetic_cl4s0_it2/filtered_nostf_8-200s");
//		Path workdir = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/cluster34/synthetic_cl4s0_it1/filtered_nostf_8-200s");
//		Path workdir = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/cluster34/synthetic_cl3s0_it1/filtered_nostf_6-200s");
//		Path workdir = Paths.get(".");
		
//		Path timewindowPath = workdir.resolve("selectedTimewindow_SScS_60deg.dat");
		Path timewindowPath = workdir.resolve("selectedTimewindow_PcP_65deg_goodeq.dat");
		Set<TimewindowData> timewindowsForSelection = TimewindowDataFile.read(timewindowPath); 
//		timewindowsForSelection = null;
		
		boolean convolved = false;
		
		boolean extendedDistanceRange = true;
		
		boolean select = false;
		double minCorr = 0.5;
		double maxVariance = 2.5;
		double maxRatio = 2.5;
		double maxRatio_S = 2.5;
		
		double timeBefore = 20.; //30 for Scd
		double timeAfter = 20;
		double timeBeforesS = 5;
		
		double minPeriod = 6.;
		
		Set<EventFolder> eventFolderSet = DatasetAid.eventFolderSet(workdir);
		
		TauP_Time timetool = new TauP_Time("prem");
		timetool.parsePhaseList("P, PcP, pP");
		
		Set<TimewindowData> infoSet = new HashSet<>();
		Path outpath = workdir.resolve("selectedTimewindow_PcP" + GadgetAid.getTemporaryString() + ".dat");
		
		Set<TimewindowData> infoSetScStight = new HashSet<>();
		Path outpathTight = workdir.resolve("selectedTimewindow_PcPtight" + GadgetAid.getTemporaryString() + ".dat");
		
		Set<TimewindowData> infoSetScd = new HashSet<>();
		Path outpathScd = workdir.resolve("selectedTimewindow_Pcd" + GadgetAid.getTemporaryString() + ".dat");
		
		Set<TimewindowData> infoSet_noSelection = new HashSet<>();
		Path outpath_noSelection = workdir.resolve("timewindow_PcP" + GadgetAid.getTemporaryString() + ".dat");
		
		Set<TimewindowData> infoSetS = new HashSet<>();
		Path outpathS = workdir.resolve("timewindow_P.dat");

		int countIncludeS = 0;
		
		for (EventFolder eventFolder : eventFolderSet) {
			System.out.println(eventFolder);
			timetool.setSourceDepth(6371. - eventFolder.getGlobalCMTID().getEventData().getCmtPosition().getR());
			
			Set<SACFileName> obsNames = eventFolder.sacFileSet();
			obsNames.removeIf(sfn -> !sfn.isOBS());
			obsNames.removeIf(sfn -> !sfn.getComponent().equals(SACComponent.Z));
			
			for (SACFileName obsName : obsNames) {
				SACHeaderAccess obsHeader = obsName.readHeader();
				
				if (timewindowsForSelection != null) {
				if (timewindowsForSelection.parallelStream().filter(tw -> tw.getGlobalCMTID().equals(obsHeader.getGlobalCMTID())
						&& tw.getObserver().equals(obsHeader.getObserver()) && tw.getComponent().equals(obsName.getComponent()))
						.count() == 0)
					continue;
				}
				
				double distance = obsHeader.getValue(SACHeaderEnum.GCARC);
				timetool.calculate(distance);
				if (timetool.getNumArrivals() != 3) {
					continue;
				}
				Arrival arrivalS = timetool.getArrival(0);
				Arrival arrivalScS = timetool.getArrival(1);
				Arrival arrivalsS = timetool.getArrival(2);
				if (!arrivalS.getPhase().getName().equals("P") || !arrivalScS.getPhase().getName().equals("PcP")
						|| !arrivalsS.getPhase().getName().equals("pP")) {
					System.err.println("Problem computing P or PcP " + obsName.getName() + " " + obsHeader.getValue(SACHeaderEnum.GCARC));
					continue;
				}
				double timeS = arrivalS.getTime();
				double timeScS = arrivalScS.getTime();
				double timesS = arrivalsS.getTime();
				
				if (timesS - timeScS < minPeriod * 1.6)
					continue;
				
				SACFileAccess synData = null;
				if (!convolved)
					synData = new SACFileName(Paths.get(obsName.getAbsolutePath().concat("s"))).read();
				else
					synData = new SACFileName(Paths.get(obsName.getAbsolutePath().concat("sc"))).read();
				Trace synTrace = synData.createTrace().cutWindow(timeS - 15, timeS + 40);
				double deltaTimeP2P = Math.abs(synTrace.getXforMaxValue() - synTrace.getXforMinValue());
				double timeFirstPeak = synTrace.getXforMaxValue() < synTrace.getXforMinValue() ? synTrace.getXforMaxValue() : synTrace.getXforMinValue();
				double timeLatePeak = synTrace.getXforMaxValue() > synTrace.getXforMinValue() ? synTrace.getXforMaxValue() : synTrace.getXforMinValue();
				double timeEndOfS = timeLatePeak + deltaTimeP2P * 0.9;
				
				SACFileAccess obsData = obsName.read();
				Trace obsTrace = obsData.createTrace().cutWindow(timeS - 5, timeS + 20);
				double synSP2P = synTrace.getYVector().getLInfNorm();
				double obsSP2P = obsTrace.getYVector().getLInfNorm();
				double ratio_S = synSP2P / obsSP2P;
				
				double startTime = timeScS - timeBefore;
				double endTime = timeScS + timeAfter;
				
				double startTimeTight = timeScS - 5;
				
				double endTimeScd = timeScS - 1.;
				
				if (startTime < timeEndOfS)
					startTime = timeEndOfS;
				if (startTimeTight < timeEndOfS)
					startTimeTight = timeEndOfS;
				if (endTime > timesS - timeBeforesS)
					endTime = timesS - timeBeforesS;
				
				if (extendedDistanceRange) {
					double startTime0 = timeScS - timeBefore;
					double startTime1 = timeScS - 2.;
					if (startTime0 >= timeEndOfS)
						startTime = startTime0;
					else if (startTime0 < timeEndOfS && startTime1 >= timeEndOfS)
						startTime = timeEndOfS;
					else if (startTime1 < timeEndOfS && startTime1 >= timeFirstPeak)
						startTime = startTime1;
					else if (timeScS >= timeFirstPeak)
						startTime = timeFirstPeak;
					else {
						countIncludeS++;
						continue;
					}
				}
				else {
					if (timeEndOfS > timeScS - minPeriod/2.5) {
						countIncludeS++;
						continue;
					}
				}
				
				TimewindowData timewindow_S = new TimewindowData(timeS - 10, timeS + 20,
						obsHeader.getObserver(), obsHeader.getGlobalCMTID(),
						obsName.getComponent(), new Phase[] {Phase.P});
				infoSetS.add(timewindow_S);
				
//				if (ratio_S > maxRatio_S || ratio_S < 1./maxRatio_S)
//					continue;
				
				boolean addScS = true;
				if (select) {
					Trace synScS = synData.createTrace().cutWindow(startTime, endTime);
					Trace obsScS = obsData.createTrace().cutWindow(startTime, endTime);
					double shift = synScS.getXforMaxValue() - obsScS.getXforMaxValue();
					
					RealVector obsVector = obsData.createTrace().cutWindow(startTime - shift, endTime - shift).getYVector()
							.mapMultiply(ratio_S);
					RealVector synVector = new ArrayRealVector(Arrays.copyOf(synScS.getY(), obsVector.getDimension()));
					
					double corr = obsVector.dotProduct(synVector) / (obsVector.getNorm() * synVector.getNorm());
					double ratio = synVector.getLInfNorm() / obsVector.getLInfNorm();
					double variance = synVector.subtract(obsVector)
							.dotProduct(synVector.subtract(obsVector))
							/ obsVector.dotProduct(obsVector);
					
					boolean keep = corr >= minCorr && ratio < maxRatio && ratio > 1./maxRatio && variance < maxVariance;
					addScS = keep;
				}
				
				TimewindowData timewindow = new TimewindowData(startTime, endTime,
						obsHeader.getObserver(), obsHeader.getGlobalCMTID(),
						obsName.getComponent(), new Phase[] {Phase.PcP});
				if (addScS)
					infoSet.add(timewindow);
				
				if (startTime < endTimeScd) {
					TimewindowData timewindowScd = new TimewindowData(startTime, endTimeScd,
							obsHeader.getObserver(), obsHeader.getGlobalCMTID(),
							obsName.getComponent(), new Phase[] {Phase.PcP});
					if (addScS)
						infoSetScd.add(timewindowScd);
				}
				
//				TimewindowInformation timewindowTight = new TimewindowInformation(startTimeTight, endTime,
//						obsHeader.getStation(), obsHeader.getGlobalCMTID(),
//						obsName.getComponent(), new Phase[] {Phase.PcP});
//				if (addScS)
//					infoSetScStight.add(timewindowTight);
				
				infoSet_noSelection.add(timewindow);
			}
		}
		
		System.out.println("Excluded " + countIncludeS + " timewindows that include P phase");
		
		TimewindowDataFile.write(infoSet, outpath);
		TimewindowDataFile.write(infoSetS, outpathS);
		TimewindowDataFile.write(infoSet_noSelection, outpath_noSelection);
		TimewindowDataFile.write(infoSetScd, outpathScd);
//		TimewindowInformationFile.write(infoSetScStight, outpathTight);
	}

}
