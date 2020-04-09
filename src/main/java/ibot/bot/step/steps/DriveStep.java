package ibot.bot.step.steps;

import ibot.bot.controls.Handling;
import ibot.bot.input.Bundle;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.Constants;
import ibot.output.Output;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class DriveStep extends Step {

	protected Vector3 target;

	public DriveStep(Bundle bundle, Vector3 target){
		super(bundle);
		this.target = target;
	}

	public DriveStep(Bundle bundle, Vector2 target){
		this(bundle, target.withZ(Constants.CAR_HEIGHT));
	}

	@Override
	public Output getOutput(){
		this.setFinished(this.target.distance(this.bundle.packet.car.position) < 75);
		return Handling.drive(this.bundle, this.target, true, false);
	}

	@Override
	public int getPriority(){
		return Priority.DRIVE;
	}

}
