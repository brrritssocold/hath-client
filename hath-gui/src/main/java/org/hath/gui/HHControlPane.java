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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import org.hath.base.Settings;
import org.hath.base.StatListener;
import org.hath.base.Stats;

public class HHControlPane extends JPanel {
	private static final long serialVersionUID = 6414081823838202123L;
	private HentaiAtHomeClientGUI clientGUI;
	private StatPane statPane;
	private GraphPane graphPane;

	public HHControlPane(HentaiAtHomeClientGUI clientGUI) {
		this.clientGUI = clientGUI;

		setPreferredSize(new Dimension(1000, 220));
		setLayout(new BorderLayout());
		
		statPane = new StatPane();
		graphPane = new GraphPane();
		
		add(statPane, BorderLayout.LINE_START);		
		add(graphPane, BorderLayout.CENTER);		
	}
	
	public void updateData() {
		statPane.updateStats();
		//graphPane.repaint(); - don't do this, it causes random warpings on the graph as well as a fairly high CPU usage.
	}
	
	private class StatPane extends JPanel implements StatListener {
		private static final long serialVersionUID = -4597810266493308911L;
		private Font myFont;
	
		public StatPane() {
			super();
		
			setPreferredSize(new Dimension(500, 220));
			setBorder(BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Program Stats"), BorderFactory.createEmptyBorder(5,5,5,5)), getBorder()));
			Stats.addStatListener(this);
			
			repaint();
		}
		
		public void statChanged(String stat) {
			repaint(10);
		}
		
		public void updateStats() {
			repaint(10);
		}
		
		public void paint(Graphics g) {
			if(!clientGUI.isShowing()) {
				return;
			}
		
			Graphics2D g2 = (Graphics2D) g;
			g2.clearRect(0, 0, getWidth(), getHeight());

			super.paint(g);

			if(myFont == null) {
				myFont = new Font("Sans-serif", Font.PLAIN, 10);
			}
		
			g2.setFont(myFont);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(Color.BLACK);
			
			int xoff = 20;
			int yoff = 30;
			int yspace = 25;
			int xargwidth = 110;
			int xvalwidth = 90;
			
			int x1pos = xoff;
			int x2pos = x1pos + xargwidth;
			int x3pos = x2pos + xvalwidth;
			int x4pos = x3pos + xargwidth;
			
			g2.drawString("Client Status", x1pos, yoff + yspace * 0);
			g2.drawString("Uptime", x1pos, yoff + yspace * 1);
			g2.drawString("Last Server Contact", x3pos, yoff + yspace * 1);
			g2.drawString("Total Files Sent", x1pos, yoff + yspace * 2);
			g2.drawString("Total Files Rcvd", x3pos, yoff + yspace * 2);
			g2.drawString("Total Bytes Sent", x1pos, yoff + yspace * 3);
			g2.drawString("Total Bytes Rcvd", x3pos, yoff + yspace * 3);
			g2.drawString("Avg Bytes Sent/Sec", x1pos, yoff + yspace * 4);
			g2.drawString("Avg Bytes Rcvd/Sec", x3pos, yoff + yspace * 4);
			g2.drawString("Files In Cache", x1pos, yoff + yspace * 5);
			g2.drawString("Used Cache Size", x3pos, yoff + yspace * 5);
			g2.drawString("Cache Utilization", x1pos, yoff + yspace * 6);
			g2.drawString("Free Cache Space", x3pos, yoff + yspace * 6);
			
			java.text.DecimalFormat df = new java.text.DecimalFormat("0.00");
			
			String szclientStartTime = (int) (Stats.getUptime() / 3600) + " hr " + (int) ((Stats.getUptime() % 3600) / 60) + " min";
			int lastServerContact = Stats.getLastServerContact();
			String szserverContact = lastServerContact == 0 ? "Never" : (int) (System.currentTimeMillis() / 1000) - lastServerContact + " sec ago";
			long rawbytesSent = Stats.getBytesSent();
			String szbytesSent = StorageUnit.of(rawbytesSent).format(rawbytesSent);
			long rawbytesRcvd = Stats.getBytesRcvd();
			String szbytesRcvd = StorageUnit.of(rawbytesRcvd).format(rawbytesRcvd);
			int rawbytesSentPerSec = Stats.getBytesSentPerSec();
			String szbytesSentPerSec = StorageUnit.of(rawbytesSentPerSec).format(rawbytesSentPerSec) + "/s";
			int rawbytesRcvdPerSec = Stats.getBytesRcvdPerSec();
			String szbytesRcvdPerSec = StorageUnit.of(rawbytesRcvdPerSec).format(rawbytesRcvdPerSec) + "/s";
			long rawcacheSize = Stats.getCacheSize();
			String szcacheSize = StorageUnit.of(rawcacheSize).format(rawcacheSize);
			float rawcacheFill = Stats.getCacheFill();
			String szcacheFill = df.format(rawcacheFill * 100) + "%";
			long rawcacheFree = Stats.getCacheFree();
			String szcacheFree = StorageUnit.of(rawcacheFree).format(rawcacheFree);
		
			g2.drawString(Stats.getProgramStatus(), x2pos, yoff + yspace * 0);
			g2.drawString(szclientStartTime, x2pos, yoff + yspace * 1);
			g2.drawString(szserverContact, x4pos, yoff + yspace * 1);
			g2.drawString(Stats.getFilesSent() + "", x2pos, yoff + yspace * 2);
			g2.drawString(Stats.getFilesRcvd() + "", x4pos, yoff + yspace * 2);
			g2.drawString(szbytesSent, x2pos, yoff + yspace * 3);
			g2.drawString(szbytesRcvd, x4pos, yoff + yspace * 3);
			g2.drawString(szbytesSentPerSec, x2pos, yoff + yspace * 4);
			g2.drawString(szbytesRcvdPerSec, x4pos, yoff + yspace * 4);
			g2.drawString(Stats.getCacheCount() + "", x2pos, yoff + yspace * 5);
			g2.drawString(szcacheSize, x4pos, yoff + yspace * 5);
			g2.drawString(szcacheFill, x2pos, yoff + yspace * 6);
			g2.drawString(szcacheFree, x4pos, yoff + yspace * 6);
			
			int currentBytesPerSec = Stats.getCurrentBytesPerSec();
			int maxBytesPerSec = Settings.getThrottleBytesPerSec() > 0 ? Settings.getThrottleBytesPerSec() : (Stats.getBytesSentPerSec() > 0 ? Stats.getBytesSentPerSec() * 3 : Integer.MAX_VALUE);
			
			String szopenConnections = Stats.getOpenConnections() + "";
			String szcurrentBytesPerSec = df.format(currentBytesPerSec / 1000.0);
			
			g2.drawRect(410, 40, 30, 150);
			g2.drawString("Conns", 410, 30);
			g2.drawString(szopenConnections, 422 - (szopenConnections.length() - 1) * 3, 203);
			g2.drawRect(450, 40, 30, 150);
			g2.drawString("KBps", 453, 30);
			g2.drawString(szcurrentBytesPerSec, 455 - (szcurrentBytesPerSec.length() - 4) * 3, 203);
			
			drawBlipBar(g2, 415, 45, Stats.getOpenConnections() / (double) Settings.getMaxConnections());
			drawBlipBar(g2, 455, 45, currentBytesPerSec / (double) maxBytesPerSec);
		}
		
		public void drawBlipBar(Graphics2D g2, int xpos, int ypos, double pct) {
			int xwidth = 20;
			int yspace = 2;
			int yheight = 5;
			int bottom = ypos + 140;
			
			int severity = 0;
			int toSeverity = Math.min(20, (int) Math.ceil(pct * 20));

			while(++severity <= toSeverity) {
				if(severity < 11) {
					g2.setColor(Color.GREEN);
				} else if(severity < 16) {
					g2.setColor(Color.ORANGE);
				} else {
					g2.setColor(Color.RED);
				}
				
				int yoff = bottom - (yheight * severity + yspace * (severity - 1));
				g2.fillRect(xpos, yoff, xwidth, yheight);
				
				g2.setColor(Color.BLACK);
				g2.drawRect(xpos, yoff, xwidth, yheight);
			}
		}
	}
	
	private class GraphPane extends JPanel implements StatListener {
		private static final long serialVersionUID = 1328288714655544785L;
		private int[] heights;
		private long lastGraphRefresh = 0;
		private long bytesLast10Sec = 0, bytesLastMin = 0, bytesLast15Min = 0, bytesLast60Min = 0;
		private Font myFont;
		
		public GraphPane() {
			super();
			
			setMinimumSize(new Dimension(500, 220));
			setBorder(BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Beautiful Line"), BorderFactory.createEmptyBorder(5,5,5,5)), getBorder()));
			Stats.addStatListener(this);
			
			repaint();
		}

		public void statChanged(String stat) {
			if(stat.equals("bytesSentHistory")) {
				repaint();
			}
		}
	
		public void paint(Graphics g) {
			if(!clientGUI.isShowing()) {
				return;
			}
		
			Graphics2D g2 = (Graphics2D) g;
			g2.clearRect(0, 0, getWidth(), getHeight());

			super.paint(g);

			if(myFont == null) {
				myFont = new Font("Sans-serif", Font.PLAIN, 10);
			}
		
			g2.setFont(myFont);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.clearRect(15, 15, getWidth() - 20, 180);
		
			int xoff = 10;
			int xwidth = getWidth() - 20;
		
			g2.setColor(Color.BLACK);
			g2.fillRect(xoff, 20, xwidth, 170);
			
			g2.setColor(Color.GREEN);
			g2.drawRect(xoff, 20, xwidth, 170);
			
			g2.setColor(Color.GRAY);
			
			int barwidth = xwidth / 6;
			
			for(int i=1; i<=5; i++) {
				int xpos = xoff + barwidth * i;
				g2.drawLine(xpos, 21, xpos, 189);
			}

			double blipwidth = (xwidth - 2) / 359.0;
			int blipLeftOff = xoff + 1;
			int blipBottom = 189;
			int blipTop = 21;
			int blipMaxHeight = blipBottom - blipTop;
			int topSpeedKBps = -1;
			
			if(heights == null || lastGraphRefresh < System.currentTimeMillis() - 10000) {
				// re-calculate stuff
			
				heights = new int[360];
				int[] bytesSent = Stats.getBytesSentHistory();
				
				bytesLast10Sec = bytesSent[1];
				bytesLastMin = 0;
				bytesLast15Min = 0;
				bytesLast60Min = 0;
				
				// this correction was made to avoid the blips that are caused by the packets-instead-of-bytes measurements, but the method was changed so it's no longer necessary
				//int forcedMaxBytesPerDecaSec = Settings.getThrottleBytesPerSec() > 0 ? Settings.getThrottleBytesPerSec() * 10 : Integer.MAX_VALUE;

				// we'll use the throttle if set, and guess based on the outgoing speed the server has if not
				double guessedMaxBytesPerDecaSec = 0;
				
				if(Settings.getThrottleBytesPerSec() > 0 || Stats.getBytesSentPerSec() > 0) {
					guessedMaxBytesPerDecaSec = Math.min(Settings.getThrottleBytesPerSec() * 10.0, Stats.getBytesSentPerSec() * 30.0);
				}
				
				if(guessedMaxBytesPerDecaSec == 0) {
					guessedMaxBytesPerDecaSec = 200000.0;
				}

				double actualMaxBytesPerDecaSec = 0;
				
				int newi = 0;
				for(int i=360; i>0; i--) {	// rly
					int actualBytesSent = bytesSent[i]; //Math.min(forcedMaxBytesPerDecaSec, bytesSent[i]);
					heights[newi++] = (int) Math.round((actualBytesSent / guessedMaxBytesPerDecaSec) * blipMaxHeight);
					actualMaxBytesPerDecaSec = Math.max(actualBytesSent, actualMaxBytesPerDecaSec);

					bytesLast60Min += actualBytesSent;			
					if(i <= 90) {
						bytesLast15Min += actualBytesSent;
						if(i <= 6) {
							bytesLastMin += actualBytesSent;
						}
					}
				}
				
				// if our guess was too low, correct the graph. this should never happen if the throttle is set, but could occur if we guess based on historic speed.
				if(actualMaxBytesPerDecaSec > guessedMaxBytesPerDecaSec) {
					double correction = guessedMaxBytesPerDecaSec / actualMaxBytesPerDecaSec;
					
					for(int i=0; i<360; i++) {
						heights[i] = (int) Math.round(heights[i] * correction);
					}
				}
				
				topSpeedKBps = (int) Math.ceil(Math.max(guessedMaxBytesPerDecaSec, actualMaxBytesPerDecaSec) / 10000);
			}
			
			if(heights != null) {
				// draw graphs
			
				g2.setColor(Color.GREEN);
				
				int x1 = 0;
				int x2 = (int) Math.round(blipLeftOff + blipwidth * 0);
				int y1 = 0;
				int y2 = Math.round(blipBottom - heights[0]);

				for(int i=0; i<359; i++) {
					x1 = x2;
					x2 = (int) Math.round(blipLeftOff + blipwidth * (i+1));
					y1 = y2;
					y2 = Math.round(blipBottom - heights[i+1]);				
					g2.drawLine(x1, y1, x2, y2);
				}
				
				g2.setColor(Color.PINK);
				
				int average = (heights[0] + heights[1] + heights[2] + heights[3] + heights[4] + heights[5]) / 6;

				x1 = 0;
				x2 = (int) Math.round(blipLeftOff + blipwidth * 0);
				y1 = 0;
				y2 = Math.round(blipBottom - average);				
				
				for(int i=0; i<359; i++) {
					if(i < 354) {
						average = Math.max(0, average + (heights[i + 6] - heights[i]) / 6);
					}
					
					x1 = x2;
					x2 = (int) Math.round(blipLeftOff + blipwidth * (i+1));
					y1 = y2;
					y2 = Math.round(blipBottom - average);
					g2.drawLine(x1, y1, x2, y2);
				}
				
				// draw the stats below the graph
				
				g2.setColor(Color.BLACK);
				
				java.text.DecimalFormat df = new java.text.DecimalFormat("0.00");
				
				long uptime = Stats.getUptime();
				g2.drawString("60 min: " + (!Stats.isClientRunning() || uptime < 3600 ? "N/A" : df.format(bytesLast60Min / 3600000.0)) + " KB/s",  10, 205);
				g2.drawString("15 min: " + (!Stats.isClientRunning() || uptime < 900  ? "N/A" : df.format(bytesLast15Min / 900000.0))  + " KB/s", 135, 205);
				g2.drawString("1 min: "  + (!Stats.isClientRunning() || uptime < 60   ? "N/A" : df.format(bytesLastMin   / 60000.0))   + " KB/s", 260, 205);
				g2.drawString("Last: "   + (!Stats.isClientRunning()                  ? "N/A" : df.format(bytesLast10Sec / 10000.0))   + " KB/s", 385, 205);
			}
			
			if(topSpeedKBps > -1) {
				// draw speed bars
			
				g2.setColor(Color.RED);
				
				int y1 = blipTop + 12;
				int y2 = (int) (blipTop + blipMaxHeight * 0.25) + 12;
				int y3 = (int) (blipTop + blipMaxHeight * 0.50) + 12;
				int y4 = (int) (blipTop + blipMaxHeight * 0.75) + 12; 
				
				g2.drawString(topSpeedKBps + " KB/s", 15, y1);
				g2.drawLine(11, y1 - 11, xwidth + 9, y1 - 11);
				g2.drawString((int) (topSpeedKBps * 0.75) + " KB/s", 15, y2);
				g2.drawLine(11, y2 - 11, xwidth + 9, y2 - 11);
				g2.drawString((int) (topSpeedKBps * 0.5) + " KB/s", 15, y3);
				g2.drawLine(11, y3 - 11, xwidth + 9, y3 - 11);
				g2.drawString((int) (topSpeedKBps * 0.25) + " KB/s", 15, y4);
				g2.drawLine(11, y4 - 11, xwidth + 9, y4 - 11);			
			}
		}	
	}
}
