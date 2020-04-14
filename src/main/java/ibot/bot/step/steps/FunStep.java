package ibot.bot.step.steps;

import java.awt.Color;

import ibot.bot.controls.AirControl;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.input.Pencil;
import ibot.bot.stack.PopStack;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.output.Output;
import ibot.vectors.Vector3;

public class FunStep extends Step {

	public FunStep(Bundle bundle){
		super(bundle);
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		double time = packet.time;
		Car car = packet.car;

		pencil.stackRenderString("Round: " + packet.isRoundActive, Color.GRAY);

		if(!canHaveFun(this.bundle))
			return new PopStack();

		if(car.hasWheelContact)
			return new Controls().withJump(time % 0.4 < 0.2);

		Vector3 forward = car.velocity.flatten().withAngleZ(Math.toRadians(60));
		double sign = ((car.index % 2) * 2 - 1);
		double[] orient = AirControl.getRollPitchYaw(car, forward, car.orientation.right.scale(sign));
		return new Controls().withOrient(orient).withBoost(time % 0.2 < 0.1 || car.velocity.z < 0)
				.withJump(car.position.z > 400 && time % 0.2 < 0.1);
	}

	@Override
	public int getPriority(){
		return Priority.FUN;
	}

	public static boolean canHaveFun(Bundle bundle){
		DataPacket packet = bundle.packet;
		Info info = bundle.info;
		if(packet.isKickoffPause || (packet.score < 2 && !packet.hasMatchEnded))
			return false;
		return info.goingInEnemyGoal && (info.earliestEnemyIntercept == null
				|| info.earliestEnemyIntercept.time + 0.6 > info.goalTime.getAsDouble());
	}

}
