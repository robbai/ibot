package ibot.bot.physics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import ibot.bot.step.steps.jump.DoubleJumpStep;
import ibot.bot.utils.Pair;
import ibot.bot.utils.StaticClass;
import ibot.bot.utils.maths.MathsUtils;
import ibot.bot.utils.rl.Constants;

public class JumpPhysics extends StaticClass {

	// https://wikimedia.org/api/rest_v1/media/math/render/svg/8876516f71a06f98f87c759f9df9f4100b1e7072

	public static double[][] doubleTimeZ;
	public static double maxDoubleZ;

	public static double timeZ(double targetZ, double gravity, double holdTime, boolean doubleJump){
		if(doubleJump)
			return fileTimeZ(doubleTimeZ, targetZ);
		double initialVelocity = Constants.JUMP_IMPULSE;
		double z = (initialVelocity * holdTime + 0.5 * (gravity + Constants.JUMP_ACCELERATION) * Math.pow(holdTime, 2));
		if(targetZ > 0 && targetZ < z){
			double time1 = -((Math
					.sqrt(2 * (gravity + Constants.JUMP_ACCELERATION) * targetZ + Math.pow(initialVelocity, 2))
					+ initialVelocity) / (gravity + Constants.JUMP_ACCELERATION));
			double time2 = ((Math
					.sqrt(2 * (gravity + Constants.JUMP_ACCELERATION) * targetZ + Math.pow(initialVelocity, 2))
					- initialVelocity) / (gravity + Constants.JUMP_ACCELERATION));
			return solutionT(time1, time2);
		}
		double holdVelocity = (initialVelocity + (gravity + Constants.JUMP_ACCELERATION) * holdTime);
		double time1 = -((Math.sqrt(2 * gravity * (targetZ - z) + Math.pow(holdVelocity, 2)) + holdVelocity) / gravity);
		double time2 = ((Math.sqrt(2 * gravity * (targetZ - z) + Math.pow(holdVelocity, 2)) - holdVelocity) / gravity);
		return solutionT(time1, time2) + holdTime;
	}

	private static double fileTimeZ(double[][] timeZ, double targetZ){
		for(int i = 0; i < timeZ.length - 1; i++){
			double[] one = timeZ[i];
			double[] two = timeZ[i + 1];

			if(one[1] <= targetZ && two[1] >= targetZ){
				return MathsUtils.lerp(one[0], two[0], (targetZ - one[1]) / (two[1] - one[1]));
			}
		}
		return Double.NaN;
	}

	public static double maxZ(double gravity, double holdTime, boolean doubleJump){
		if(doubleJump)
			return maxDoubleZ;
		final double acceleration = (gravity + Constants.JUMP_ACCELERATION);
		double velocity = Constants.JUMP_IMPULSE;
		double height = (velocity * holdTime + 0.5 * acceleration * Math.pow(holdTime, 2));
		velocity += (acceleration * holdTime);
		if(doubleJump){
			height += (velocity * DoubleJumpStep.DOUBLE_JUMP_DELAY
					+ 0.5 * gravity * Math.pow(DoubleJumpStep.DOUBLE_JUMP_DELAY, 2));
			velocity += (gravity * DoubleJumpStep.DOUBLE_JUMP_DELAY) + Constants.JUMP_IMPULSE;
		}
		return height - Math.pow(velocity, 2) / (2 * gravity);
	}

	private static double solutionT(double time1, double time2){
		if(time1 <= 0 || Double.isNaN(time1))
			return time2;
		if(time2 <= 0 || Double.isNaN(time2))
			return time1;
		return Math.min(time1, time2);
	}

	public static Pair<Double[][], Double> loadFile(ClassLoader classLoader, String fileName){
		InputStream in = classLoader.getResourceAsStream(fileName);
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		try{
			ArrayList<String> lines = new ArrayList<String>();
			while(reader.ready()){
				lines.add(reader.readLine());
			}

			Double[][] data = new Double[lines.size()][2];
			double max = Double.MIN_VALUE;
			for(int i = 0; i < lines.size(); i++){
				String[] split = lines.get(i).split(":");
				data[i][0] = Double.parseDouble(split[0]);
				data[i][1] = Double.parseDouble(split[1]);
				max = Math.max(max, data[i][1]);
			}
			return new Pair<Double[][], Double>(data, max);
		}catch(IOException e){
			e.printStackTrace();
		}
		return null;
	}

}
