/*

Copyright 2008-2023 E-Hentai.org
https://forums.e-hentai.org/
tenboro@e-hentai.org

This file is part of Hentai@Home GUI.

Hentai@Home GUI is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Hentai@Home GUI is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Hentai@Home GUI.  If not, see <http://www.gnu.org/licenses/>.

*/

package hath.gui;
import hath.base.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class HentaiAtHomeClientGUI extends JFrame implements HathGUI, ActionListener, WindowListener, MouseListener, Runnable {
	private HentaiAtHomeClient client;
	private HHControlPane controlPane;
	private HHLogPane logPane;
	private Thread myThread;
	private JMenuItem refresh_settings, suspend_resume, suspend_5min, suspend_15min, suspend_30min, suspend_1hr, suspend_2hr, suspend_4hr, suspend_8hr;
	private SystemTray tray;
	private TrayIcon trayIcon;
	private boolean trayFirstMinimize;
	private long lastSettingRefresh = 0;
	
	public HentaiAtHomeClientGUI(String[] args) {
		String mainjar = "HentaiAtHome.jar";
		if(! (new java.io.File(mainjar)).canRead()) {
			Out.error("Required JAR file " + mainjar + " could not be found. Please re-download Hentai@Home.");
			System.exit(-1);
		}
	
		try {
    		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    	} catch(Exception e) {
   	 		e.printStackTrace();
			System.exit(-1);
    	}

		Image icon16 = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/src/hath/gui/icon16.png"));
		Image icon32 = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/src/hath/gui/icon32.png"));

		setTitle("Hentai@Home " + Settings.CLIENT_VERSION + " (Build " + Settings.CLIENT_BUILD + ")");
		setIconImage(icon32);
		setSize(1000, 550);
		setResizable(true);
		
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		addWindowListener(this);
		
		// set up the menu bar
		
		JMenuBar mb = new JMenuBar();
		JMenu program = new JMenu("Program");
		JMenu suspend = new JMenu("Suspend");

		// set up the program menu
		refresh_settings = new JMenuItem("Refresh Settings");
		refresh_settings.addActionListener(this);
		refresh_settings.setEnabled(false);
		program.add(refresh_settings);
		program.add(new JSeparator());
		JMenuItem program_exit = new JMenuItem("Shutdown H@H");
		program_exit.addActionListener(this);
		program.add(program_exit);
		
		// set up the suspend menu
		suspend_resume = new JMenuItem("Resume");
		suspend_resume.setEnabled(false);
		suspend_5min = new JMenuItem("Suspend for 5 Minutes");
		suspend_15min = new JMenuItem("Suspend for 15 Minutes");
		suspend_30min = new JMenuItem("Suspend for 30 Minutes");
		suspend_1hr = new JMenuItem("Suspend for 1 Hour");
		suspend_2hr = new JMenuItem("Suspend for 2 Hours");
		suspend_4hr = new JMenuItem("Suspend for 4 Hours");
		suspend_8hr = new JMenuItem("Suspend for 8 Hours");
		
		suspend_resume.addActionListener(this);
		suspend_5min.addActionListener(this);
		suspend_15min.addActionListener(this);
		suspend_30min.addActionListener(this);
		suspend_1hr.addActionListener(this);
		suspend_2hr.addActionListener(this);
		suspend_4hr.addActionListener(this);
		suspend_8hr.addActionListener(this);
		suspend.add(suspend_resume);
		suspend.add(new JSeparator());
		suspend.add(suspend_5min);
		suspend.add(suspend_15min);
		suspend.add(suspend_30min);
		suspend.add(suspend_1hr);
		suspend.add(suspend_2hr);
		suspend.add(suspend_4hr);
		
		setResumeEnabled(false);
		setSuspendEnabled(false);

		mb.add(program);
		mb.add(suspend);
		setJMenuBar(mb);
		
		// initialize the panes
		
		getContentPane().setLayout(new BorderLayout());

		controlPane = new HHControlPane(this);
		getContentPane().add(controlPane, BorderLayout.PAGE_START);
		
		logPane = new HHLogPane();
		getContentPane().add(logPane, BorderLayout.CENTER);
		
		// create the systray
		
		if(SystemTray.isSupported()) {
			trayFirstMinimize = true; // popup the "still running" box the first time the client is minimized to the systray this run
			setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // we'll handle this with the WindowListener

			tray = SystemTray.getSystemTray();
			PopupMenu trayMenu = new PopupMenu();
			
			//MenuItem test = new MenuItem("test");
			//test.addActionListener(this);
			//trayMenu.add(test);
			
			trayIcon = new TrayIcon(icon16, "Hentai@Home", trayMenu);
			trayIcon.addMouseListener(this);
			
			try {
				tray.add(trayIcon);
			} catch(AWTException e) {
				e.printStackTrace();
			}
		}
		
		boolean startVisible = true;
		
		for(String s : args) {
			if(s.equalsIgnoreCase("--silentstart")) {
				if(SystemTray.isSupported()) {
					startVisible = false;
				}
			}
		}

		pack();		
		setVisible(startVisible);
		
		lastSettingRefresh = System.currentTimeMillis();

		myThread = new Thread(this);
		myThread.start();

		try {
			Thread.currentThread().sleep(startVisible ? 2000 : 60000);
		} catch(Exception e) {}
		
		Settings.setActiveGUI(this);
		Stats.trackBytesSentHistory();
		client = new HentaiAtHomeClient(new InputQueryHandlerGUI(this), args);
		setSuspendEnabled(true);
	}
	
	public void run() {
		while(true) {		
			try {
				myThread.sleep(500);
			} catch(Exception e) {}
			
			if(!Stats.isClientSuspended() && suspend_resume.isEnabled()) {
				setResumeEnabled(false);
				setSuspendEnabled(true);
			}
			
			if(!refresh_settings.isEnabled() && lastSettingRefresh < System.currentTimeMillis() - 60000) {
				refresh_settings.setEnabled(true);
			}
		
			controlPane.updateData();
			logPane.checkRebuildLogDisplay();
		}
	}
	
	public void notifyWarning(String title, String text) {
		JOptionPane.showMessageDialog(this, text, title, JOptionPane.WARNING_MESSAGE);
	}

	public void notifyError(String reason) {
		JOptionPane.showMessageDialog(this, reason + "\n\nFor more information, look in the log files found in the data directory.", "Hentai@Home has encountered an error", JOptionPane.ERROR_MESSAGE);
	}
	
	// ActionListener for the JMenuBar
	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();
		
		if(cmd.equals("Refresh Settings")) {
			refresh_settings.setEnabled(false);
			client.getClientAPI().refreshSettings();
		} else if(cmd.equals("Shutdown H@H")) {
			if(client != null) {
				new GUIThreaded(client, GUIThreaded.ACTION_SHUTDOWN);
			}
			else {
				System.exit(0);
			}
		} else if(cmd.equals("Resume")) {
			clientResume();
		} else if(cmd.equals("Suspend for 5 Minutes")) {
			clientSuspend(60 * 5);
		} else if(cmd.equals("Suspend for 15 Minutes")) {
			clientSuspend(60 * 15);
		} else if(cmd.equals("Suspend for 30 Minutes")) {
			clientSuspend(60 * 30);
		} else if(cmd.equals("Suspend for 1 Hour")) {
			clientSuspend(60 * 60);
		} else if(cmd.equals("Suspend for 2 Hours")) {
			clientSuspend(60 * 120);
		} else if(cmd.equals("Suspend for 4 Hours")) {
			clientSuspend(60 * 240);
		} else if(cmd.equals("Suspend for 8 Hours")) {
			clientSuspend(60 * 480);
		} 
	}
	
	// WindowListener for the JFrame
	public void windowActivated(WindowEvent e) {}
	public void windowClosed(WindowEvent e) {}

	public void windowClosing(WindowEvent e) {
		setVisible(false);
		
		if(trayFirstMinimize) {
			trayFirstMinimize = false;
			trayIcon.displayMessage("Hentai@Home is still running", "Click here when you wish to show the Hentai@Home Client", TrayIcon.MessageType.INFO);
		}
	}

	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}
	
	
	// MouseListener for the SystemTray
	public void mouseClicked(MouseEvent e) {
		setVisible(true);	
	}
	
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}

	
	private void clientSuspend(int suspendTimeSeconds) {
		if(client != null && client.getClientAPI() != null) {
			if(client.getClientAPI().clientSuspend(suspendTimeSeconds).getResultText().equals("OK")) {
				setSuspendEnabled(false);
				setResumeEnabled(true);
			}
			else {
				Out.error("Failed to suspend");
			}
		}
		else {
			Out.error("The client is not started, cannot suspend.");
		}
	}
	
	private void clientResume() {
		if(client != null && client.getClientAPI() != null) {
			if(client.getClientAPI().clientResume().getResultText().equals("OK")) {
				setSuspendEnabled(true);
				setResumeEnabled(false);
			}
			else {
				Out.error("Failed to resume");
			}
		}
		else {
			Out.error("The client is not started, cannot resume.");
		}
	}
	
	private void setResumeEnabled(boolean enabled) {
		suspend_resume.setEnabled(enabled);
	}
	
	private void setSuspendEnabled(boolean enabled) {
		suspend_5min.setEnabled(enabled);
		suspend_15min.setEnabled(enabled);
		suspend_30min.setEnabled(enabled);
		suspend_1hr.setEnabled(enabled);
		suspend_2hr.setEnabled(enabled);
		suspend_4hr.setEnabled(enabled);
		suspend_8hr.setEnabled(enabled);
	}
	
	public static void main(String[] args) {
		new HentaiAtHomeClientGUI(args);
	}
}
