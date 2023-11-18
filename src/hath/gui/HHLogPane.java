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

import java.lang.StringBuilder;
import java.awt.*;
import javax.swing.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

public class HHLogPane extends JPanel implements OutListener, ComponentListener {
	private final int LOG_LINE_COUNT = 100;
	private JTextArea textArea;
	private String[] loglines;
	private StringBuilder stringBuilder;
	private int logpointer = 0, logLinesSinceRebuild = 0;
	private long lastLogDisplayRebuild = 0;
	private Object logSyncer = new Object();
	private int stringCutoff = 142, displayLineCount = 18;
	private boolean windowResized = false;

	public HHLogPane() {
		loglines = new String[LOG_LINE_COUNT];
		stringBuilder = new StringBuilder(3000);

		setLayout(new BorderLayout());

		textArea = new JTextArea("");
		textArea.setFont(new Font("Courier", Font.PLAIN, 11));
		textArea.setEditable(false);
		textArea.setLineWrap(false);
		addText("Hentai@Home GUI " + Settings.CLIENT_VERSION + " initializing...");
		addText("The client will automatically start up momentarily...");

		JScrollPane taHolder = new JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_NEVER,	JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		taHolder.setPreferredSize(new Dimension(1000, 300));
		//taHolder.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Program Output"), BorderFactory.createEmptyBorder(5,5,5,5)), taHolder.getBorder()));

		add(taHolder, BorderLayout.CENTER);

		Out.addOutListener(this);
		addComponentListener(this);
	}

	public void outputWritten(String entry) {
		addText(entry);
	}

	public void addText(String toAdd) {
		synchronized(logSyncer) {
			if(++logpointer >= LOG_LINE_COUNT) {
				logpointer = 0;
			}

			if(toAdd.length() > stringCutoff) {
				loglines[logpointer] = toAdd.substring(0, stringCutoff);
			}
			else {
				loglines[logpointer] = toAdd;
			}

			++logLinesSinceRebuild;
		}
	}

	public synchronized void checkRebuildLogDisplay() {
		long nowtime = System.currentTimeMillis();

		if(windowResized) {
			windowResized = false;
			stringCutoff = Math.max(stringCutoff, (int) (getWidth() / 7));
			displayLineCount = Math.max(1, Math.min(LOG_LINE_COUNT, (int) (getHeight() / 16)));
		}
		else if( (logLinesSinceRebuild < 1) || (nowtime - lastLogDisplayRebuild < 500) ) {
			return;
		}

		lastLogDisplayRebuild = nowtime;
		logLinesSinceRebuild = 0;
		stringBuilder.setLength(0);
		int displayLineIndex = LOG_LINE_COUNT - displayLineCount;

		// sync to prevent weirdness from threads adding text to the log array while the display text is building
		synchronized(logSyncer) {
			while(++displayLineIndex <= LOG_LINE_COUNT) {
				int logindex = logpointer + displayLineIndex;
				logindex = logindex >= LOG_LINE_COUNT ? logindex - LOG_LINE_COUNT : logindex;

				if(loglines[logindex] != null) {
					stringBuilder.append(loglines[logindex]);
					stringBuilder.append("\n");
				}
			}
		}

		textArea.setText(stringBuilder.toString());
		textArea.setCaretPosition(stringBuilder.length());
	}
	
	public void componentHidden(ComponentEvent event) {}
	public void componentMoved(ComponentEvent event) {}
	public void componentShown(ComponentEvent event) {}
	
	public void componentResized(ComponentEvent event) {
		windowResized = true;
		checkRebuildLogDisplay();
	}
}
