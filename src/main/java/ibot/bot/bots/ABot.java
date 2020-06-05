package ibot.bot.bots;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;

import rlbot.Bot;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.GameTickPacket;
import ibot.Main;
import ibot.boost.BoostManager;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.stack.PopStack;
import ibot.bot.stack.PushStack;
import ibot.bot.stack.StackAction;
import ibot.bot.stack.SwapStack;
import ibot.bot.step.Step;
import ibot.bot.utils.maths.MathsUtils;
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

	private String lastStepsString;

	public int iteration;

	public ABot(int index, int team){
		super();
		System.out.println(this.printPrefix() + "Initialising");

		this.index = index;
		this.team = team;

		this.sign = Car.determineSign(team);

		this.info = new Info(this);
		this.bundle = new Bundle(this, this.info);

		this.steps = new ArrayList<Step>();
	}

	@Override
	public Controls processInput(GameTickPacket rawPacket){
		if(Main.isLowestIndex(this.index)){
			if(!rawPacket.gameInfo().isRoundActive()){
				if(!ranGc){
					System.gc();
					ranGc = true;
				}
			}else{
				ranGc = false;
			}
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
		String stepsString = this.stepsString();
		if(!stepsString.equals(this.lastStepsString)){
			System.out.println(this.printPrefix() + stepsString);
			this.lastStepsString = stepsString;
		}

		// Post-update our info.
		this.info.postUpdate(packet, controls);

		// Post-update our pencil.
		this.bundle.pencil.postRender(this.bundle);

		return controls;
	}

	private Controls getControls(){
		for(Step step : this.steps){
			this.bundle.pencil.stackRenderString(step.getClass().getSimpleName(), Color.WHITE);
		}

		final int maxIterations = 10;
		ArrayList<Step> triedSteps = new ArrayList<Step>(maxIterations);

		for(this.iteration = 0; this.iteration < maxIterations; this.iteration++){
			Output fallback = this.fallback();
			if(fallback != null){
				if(fallback instanceof Step){
					if(((Step)fallback).getPriority() > this.stepsPriority()){
						this.clearSteps();
						this.steps.add((Step)fallback);
					}
				}else if(fallback instanceof Controls){
					if(this.getActiveStep() == null)
						return (Controls)fallback;
				}
			}

			Step activeStep = this.getActiveStep();
			if(activeStep == null)
				continue;

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

			triedSteps.add(activeStep);
		}

		Step activeStep = this.getActiveStep();
		System.err.println(this.printPrefix() + "Couldn't get controls from "
				+ (activeStep != null ? activeStep.getClass().getSimpleName() : "null") + ", tried "
				+ stepsString(triedSteps));
		return new Controls();
	}

	protected abstract Output fallback();

	@Override
	public void retire(){
		System.out.println(this.printPrefix() + "Retiring");
	}

	public void sendQuickChat(boolean teamOnly, byte... quickChatSelection){
		try{
			RLBotDll.sendQuickChat(this.index, teamOnly,
					quickChatSelection[MathsUtils.RAND.nextInt(quickChatSelection.length)]);
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

	private static String stepsString(ArrayList<Step> steps){
		ArrayList<String> strings = new ArrayList<String>(steps.size());
		for(Step step : steps){
			strings.add(step.getClass().getSimpleName());
		}
		return Arrays.toString(strings.toArray());
	}

	private String stepsString(){
		return stepsString(this.steps);
	}

	/*
	 * step.getClass().getName()
	 */
	protected Step findStep(String stepClassName){
		for(Step step : this.steps){
			if(step.getClass().getName().equals(stepClassName)){
				return step;
			}
		}
		return null;
	}

}
