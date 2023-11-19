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

import java.util.Arrays;
import java.text.DecimalFormat;
import java.awt.*;
import javax.swing.*;

public class HHControlPane extends JPanel {

	private HentaiAtHomeClientGUI clientGUI;
	private StatPane statPane;
	private GraphPane graphPane;
	private String[] argStrings = { "Client Status:", "Uptime:", "Last Check-In:", "Total Files Sent:", "Total Files Rcvd:", "Total Bytes Sent:", "Total Bytes Rcvd:", "Avg Bytes Sent:", "Avg Bytes Rcvd:", "Cache Filecount:", "Used Cache Size:", "Cache Utilization:", "Free Cache Size:", "Static Ranges:", "Connections:" };
	private int[] argLengths = null;
	private DecimalFormat df;

	public HHControlPane(HentaiAtHomeClientGUI clientGUI) {
		this.clientGUI = clientGUI;
		df = new DecimalFormat("0.00");

		setPreferredSize(new Dimension(1000, 220));
		setLayout(new BorderLayout());

		statPane = new StatPane();
		graphPane = new GraphPane();

		add(statPane, BorderLayout.LINE_START);
		add(graphPane, BorderLayout.CENTER);
	}

	public void updateData() {
		statPane.updateStats();
	}

	private class StatPane extends JPanel implements StatListener {
		private Font myFont;

		public StatPane() {
			super();

			setPreferredSize(new Dimension(350, 220));
			setBorder(BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Program Stats"), BorderFactory.createEmptyBorder(5,5,5,5)), getBorder()));
			Stats.addStatListener(this);

			repaint();
		}

		public void statChanged(String stat) {
			//repaint(200);
		}

		public void updateStats() {
			repaint(50);
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

			int xoff = 10;
			int yoff = 30;
			int yspace = 24;
			int xargwidth = 95;
			int xvalwidth = 70;
			int x1pos = xoff + xargwidth;
			int x2pos = x1pos + xvalwidth + xargwidth;
			
			if(argLengths == null) {
				// FontMetrics is inefficient, so we only want to do this once
				FontMetrics fontMetrics = g2.getFontMetrics();
				argLengths = new int[argStrings.length];
				int i = 0;

				for(String arg : argStrings) {
					argLengths[i++] = fontMetrics.stringWidth(arg);
				}
			}

			int argx = 0, argl = 0;

			for(String arg : argStrings) {
				g2.drawString(arg, (argx % 2 == 0 ? x1pos : x2pos) - argLengths[argl++] - 7, yoff + yspace * (argx / 2));
				argx += argx == 0 ? 2 : 1;
			}

			int lastServerContact = Stats.getLastServerContact();
			long rawbytesSent = Stats.getBytesSent();
			long rawbytesRcvd = Stats.getBytesRcvd();
			int rawbytesSentPerSec = Stats.getBytesSentPerSec();
			int rawbytesRcvdPerSec = Stats.getBytesRcvdPerSec();
			long rawcacheSize = Stats.getCacheSize();
			long rawcacheFree = Stats.getCacheFree();

			g2.drawString(Stats.getProgramStatus(), x1pos, yoff + yspace * 0);
			g2.drawString((int) (Stats.getUptime() / 3600) + " hr " + (int) ((Stats.getUptime() % 3600) / 60) + " min", x1pos, yoff + yspace * 1);
			g2.drawString(lastServerContact == 0 ? "Never" : (int) (System.currentTimeMillis() / 1000) - lastServerContact + " sec ago", x2pos, yoff + yspace * 1);
			g2.drawString(Stats.getFilesSent() + "", x1pos, yoff + yspace * 2);
			g2.drawString(Stats.getFilesRcvd() + "", x2pos, yoff + yspace * 2);
			g2.drawString(StorageUnit.of(rawbytesSent).format(rawbytesSent), x1pos, yoff + yspace * 3);
			g2.drawString(StorageUnit.of(rawbytesRcvd).format(rawbytesRcvd), x2pos, yoff + yspace * 3);
			g2.drawString(StorageUnit.of(rawbytesSentPerSec).format(rawbytesSentPerSec) + "/s", x1pos, yoff + yspace * 4);
			g2.drawString(StorageUnit.of(rawbytesRcvdPerSec).format(rawbytesRcvdPerSec) + "/s", x2pos, yoff + yspace * 4);
			g2.drawString(Stats.getCacheCount() + "", x1pos, yoff + yspace * 5);
			g2.drawString(StorageUnit.of(rawcacheSize).format(rawcacheSize), x2pos, yoff + yspace * 5);
			g2.drawString(df.format(Stats.getCacheFill() * 100) + "%", x1pos, yoff + yspace * 6);
			g2.drawString(StorageUnit.of(rawcacheFree).format(rawcacheFree), x2pos, yoff + yspace * 6);
			g2.drawString(Settings.getStaticRangeCount() + "", x1pos, yoff + yspace * 7);
			g2.drawString(Stats.getOpenConnections() + " / " + Settings.getMaxConnections(), x2pos, yoff + yspace * 7);
		}
	}

	private class GraphPane extends JPanel implements StatListener {
		private short[] graphHeights;
		private long lastGraphRefresh = 0;
		private int peakSpeedKBps = 0;
		private BasicStroke graphStroke, otherStroke;
		private Font myFont;

		public GraphPane() {
			super();

			graphStroke = new BasicStroke(2, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);
			otherStroke = new BasicStroke(1);
			
			setMinimumSize(new Dimension(650, 220));
			//setBorder(BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Beautiful Line"), BorderFactory.createEmptyBorder(5,5,5,5)), getBorder()));
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

			int xoff = 0;
			int adjustedxwidth = getWidth() - 2;
			double blipwidth = adjustedxwidth / 354.0;

			g2.setColor(Color.BLACK);
			g2.fillRect(xoff, 2, adjustedxwidth + 1, 215);

			g2.setColor(Color.GRAY);

			int barwidth = adjustedxwidth / 6;
			int blipBottom = 215;
			int blipTop = 2;

			for(int i=1; i<=5; i++) {
				int xpos = xoff + barwidth * i;
				g2.drawLine(xpos, blipTop, xpos, blipBottom);
			}

			int blipLeftOff = xoff + 1;
			int blipMaxHeight = blipBottom - blipTop;

			if(graphHeights == null || lastGraphRefresh < System.currentTimeMillis() - 10000) {
				if(graphHeights == null) {
					// the bytesSent array has 361 entries with the current decasecond as index 0, so we want to read the data from index 1-360
					// because every entry is the average over the last six entries, we lose the first five entries for the graphHeights array
					graphHeights = new short[354];
				}

				Arrays.fill(graphHeights, (short) 0);

				int[] bytesSent = Stats.getBytesSentHistory();
				double maxBytesSentPerMinute = 6000000;

				if(bytesSent != null) {				
					double initialAverage = bytesSent[360] + bytesSent[359] + bytesSent[358] + bytesSent[357] + bytesSent[356] + bytesSent[355];
					double bytesLastMinute = initialAverage;

					for(int i=360; i>6; i--) {
						bytesLastMinute = bytesLastMinute + bytesSent[i - 6] - bytesSent[i];
						maxBytesSentPerMinute = Math.max(maxBytesSentPerMinute, bytesLastMinute);
					}

					maxBytesSentPerMinute = 1200000 * Math.ceil(maxBytesSentPerMinute / 1200000);
					bytesLastMinute = initialAverage;
					int newi = 0;
					
					for(int i=360; i>6; i--) {
						bytesLastMinute = bytesLastMinute + bytesSent[i - 6] - bytesSent[i];
						graphHeights[newi++] = (short) (blipMaxHeight * bytesLastMinute / maxBytesSentPerMinute);
					}
					
					/*
					java.lang.StringBuilder debug = new java.lang.StringBuilder();
					
					for(short height : graphHeights) {
						debug.append(height + " ");
					}

					Out.debug(debug.toString());
					*/
				}

				peakSpeedKBps = (int) (maxBytesSentPerMinute / 60000);
			}

			// draw the graph line
			g2.setColor(Color.GREEN);
			g2.setStroke(graphStroke);

			int x1 = 0;
			int x2 = (int) Math.round(blipLeftOff + blipwidth * 0);
			int y1 = 0;
			int y2 = Math.round(blipBottom - graphHeights[0]);

			for(int i=1; i<354; i++) {
				x1 = x2;
				x2 = (int) Math.round(blipLeftOff + blipwidth * i);
				y1 = y2;
				y2 = Math.round(blipBottom - graphHeights[i]);
				g2.drawLine(x1, y1, x2, y2);
			}

			// draw speed demarkers
			g2.setColor(Color.LIGHT_GRAY);
			g2.setStroke(otherStroke);

			int s1 = blipTop;
			int s2 = (int) (blipTop + blipMaxHeight * 0.25);
			int s3 = (int) (blipTop + blipMaxHeight * 0.50);
			int s4 = (int) (blipTop + blipMaxHeight * 0.75);

			g2.drawString(peakSpeedKBps + " KB/s", 7, s1 + 12);
			g2.drawLine(xoff, s1, adjustedxwidth + 3, s1);
			g2.drawString((int) (peakSpeedKBps * 0.75) + " KB/s", 7, s2 + 12);
			g2.drawLine(xoff, s2, adjustedxwidth + 3, s2);
			g2.drawString((int) (peakSpeedKBps * 0.5) + " KB/s", 7, s3 + 12);
			g2.drawLine(xoff, s3, adjustedxwidth + 3, s3);
			g2.drawString((int) (peakSpeedKBps * 0.25) + " KB/s", 7, s4 + 12);
			g2.drawLine(xoff, s4, adjustedxwidth + 3, s4);
		}
	}
}
