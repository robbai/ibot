package ibot.bot.bots;

import rlbot.Bot;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.GameTickPacket;
import ibot.boost.BoostManager;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.utils.MathsUtils;
import ibot.input.DataPacket;
import ibot.output.ControlsOutput;
import ibot.prediction.BallPrediction;

public abstract class ABot implements Bot {

	private static boolean ranGc;

	public final int index, team;

	protected Bundle bundle;
	private Info info;

	public ABot(int index, int team){
		super();
		this.index = index;
		this.team = team;

		this.info = new Info(this);

		this.bundle = new Bundle(this, this.info);
	}

	protected abstract ControlsOutput processInput();

	@Override
	public ControlsOutput processInput(GameTickPacket rawPacket){
		if(!rawPacket.gameInfo().isRoundActive()){
			if(!ranGc){
				System.gc();
				ranGc = true;
			}
		}else{
			ranGc = false;
		}

		// Just return immediately if something looks wrong with the data.
		// System.out.println(rawPacket.gameInfo().isKickoffPause() + ", " +
		// rawPacket.gameInfo().isMatchEnded() + ", " +
		// rawPacket.gameInfo().isRoundActive());
		if(rawPacket.playersLength() <= index){
			return new ControlsOutput();
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
		ControlsOutput controls = this.processInput();

		// Post-update our info.
		this.info.postUpdate(packet, controls);

		// Post-update our pencil.
		this.bundle.pencil.postRender();

		return controls;
	}

	@Override
	public void retire(){
		System.out.println("Retiring sample bot " + index);
	}

	public void sendQuickChat(boolean teamOnly, byte... quickChatSelection){
		try{
			RLBotDll.sendQuickChat(this.index, teamOnly,
					quickChatSelection[MathsUtils.random.nextInt(quickChatSelection.length)]);
		}catch(Exception e){
			System.err.println("Error when trying to send quick-chat [" + quickChatSelection.toString() + "]");
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

}
