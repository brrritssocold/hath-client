/*

Copyright 2008-2012 E-Hentai.org
http://forums.e-hentai.org/
ehentai@gmail.com

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

package org.hath.gui;
import org.hath.base.*;

import java.awt.*;
import javax.swing.*;

public class HHLogPane extends JPanel implements OutListener {
	
	private JTextArea textArea;

	public HHLogPane() {
		setLayout(new BorderLayout());
		
		textArea = new JTextArea("");
		textArea.setFont(new Font("Courier", Font.PLAIN, 11));
		textArea.setEditable(false);
		textArea.setLineWrap(false);
		textArea.setWrapStyleWord(true);
		addText("Hentai@Home GUI " + Settings.CLIENT_VERSION + " initializing...");
		addText("The client will automatically start up momentarily...");
		
		JScrollPane taHolder = new JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,	JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		taHolder.setPreferredSize(new Dimension(1000, 450));
		taHolder.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Program Output"), BorderFactory.createEmptyBorder(5,5,5,5)), taHolder.getBorder()));

		add(taHolder, BorderLayout.CENTER);
		
		Out.addOutListener(this);
	}
	
	public void outputWritten(String entry) {
		addText(entry);
	}

	public synchronized void addText(String toAdd) {
		int linecount = textArea.getLineCount();
		if(linecount > 48) {
			try {
				textArea.replaceRange("", 0, textArea.getLineEndOffset(1));
			} catch(javax.swing.text.BadLocationException e) {
				e.printStackTrace();
			}
		}
		
		textArea.append(toAdd +"\n");
		
		try {
			textArea.setCaretPosition(textArea.getText().length());
		} catch(java.lang.NullPointerException e) {
			// Java 7 sometimes throws up when doing getText()..
		}
	}
}
