package ibot;

import java.awt.BorderLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import rlbot.manager.BotManager;
import ibot.utils.PortReader;

public class Main {

	private static final Integer DEFAULT_PORT = 56926;
	private static final String LOGO_FILE = "icon.png", MUTATOR_FILE = "zero_gravity_mutator.py";
	private static final boolean RUN_MUTATOR = false;
	private static List<String> arguments;

	public static void main(String[] args){
		System.out.println("Args: " + Arrays.toString(args));
		setArguments(args);

		// Run the mutator script.
		if(RUN_MUTATOR){
			try{
				String command = Main.class.getClassLoader().getResource(MUTATOR_FILE).getFile();
				if(command.startsWith("\\") || command.startsWith("/"))
					command = command.substring(1);
				command = "python " + command;
				Runtime.getRuntime().exec(command);
				System.out.println("Ran: " + command);
			}catch(IOException e){
				e.printStackTrace();
			}
		}

		BotManager botManager = new BotManager();
		botManager.setRefreshRate(120);
		Integer port = PortReader.readPortFromArgs(args).orElseGet(() -> {
			System.out.println("Could not read port from args, using default!");
			return DEFAULT_PORT;
		});

		PythonInterface pythonInterface = new PythonInterface(port, botManager);
		new Thread(pythonInterface::start).start();

		JFrame frame = new JFrame("ibot");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		BorderLayout borderLayout = new BorderLayout();
		panel.setLayout(borderLayout);
		JPanel dataPanel = new JPanel();
		dataPanel.setLayout(new BoxLayout(dataPanel, BoxLayout.Y_AXIS));
		dataPanel.setBorder(new EmptyBorder(0, 10, 0, 0));
		dataPanel.add(new JLabel("Listening on port " + port), BorderLayout.CENTER);
		dataPanel.add(new JLabel("I'm the thing controlling the Java bot, keep me open :)"), BorderLayout.CENTER);
		JLabel botsRunning = new JLabel("Bots running: ");
		dataPanel.add(botsRunning, BorderLayout.CENTER);
		panel.add(dataPanel, BorderLayout.CENTER);
		frame.add(panel);

		URL url = Main.class.getClassLoader().getResource(LOGO_FILE);
		Image image = Toolkit.getDefaultToolkit().createImage(url);
		panel.add(new JLabel(new ImageIcon(image)), BorderLayout.WEST);
		frame.setIconImage(image);

		frame.pack();
		frame.setVisible(true);

		ActionListener myListener = e -> {
			Set<Integer> runningBotIndices = botManager.getRunningBotIndices();

			String botsStr;
			if(runningBotIndices.isEmpty()){
				botsStr = "None";
			}else{
				botsStr = runningBotIndices.stream().sorted().map(i -> "#" + i).collect(Collectors.joining(", "));
			}
			botsRunning.setText("Bots indices running: " + botsStr);
		};

		new Timer(1000, myListener).start();
	}

	public static List<String> getArguments(){
		return arguments;
	}

	public static void setArguments(String[] args){
		arguments = Arrays.asList(args);
	}

}
