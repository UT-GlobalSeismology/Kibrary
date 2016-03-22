package io.github.kensuke1984.kibrary.external.gmt;

import io.github.kensuke1984.kibrary.math.geometry.RThetaPhi;
import io.github.kensuke1984.kibrary.math.geometry.XYZ;
import io.github.kensuke1984.kibrary.util.Earth;
import io.github.kensuke1984.kibrary.util.HorizontalPosition;

/**
 * クロスセクションの地表面でのパス
 * @version 0.0.1
 * @author Kensuke Konishi
 *
 */
public class CrossSectionLine {

	
	/**
	 * @param centerLocation  中心位置
	 * @param theta　中心位置からの角度（結果２thetaとる） (rad)
	 * @param azimuth  (rad)
	 * @param deltaTheta  (rad)
	 */
	public CrossSectionLine(HorizontalPosition centerLocation, double theta,
			double azimuth, double deltaTheta) {
		this.centerLocation = centerLocation;
		this.theta = theta;
		this.azimuth = azimuth;
		this.deltaTheta = deltaTheta;
		setPoints();
	}

	/**
	 * 中心点の座標
	 */
	protected HorizontalPosition centerLocation;
	/**
	 * 中心点からの角度（rad） 断面はトータルで2theta分
	 */
	protected double theta;
	/**
	 * 断面の中心点での方位角（rad)
	 */
	protected double azimuth;
	/**
	 * 断面の始点
	 */
	private HorizontalPosition startPoint;
	
	/**
	 * 断面の終点
	 */
	private HorizontalPosition endPoint;
	
	/**
	 * 断面の各点（始点　中点　終点を含む）
	 */
	private HorizontalPosition[] positions;
	
	/**
	 * 断面を切る細かさ
	 */
	protected double deltaTheta;

	/**
	 * スタート点からの震央距離
	 */
	protected double[] thetaX;
	
	
	public double[] getThetaX() {
		return thetaX;
	}




	/**
	 * 与えられた情報から　各点の座標を求める
	 */
	protected void setPoints() {
			int nTheta = (int) Math.round(theta / deltaTheta);
			HorizontalPosition[] positions = new HorizontalPosition[2 * nTheta+1];
			thetaX = new double[2*nTheta+1];
			for(int i=-nTheta;i<nTheta+1;i++){
				if(i<0){
					double theta = -i * deltaTheta;
					XYZ xyz = RThetaPhi.toCartesian(Earth.EARTH_RADIUS, theta, Math.PI);
					xyz = xyz.rotateaboutZ(Math.PI - azimuth); //azimuth だけ回転
					xyz = xyz.rotateaboutY(centerLocation.getTheta());
					xyz = xyz.rotateaboutZ(centerLocation.getPhi());
					positions[i + nTheta] = xyz.getLocation();
	//				System.out.println(xyz.getLocation());
					thetaX[i+nTheta]= -theta;
				}
				else if(i>=0){
					double theta = i* deltaTheta;
					XYZ xyz = RThetaPhi.toCartesian(Earth.EARTH_RADIUS, theta, 0);
					xyz = xyz.rotateaboutZ(Math.PI - azimuth); //azimuth だけ回転
					xyz = xyz.rotateaboutY(centerLocation.getTheta());
					xyz = xyz.rotateaboutZ(centerLocation.getPhi());
					positions[i + nTheta] = xyz.getLocation();
	//				System.out.println(xyz.getLocation());
					thetaX[i+nTheta]= theta;
				}
				
				this.positions = positions;
				
			
				
			}
			
			startPoint = positions[0] ;
			endPoint = positions[positions.length-1] ;
			
			
		}

	public HorizontalPosition getCenterLocation() {
		return centerLocation;
	}

	public double getTheta() {
		return theta;
	}

	public double getAzimuth() {
		return azimuth;
	}

	public HorizontalPosition getStartPoint() {
		return startPoint;
	}

	public HorizontalPosition getEndPoint() {
		return endPoint;
	}

	public HorizontalPosition[] getPositions() {
		return positions;
	}

	public double getDeltaTheta() {
		return deltaTheta;
	}

}
