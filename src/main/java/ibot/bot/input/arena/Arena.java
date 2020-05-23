package ibot.bot.input.arena;

import java.util.ArrayList;
import java.util.List;

import ibot.bot.utils.Mode;
import ibot.bot.utils.Plane;
import ibot.input.DataPacket;
import ibot.vectors.Vector3;

public abstract class Arena {

	protected final List<Plane> planes;

	protected double width, length, height;

	protected Vector3 gravity;

	private Mode mode;

	public Arena(Mode mode, int size){
		this.mode = mode;
		this.planes = new ArrayList<Plane>(size);
	}

	public Plane getClosestPlane(Vector3 vec){
		Plane closestPlane = null;
		double closest = Double.MAX_VALUE;
		for(Plane plane : this.planes){
			double distance = plane.getNormalDistance(vec);
			if(distance >= 0 && distance < closest){
				closestPlane = plane;
				closest = distance;
			}
		}
		return closestPlane;
	}

	public void update(DataPacket packet){
		if(this.gravity == null || this.gravity.z != packet.gravity)
			this.gravity = Vector3.Z.scale(packet.gravity);
	}

	public Mode getMode(){
		return this.mode;
	}

	public Vector3 getGravity(){
		return this.gravity;
	}

	public double getGravityAcc(){
		return this.gravity.z;
	}

	public double getWidth(){
		return this.width;
	}

	public double getLength(){
		return this.length;
	}

	public double getHeight(){
		return this.height;
	}

	public Plane getFloor(){
		return this.planes.get(0);
	}

}
