package ibot.output;

import rlbot.ControllerState;

public class ControlsOutput extends Output implements ControllerState {

	// 0 is straight, -1 is hard left, 1 is hard right.
	private float steer;

	// 0 is straight, -1 is hard left, 1 is hard right.
	private float roll;

	// -1 for front flip, 1 for back flip
	private float pitch;

	// 0 is straight, -1 is hard left, 1 is hard right.
	private float yaw;

	// 0 is none, -1 is backwards, 1 is forwards
	private float throttle;

	private boolean jump;
	private boolean boost;
	private boolean handbrake;
	private boolean useItem;

	public ControlsOutput(){
	}

	public ControlsOutput(ControlsOutput other){
		this.steer = other.steer;
		this.roll = other.roll;
		this.pitch = other.pitch;
		this.yaw = other.yaw;
		this.throttle = other.throttle;
		this.jump = other.jump;
		this.boost = other.boost;
		this.handbrake = other.handbrake;
		this.useItem = other.useItem;
	}

	public ControlsOutput withSteer(double steer){
		this.steer = clamp(steer);
		return this;
	}

	public ControlsOutput withPitch(double pitch){
		this.pitch = clamp(pitch);
		return this;
	}

	public ControlsOutput withYaw(double yaw){
		this.yaw = clamp(yaw);
		return this;
	}

	public ControlsOutput withRoll(double roll){
		this.roll = clamp(roll);
		return this;
	}

	public ControlsOutput withThrottle(double throttle){
		this.throttle = clamp(throttle);
		return this;
	}

	public ControlsOutput withOrient(double[] orient){
		this.roll = clamp(orient[0]);
		this.pitch = clamp(orient[1]);
		this.yaw = clamp(orient[2]);
		return this;
	}

	public ControlsOutput withJump(boolean jump){
		this.jump = jump;
		return this;
	}

	public ControlsOutput withBoost(boolean boost){
		this.boost = boost;
		return this;
	}

	public ControlsOutput withHandbrake(boolean handbrake){
		this.handbrake = handbrake;
		return this;
	}

	public ControlsOutput withUseItem(boolean useItem){
		this.useItem = useItem;
		return this;
	}

	public ControlsOutput withJump(){
		this.jump = true;
		return this;
	}

	public ControlsOutput withBoost(){
		this.boost = true;
		return this;
	}

	public ControlsOutput withHandbrake(){
		this.handbrake = true;
		return this;
	}

	public ControlsOutput withUseItem(){
		this.useItem = true;
		return this;
	}

	private float clamp(double value){
		return (float)Math.max(-1, Math.min(1, value));
	}

	@Override
	public float getSteer(){
		return steer;
	}

	@Override
	public float getThrottle(){
		return throttle;
	}

	@Override
	public float getPitch(){
		return pitch;
	}

	@Override
	public float getYaw(){
		return yaw;
	}

	@Override
	public float getRoll(){
		return roll;
	}

	@Override
	public boolean holdJump(){
		return jump;
	}

	@Override
	public boolean holdBoost(){
		return boost;
	}

	@Override
	public boolean holdHandbrake(){
		return handbrake;
	}

	@Override
	public boolean holdUseItem(){
		return useItem;
	}

	@Override
	public boolean isControls(){
		return true;
	}

	@Override
	public boolean isAction(){
		return false;
	}

}
