package io.github.kensuke1984.kibrary.util.addons;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;

public class WriteMisfitFromWaveform {

	public static void main(String[] args) throws IOException {
		Path waveformIDPath = Paths.get(args[0]);
		Path waveformPath = Paths.get(args[1]);
		BasicID[] ids = BasicIDFile.read(waveformIDPath, waveformPath);
		
		Path clusterPath = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/syntheticPREM_Q165/filtered_stf_12.5-200s/map/cluster-6deg.inf");
		List<EventCluster> clusters = EventCluster.readClusterFile(clusterPath);
		
		Path outpath = Paths.get("misfits.txt");
		try (PrintWriter pw = new PrintWriter(outpath.toFile())) {
			pw.println("station network lat lon event component phase region amp var CC");
			
			for (int i = 0; i < ids.length; i+=2) {
				if (!ids[i].getObserver().equals(ids[i+1].getObserver()) || ! ids[i].getGlobalCMTID().equals(ids[i+1].getGlobalCMTID())) {
//					throw new RuntimeException(ids[i] + "\n" + ids[i+1]);
					System.err.println(ids[i] + "\n" + ids[i+1]);
					continue;
				}
				
				double distance = Math.toDegrees(ids[i].getGlobalCMTID().getEvent().getCmtLocation()
						.getEpicentralDistance(ids[i].getObserver().getPosition()));
				if (distance < 70 || distance > 79)
					continue;
				
				RealVector obsVec = new ArrayRealVector(ids[i].getData());
//				obsVec = obsVec.mapMultiply(1. / 1.11);
				
				RealVector synVec = new ArrayRealVector(ids[i+1].getData());
				RealVector resid = obsVec.subtract(synVec);
				
				double var = resid.dotProduct(resid) / obsVec.dotProduct(obsVec);
				double cc = synVec.dotProduct(obsVec) / (synVec.getNorm() * obsVec.getNorm());
//				double ratio = (synVec.getMaxValue() - synVec.getMinValue()) / (obsVec.getMaxValue() - obsVec.getMinValue());
				double ratio = synVec.getLInfNorm() / obsVec.getLInfNorm();
				
				String region = regionName(ids[i], clusters);
				if (region == null)
					continue;
				
				pw.println(ids[i].getObserver().getStation() + " " + ids[i].getObserver().getNetwork() + " " + ids[i].getObserver().getPosition()
						+ " " + ids[i].getGlobalCMTID() + " " + ids[i].getSacComponent() + " " + new Phases(ids[i].getPhases()) + " " + region
						+ " " + ratio + " " + var + " " + cc);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static String regionName(BasicID id, List<EventCluster> clusters) {
		EventCluster cluster = clusters.stream().filter(c -> c.getID().equals(id.getGlobalCMTID())).findFirst().get();
		double azimuth = Math.toDegrees(id.getGlobalCMTID().getEvent().getCmtLocation().getAzimuth(id.getObserver().getPosition()));
		int iaz = -1;
//		if (azimuth < 180)
//			azimuth += 360;
		for (int i = 0; i < cluster.getNAzimuthSlices(); i++) {
			if (azimuth >= cluster.getAzimuthBound(i)[0] && azimuth < cluster.getAzimuthBound(i)[1])
				iaz = i;
		}
		if (iaz == -1) {
			System.err.println("azimuth slice not found " + id + " " + azimuth);
			return null;
		}
		return String.format("cl%d_az%d", cluster.getIndex(), iaz);
	}

}
