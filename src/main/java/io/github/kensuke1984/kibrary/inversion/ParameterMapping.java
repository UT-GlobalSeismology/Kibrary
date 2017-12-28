package io.github.kensuke1984.kibrary.inversion;

import io.github.kensuke1984.kibrary.util.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.Location;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sleepycat.bind.tuple.IntegerBinding;

import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParameterMapping {
	private UnknownParameter[] originalUnknowns;
	private UnknownParameter[] unknowns;
	private int[] iOriginalToNew;
	private double[] radii;
	private int[][] iNewToOriginal;
	private Path input;
	
	public static void main(String[] args) throws IOException {
		Path unknownParameterPath = Paths.get("unknowns.inf");
		Path parameterMappingPath = Paths.get("unknownMapping.inf");
		
		UnknownParameter[] originalUnknowns = UnknownParameterFile.read(unknownParameterPath).toArray(new UnknownParameter[0]);
		ParameterMapping mapping = new ParameterMapping(originalUnknowns, parameterMappingPath);
		
		UnknownParameter[] newUnknowns = mapping.getUnknowns();
		
//		for (int iNew = 0; iNew < newUnknowns.length; iNew++) {
//			System.out.println(newUnknowns[iNew]);
//			for (int iOriginal : mapping.getiNewToOriginal(iNew))
//				System.out.println("--> " + originalUnknowns[iOriginal]);
//		}
		
//		for (UnknownParameter unknown: mapping.getUnknowns()) {
//			System.out.println(unknown);
//		}
	}
	
	public ParameterMapping(UnknownParameter[] originalUnknowns, Path input) {
		this.originalUnknowns = originalUnknowns;
		this.input = input;
		try {
			read(input);
		} catch (IOException e) {
			e.printStackTrace();
		}
		make();
	}
	
	public ParameterMapping(UnknownParameter[] originalUnknowns) {
		this.originalUnknowns = originalUnknowns;
		this.unknowns = originalUnknowns;
		this.iOriginalToNew = new int[unknowns.length];
		this.iNewToOriginal = new int[unknowns.length][];
		
		for (int i = 0; i < unknowns.length; i++) {
			iOriginalToNew[i] = i;
			iNewToOriginal[i] = new int[] {i};
		}
		
		this.input = null;
	}
	
	public void read(Path input) throws IOException {
		iOriginalToNew = new int[originalUnknowns.length];
		
		List<UnknownParameter> originalUnknownList = new ArrayList<>();
		for (UnknownParameter p : originalUnknowns)
			originalUnknownList.add(p);
		
		List<Integer> radiiOriginalToNewIndex = new ArrayList<>();
		List<Double> radii = new ArrayList<>();
		
		BufferedReader br = Files.newBufferedReader(input);
		String line;
		int iNewUnknown = -1;
		while((line=br.readLine()) != null) {
			String[] s = line.trim().split("\\s+");
			double r = Double.parseDouble(s[0]);
			int itmp = Integer.parseInt(s[1]);
			if (iNewUnknown != itmp) {
				if (itmp != iNewUnknown + 1)
					throw new RuntimeException("Unexpected");
				iNewUnknown++;
			}
			
			radiiOriginalToNewIndex.add(iNewUnknown);
			radii.add(r);
		}
		iNewUnknown++;
		
		List<List<Integer>> radiiNewToOriginalIndex = new ArrayList<>();
		for (int i = 0; i < iNewUnknown; i++) {
			List<Integer> tmplist = new ArrayList<>();
			for (int j = 0; j < radiiOriginalToNewIndex.size(); j++) {
				if (radiiOriginalToNewIndex.get(j) == i)
					tmplist.add(j);
			}
			radiiNewToOriginalIndex.add(tmplist);
		}
		
		//---- For debug
//		for (int i = 0; i < radiiOriginalToNewIndex.size(); i++)
//			System.out.println(i + " " + radiiOriginalToNewIndex.get(i));
//		for (int i = 0; i < radiiNewToOriginalIndex.size(); i++) {
//			System.out.println(i);
//			for (int j = 0; j < radiiNewToOriginalIndex.get(i).size(); j++)
//				System.out.println("---> " + radiiNewToOriginalIndex.get(i).get(j));
//		}
		//----
		
		List<HorizontalPosition> horizontalPoints = originalUnknownList.stream().map(p -> p.getLocation().toHorizontalPosition())
			.distinct().collect(Collectors.toList());
		
		for (int i = 0; i < horizontalPoints.size(); i++) {
			HorizontalPosition horizontalPoint = horizontalPoints.get(i);
			for (int k = 0; k < originalUnknowns.length; k++) {
				if (originalUnknowns[k].getLocation().toHorizontalPosition().equals(horizontalPoint)) {
					for (int l = 0; l < radiiOriginalToNewIndex.size(); l++) {
						double r = radii.get(l);
						if (Utilities.equalWithinEpsilon(originalUnknowns[k].getLocation().getR(), r, eps)) {
							iOriginalToNew[k] = radiiOriginalToNewIndex.get(l) * horizontalPoints.size() + i;
						}
					}
				}
			}
		}
		
//		int countNew = -1;
//		for (int i = 0; i < iNewUnknown; i++) {
//			List<Integer> tmplist = radiiNewToOriginalIndex.get(i);
//			for (int j = 0; j < tmplist.size(); j++) {
//				int jj = tmplist.get(j);
//				double r = radii.get(jj);
//				for (int k = 0; k < originalUnknowns.length; k++) {
//					if (Utilities.equalWithinEpsilon(originalUnknowns[k].getLocation().getR(), r, eps)) {
//						if (j == 0)
//							countNew++;
//						iOriginalToNew[k] = countNew;
//					}
//				}
//			}
//		}
//		countNew++;
		
		int nNewUnknown = horizontalPoints.size() * iNewUnknown;
//		System.out.println("nNewUnknown = " + nNewUnknown);
		
		Map<Integer, List<Integer>> iNewToOriginalMap = new HashMap<>();
		for (int i = 0; i < iOriginalToNew.length; i++) {
			Integer key = iOriginalToNew[i];
			
//			System.out.println(i + " " + key);
			
			List<Integer> tmplist;
			if (iNewToOriginalMap.containsKey(key)) {
				tmplist = iNewToOriginalMap.get(key);
				tmplist.add(i);
				iNewToOriginalMap.replace(key, tmplist);
			}
			else {
				tmplist = new ArrayList<>();
				tmplist.add(i);
				iNewToOriginalMap.put(key, tmplist);
			}
		}
		
//		System.out.println(iNewToOriginalMap.size());
		
		iNewToOriginal = new int[nNewUnknown][];
		for (int i = 0; i < nNewUnknown; i++) {
			List<Integer> tmplist = iNewToOriginalMap.get(i);
			iNewToOriginal[i] = new int[tmplist.size()];
			for (int j = 0; j < tmplist.size(); j++)
				iNewToOriginal[i][j] = tmplist.get(j);
		}
		
		//--- For debug
//		for (int i = 0; i < originalUnknowns.length; i++) {
//			System.out.println(i + " " + iOriginalToNew[i]);
//		}
//		for (int i = 0; i < nNewUnknown; i++) {
//			System.out.println(i);
//			for (int j = 0; j < iNewToOriginal[i].length; j++)
//				System.out.println("---> " + iNewToOriginal[i][j]);
//		}
		//---
		
		unknowns = new UnknownParameter[nNewUnknown];
	}
	
	public void make() {
		for (int i = 0; i < unknowns.length; i++) {
			double rmean = 0;
			double weight = 0;
			for (int j = 0; j < iNewToOriginal[i].length; j++) {
				UnknownParameter unknown = originalUnknowns[iNewToOriginal[i][j]];
				rmean += unknown.getLocation().getR();
				weight += unknown.getWeighting();
			}
			rmean /= iNewToOriginal[i].length;
			
			UnknownParameter refUnknown = originalUnknowns[iNewToOriginal[i][0]];
			PartialType type = refUnknown.getPartialType();
			Location location = refUnknown.getLocation().toLocation(rmean);
			UnknownParameter newUnknown = new Physical3DParameter(type, location, weight);
			unknowns[i] = newUnknown;
		}
	}
	
	public UnknownParameter[] getOriginalUnknowns() {
		return originalUnknowns;
	}
	
	public UnknownParameter[] getUnknowns() {
		return unknowns;
	}
	
	public int[] getiOriginalToNew() {
		return iOriginalToNew;
	}
	
	public int getiOriginalToNew(int iOriginal) {
		return iOriginalToNew[iOriginal];
	}
	
	public int[][] getiNewToOriginal() {
		return iNewToOriginal;
	}
	
	public int[] getiNewToOriginal(int iNew) {
		return iNewToOriginal[iNew];
	}
	
	public Path getInput() {
		return input;
	}
	
	final double eps = 1e-6;
}
