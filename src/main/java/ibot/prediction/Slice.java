package ibot.prediction;

import ibot.vectors.Vector3;

public class Slice {
	
	public final Vector3 position;
	
	public final double time;
	
	public Slice(Vector3 position, double time){
		this.position = position;
		this.time = time;
	}

}
