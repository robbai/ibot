package ibot.bot.bots;

import java.awt.Color;
import java.util.ArrayList;

import rlbot.Bot;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.GameTickPacket;
import ibot.boost.BoostManager;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.stack.PopStack;
import ibot.bot.stack.PushStack;
import ibot.bot.stack.StackAction;
import ibot.bot.stack.SwapStack;
import ibot.bot.step.Step;
import ibot.bot.utils.MathsUtils;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.output.Output;
import ibot.prediction.BallPrediction;

public abstract class ABot implements Bot {

	private static boolean ranGc;

	public final int index, team;
	public final double sign;

	protected Bundle bundle;
	private Info info;

	private ArrayList<Step> steps;

	public ABot(int index, int team){
		super();
		this.index = index;
		this.team = team;

		this.sign = Car.determineSign(team);

		this.info = new Info(this);
		this.bundle = new Bundle(this, this.info);

		this.steps = new ArrayList<Step>();
	}

	@Override
	public Controls processInput(GameTickPacket rawPacket){
		if(!rawPacket.gameInfo().isRoundActive()){
			if(!ranGc){
				System.gc();
				ranGc = true;
			}
		}else{
			ranGc = false;
		}

		// Just return immediately if something looks wrong with the data.
		if(rawPacket.playersLength() <= this.index){
			return new Controls();
		}

		// Update the boost manager.
		BoostManager.loadGameTickPacket(rawPacket);

		// Update the ball prediction.
		BallPrediction.update();

		// Create the packet.
		DataPacket packet = new DataPacket(rawPacket, index);
		this.bundle.packet = packet;

		// Update our pencil.
		this.bundle.pencil.preRender(this.bundle);

		// Update our info.
		this.info.update(packet);

		// Get our output.
		Controls controls = this.getControls();

		// Post-update our info.
		this.info.postUpdate(packet, controls);

		// Post-update our pencil.
		this.bundle.pencil.postRender();

		return controls;
	}

	private Controls getControls(){
		for(Step step : this.steps){
			this.bundle.pencil.stackRenderString(step.getClass().getSimpleName(), Color.WHITE);
		}

		for(int i = 0; i < 10; i++){
			Step foundStep = this.fallbackStep();
			if(foundStep != null && foundStep.getPriority() > this.stepsPriority()){
				this.clearSteps();
				this.steps.add(foundStep);
			}

			Step activeStep = this.getActiveStep();

			Output output = activeStep.getOutput();
			if(output instanceof StackAction){
				if(output instanceof PopStack){
					this.popStep();
				}else if(output instanceof PushStack){
					if(output instanceof SwapStack && this.steps.size() > 0){
						this.popStep();
					}
					this.steps.add(((PushStack)output).step);
				}
			}else if(output instanceof Step){
				this.steps.add((Step)output);
			}else if(output instanceof Controls){
				if(activeStep.isFinished()){
					this.popStep();
				}else{
					Controls controls = (Controls)output;
					for(int j = this.steps.size() - 2; j >= 0; j--){
						this.steps.get(j).manipulateControls(controls);
					}
					return controls;
				}
			}
		}

		Step activeStep = this.getActiveStep();
		System.out.println(this.printPrefix() + "Couldn't get controls from "
				+ (activeStep != null ? activeStep.getClass().getSimpleName() : "null"));
		return new Controls();
	}

	protected abstract Step fallbackStep();

	@Override
	public void retire(){
		System.out.println(this.printPrefix() + "Retiring");
	}

	public void sendQuickChat(boolean teamOnly, byte... quickChatSelection){
		try{
			RLBotDll.sendQuickChat(this.index, teamOnly,
					quickChatSelection[MathsUtils.random.nextInt(quickChatSelection.length)]);
		}catch(Exception e){
			System.err.println(this.printPrefix() + "Error sending quick-chat [" + quickChatSelection.toString() + "]");
		}
	}

	public void sendQuickChat(byte... quickChatSelection){
		this.sendQuickChat(false, quickChatSelection);
	}

	/*
	 * Necessary for the framework, sadly.
	 */
	public int getIndex(){
		return this.index;
	}

	public String printPrefix(){
		return "[" + this.index + "] ";
	}

	protected int stepsPriority(){
		int priority = Integer.MIN_VALUE;
		for(Step step : this.steps){
			priority = Math.max(priority, step.getPriority());
		}
		return priority;
	}

	protected Step getActiveStep(){
		if(this.steps.size() == 0){
			return null;
		}
		return this.steps.get(this.steps.size() - 1);
	}

	public void clearSteps(){
		this.steps.clear();
	}

	private void popStep(){
		if(this.steps.size() > 0){
			this.steps.remove(this.steps.size() - 1);
		}
	}

}
