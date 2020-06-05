package ibot.boost;

import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

/**
 * Representation of one of the boost pads on the field.
 */
public class BoostPad {

	private final Vector2 position;
	private final boolean isFullBoost;

	private boolean isActive = false;
	private float timer;

	public BoostPad(rlbot.flat.BoostPad rawPad){
		this.position = new Vector3(rawPad.location()).flatten();
		this.isFullBoost = rawPad.isFullBoost();
	}

	public void setActive(boolean active){
		isActive = active;
	}

	public Vector2 getPosition(){
		return position;
	}

	public boolean isFullBoost(){
		return isFullBoost;
	}

	public boolean isActive(){
		return isActive;
	}

	public float getTimer(){
		return timer;
	}

	public float getTimeLeft(){
		if(this.isActive())
			return 0;
		return (this.isFullBoost ? 10 : 4) - this.timer;
	}

	public void setTimer(float timer){
		this.timer = timer;
	}

}
