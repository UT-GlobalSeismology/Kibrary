package io.github.kensuke1984.kibrary.quick;

import java.io.IOException;
import java.nio.file.Paths;

import org.apache.commons.math3.complex.Complex;

import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.spc.SPCFileAccess;
import io.github.kensuke1984.kibrary.util.spc.FormattedSPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCTensorComponent;
import io.github.kensuke1984.kibrary.util.spc.SPCFile;

public class LookAtBPspc {

	public static void main(String[] args) throws IOException {
		SPCFileName spcName = new FormattedSPCFileName(Paths.get(args[0]));
		
		SPCFileAccess dsmOutput = null;
		if (args.length == 1)
			dsmOutput = spcName.read();
		else if (args.length == 2) {
			double phi = Double.parseDouble(args[1]);
			System.out.println("Phi = " + phi);
			dsmOutput = SPCFile.getInstance(spcName, phi);
		}
		
		print(dsmOutput);
	}
	
	public static void print(SPCFileAccess dsmOutput) {
		String obsName = dsmOutput.getStationCode();
		String netwkName = dsmOutput.getNetworkCode();
		String sourceID = dsmOutput.getSourceID();
		HorizontalPosition observerPosition = dsmOutput.getObserverPosition();
		FullPosition sourceLocation = dsmOutput.getSourcePosition();
		
		double distance = sourceLocation.computeEpicentralDistanceRad(observerPosition) * 180. / Math.PI;
		
		System.out.println("Epicentral distance = " + distance);
		System.out.println("#Observer: " + obsName + " " + netwkName + " " + observerPosition + " Source: " + sourceID + " " + sourceLocation);
		
		SPCTensorComponent c1 = SPCTensorComponent.valueOf27Conmponent(1, 1, 2);
		SPCTensorComponent c2 = SPCTensorComponent.valueOf27Conmponent(1, 2, 2);
		SPCTensorComponent c3 = SPCTensorComponent.valueOf27Conmponent(1, 3, 2);
		SPCTensorComponent c4 = SPCTensorComponent.valueOf27Conmponent(2, 1, 2);
		SPCTensorComponent c5 = SPCTensorComponent.valueOf27Conmponent(2, 2, 2);
		SPCTensorComponent c6 = SPCTensorComponent.valueOf27Conmponent(2, 3, 2);
		SPCTensorComponent c7 = SPCTensorComponent.valueOf27Conmponent(3, 1, 2);
		SPCTensorComponent c8 = SPCTensorComponent.valueOf27Conmponent(3, 2, 2);
		SPCTensorComponent c9 = SPCTensorComponent.valueOf27Conmponent(3, 3, 2);
		
//		SpcTensorComponent c1 = SpcTensorComponent.valueOfBP(1, 1, 3);
//		SpcTensorComponent c2 = SpcTensorComponent.valueOfBP(1, 2, 3);
//		SpcTensorComponent c3 = SpcTensorComponent.valueOfBP(1, 3, 3);
//		SpcTensorComponent c4 = SpcTensorComponent.valueOfBP(2, 1, 3);
//		SpcTensorComponent c5 = SpcTensorComponent.valueOfBP(2, 2, 3);
//		SpcTensorComponent c6 = SpcTensorComponent.valueOfBP(2, 3, 3);
//		SpcTensorComponent c7 = SpcTensorComponent.valueOfBP(3, 1, 3);
//		SpcTensorComponent c8 = SpcTensorComponent.valueOfBP(3, 2, 3);
//		SpcTensorComponent c9 = SpcTensorComponent.valueOfBP(3, 3, 3);
		
		Complex[] spcC1 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c1).getValueInFrequencyDomain(); 
		Complex[] spcC2 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c2).getValueInFrequencyDomain(); 
		Complex[] spcC3 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c3).getValueInFrequencyDomain(); 
		Complex[] spcC4 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c4).getValueInFrequencyDomain(); 
		Complex[] spcC5 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c5).getValueInFrequencyDomain();
		Complex[] spcC6 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c5).getValueInFrequencyDomain();
		Complex[] spcC7 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c5).getValueInFrequencyDomain();
		Complex[] spcC8 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c5).getValueInFrequencyDomain();
		Complex[] spcC9 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c5).getValueInFrequencyDomain();
		double[] rs = dsmOutput.getBodyR();
		System.out.println("#perturbation radius= " + rs[0]);
		for (int i = 0; i < spcC1.length; i++) {
			System.out.printf("(Real) %d %.16e %.16e %.16e %.16e %.16e %.16e %.16e %.16e %.16e\n", i, spcC1[i].getReal(), spcC2[i].getReal(), spcC3[i].getReal()
					,spcC4[i].getReal(), spcC5[i].getReal(),spcC6[i].getReal(), spcC7[i].getReal(),spcC8[i].getReal(), spcC9[i].getReal());
			System.out.printf("(Imag) %d %.16e %.16e %.16e %.16e %.16e %.16e %.16e %.16e %.16e\n", i, spcC1[i].getImaginary(), spcC2[i].getImaginary(), spcC3[i].getImaginary()
					,spcC4[i].getImaginary(), spcC5[i].getImaginary(),spcC6[i].getImaginary(), spcC7[i].getImaginary(),spcC8[i].getImaginary(), spcC9[i].getImaginary());
		}
	}
	
	public static void printHeader(SPCFileAccess dsmOutput) {
		String obsName = dsmOutput.getStationCode();
		String netwkName = dsmOutput.getNetworkCode();
		String sourceID = dsmOutput.getSourceID();
		HorizontalPosition observerPosition = dsmOutput.getObserverPosition();
		FullPosition sourceLocation = dsmOutput.getSourcePosition();
		
		System.out.println("#Observer: " + obsName + " " + netwkName + " " + observerPosition + " Source: " + sourceID + " " + sourceLocation);
	}
}