package ibot.boost;

import java.io.IOException;
import java.util.ArrayList;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.BoostPadState;
import rlbot.flat.FieldInfo;
import rlbot.flat.GameTickPacket;
import ibot.vectors.Vector2;

/**
 * Information about where boost pads are located on the field and what status
 * they have.
 */
public class BoostManager {

	private static final ArrayList<BoostPad> orderedBoosts = new ArrayList<>();
	private static final ArrayList<BoostPad> fullBoosts = new ArrayList<>();
	private static final ArrayList<BoostPad> smallBoosts = new ArrayList<>();

	public static ArrayList<BoostPad> getAllBoosts(){
		return orderedBoosts;
	}

	public static ArrayList<BoostPad> getFullBoosts(){
		return fullBoosts;
	}

	public static ArrayList<BoostPad> getSmallBoosts(){
		return smallBoosts;
	}

	private static void loadFieldInfo(FieldInfo fieldInfo){
		synchronized(orderedBoosts){

			orderedBoosts.clear();
			fullBoosts.clear();
			smallBoosts.clear();

			for(int i = 0; i < fieldInfo.boostPadsLength(); i++){
				rlbot.flat.BoostPad rawPad = fieldInfo.boostPads(i);
				BoostPad pad = new BoostPad(rawPad);

				orderedBoosts.add(pad);
				if(pad.isFullBoost()){
					fullBoosts.add(pad);
				}else{
					smallBoosts.add(pad);
				}
			}
		}
	}

	public static void loadGameTickPacket(GameTickPacket packet){
		final int boostPadStatesLength = packet.boostPadStatesLength();

		while(boostPadStatesLength > orderedBoosts.size()){
			try{
				loadFieldInfo(RLBotDll.getFieldInfo());
			}catch(IOException e){
				e.printStackTrace();
				return;
			}
			try{
				Thread.sleep(100);
			}catch(InterruptedException e){
				e.printStackTrace();
			}
		}

		for(int i = 0; i < boostPadStatesLength; i++){
			BoostPadState padState = packet.boostPadStates(i);
			BoostPad pad = orderedBoosts.get(i);
			pad.setActive(padState.isActive());
			pad.setTimer(padState.timer());
		}
	}

	public static BoostPad closestActive(Vector2 location, boolean big){
		ArrayList<BoostPad> boosts = (big ? getFullBoosts() : getSmallBoosts());
		BoostPad closestPad = null;
		double closestDistance = Double.MAX_VALUE;
		for(BoostPad pad : boosts){
			if(!pad.isActive())
				continue;
			double distance = pad.getLocation().distance(location);
			if(distance < closestDistance){
				closestDistance = distance;
				closestPad = pad;
			}
		}
		return closestPad;
	}

}
