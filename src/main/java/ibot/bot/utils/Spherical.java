package ibot.bot.utils;

import ibot.vectors.Vector3;

public class Spherical {

	public final double polar, azimuthal, radial;

	public Spherical(double polar, double azimuthal, double radial){
		this.polar = polar;
		this.azimuthal = azimuthal;
		this.radial = radial;
	}

	public Spherical(Vector3 vector){
		this.polar = Math.atan2(vector.y, vector.x);
		this.radial = vector.magnitude();
		if(this.radial != 0){
			this.azimuthal = Math.acos(vector.z / this.radial);
		}else{
			this.azimuthal = 0;
		}
	}

	public final double getPerpendicular(){
		return MathsUtils.correctAngle(Math.PI / 2 - this.polar);
	}

	public final double getElevation(){
		return MathsUtils.correctAngle(Math.PI / 2 - this.azimuthal);
	}

	@Override
	public String toString(){
		return "(polar=" + MathsUtils.round(this.polar, 2) + ", azimuthal=" + MathsUtils.round(this.azimuthal, 2)
				+ ", radial=" + MathsUtils.round(this.radial, 2) + ")";
	}

	public String toOtherString(){
		return "(perpendicular=" + MathsUtils.round(this.getPerpendicular(), 2) + ", elevation="
				+ MathsUtils.round(this.getElevation(), 2) + ", radial=" + MathsUtils.round(this.radial, 2) + ")";
	}

}
