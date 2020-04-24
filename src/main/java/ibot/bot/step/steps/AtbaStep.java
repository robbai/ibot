package ibot.bot.step.steps;

import java.awt.Color;

import ibot.bot.input.Bundle;
import ibot.bot.intercept.SeamIntercept;
import ibot.bot.step.Priority;
import ibot.input.Car;
import ibot.output.Output;

public class AtbaStep extends DriveStep {

	public AtbaStep(Bundle bundle){
		super(bundle);
	}

	@Override
	public Output getOutput(){
		if(this.bundle.info.groundIntercept == null){
			this.target = this.bundle.packet.ball.position;
		}else if(this.bundle.info.groundIntercept instanceof SeamIntercept){
			Car car = this.bundle.packet.car;
			this.target = ((SeamIntercept)this.bundle.info.groundIntercept).seamPosition.setDistanceFrom(car.position,
					800);
			this.bundle.pencil.renderer.drawLine3d(Color.GREEN, this.bundle.packet.car.position, this.target);
		}else{
			this.target = this.bundle.info.groundIntercept.intersectPosition;
			this.bundle.pencil.renderer.drawLine3d(Color.RED, this.bundle.packet.car.position, this.target);
		}
		return super.getOutput();
	}

	@Override
	public int getPriority(){
		return Priority.IDLE;
	}

}
