package io.github.kensuke1984.kibrary.inversion;

import io.github.kensuke1984.kibrary.util.spc.PartialType;

/**
 * 
 * 時間シフトに対するパラメタ情報　du/dtに対するm (Am=d)における
 * イベントか観測点名
 * 
 * sideにイベントか観測点名を入れる
 * 
 * @author Kensuke Konishi
 *
 */
public class TimeshiftParameter implements UnknownParameter{
	
	private String side;
	
	@Override
	public String toString(){
		return PartialType.TIME+" "+side;
	}
	
	public TimeshiftParameter(String side) {
		this.side = side;
		throw new UnsupportedOperationException("MATTEMATTE");
	}
	

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((side == null) ? 0 : side.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TimeshiftParameter other = (TimeshiftParameter) obj;
		if (side == null) {
			if (other.side != null)
				return false;
		} else if (!side.equals(other.side))
			return false;
		return true;
	}
	
	@Override
	public double getWeighting() {
		return 1;
	}

	@Override
	public PartialType getPartialType() {
		return PartialType.TIME;
	}



}
