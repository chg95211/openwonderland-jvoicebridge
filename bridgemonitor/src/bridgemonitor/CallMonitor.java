/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License version 2 as 
 * published by the Free Software Foundation and distributed hereunder 
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this 
 * code. 
 */

package bridgemonitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import com.sun.voip.PerfMon;
import com.sun.voip.DataUpdater;

import java.awt.Point;

public class CallMonitor {

    public static final int RECEIVE_MONITOR_PORT = 7777;

    private Socket socket;
    private OutputStream output;
    private BufferedReader bufferedReader;

    private CallMonitorListener listener;

    private ReceivedPacketsMonitor receivedPacketsMonitor;

    private AverageReceiveTimeMonitor averageReceiveTimeMonitor;

    private MissingPacketsMonitor missingPacketsMonitor;

    private JitterMonitor jitterMonitor;

    public static void main(String[] args) {
	if (args.length != 2) {
	    System.out.println("Usage:  java <bridge server> <callId>");
	    System.exit(1);
	}

	try {
	    new CallMonitor(args[0], args[1]);
	} catch (IOException e) {
	    System.out.println(e.getMessage());
	}
    }

    public CallMonitor(String server, String callId) throws IOException {
	this(new Point(0, 0), 0, null, server, callId);
    }

    public CallMonitor(Point location, int height, CallMonitorListener listener,
	    String server, String callId) throws IOException {

	this.listener = listener;

	socket = new Socket();

	InetAddress ia;

	try {
	    ia = InetAddress.getByName(server);
	} catch (UnknownHostException e) {
	    throw new IOException("Unknown host " + server + " "
		+ e.getMessage());
	}

        String s = System.getProperty("bridgemonitor.RECEIVE_MONITOR_PORT");

        int port = RECEIVE_MONITOR_PORT;

        if (s != null) {
            try {
                port = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                System.out.println("Invalid ReceiveMonitor port:  "
                    + e.getMessage() + ".  Defaulting to " + port);
            }
        }

	socket.connect(new InetSocketAddress(ia, port));

        output = socket.getOutputStream();

        bufferedReader = new BufferedReader(
	    new InputStreamReader(socket.getInputStream()));

	callId += "\n";

	output.write(callId.getBytes());

	time = System.currentTimeMillis();

	averageReceiveTimeMonitor = new AverageReceiveTimeMonitor(
	    new Point((int) location.getX() + 330, 
	    (int) location.getY() + height));

	receivedPacketsMonitor = new ReceivedPacketsMonitor(
	    new Point((int)location.getX(), (int)location.getY() + height));

	missingPacketsMonitor = new MissingPacketsMonitor(
	    new Point((int) location.getX() + 660,
	    (int) location.getY() + height));

	jitterMonitor = new JitterMonitor(
	    new Point((int) location.getX() + 990,
	    (int) location.getY() + height));
    }

    public void setVisible(boolean isVisible) {
	averageReceiveTimeMonitor.setVisible(isVisible);
	receivedPacketsMonitor.setVisible(isVisible);
	missingPacketsMonitor.setVisible(isVisible);
	jitterMonitor.setVisible(isVisible);
    }

    public void readBridgePerformanceData() {
	String s = null;

	try {
            s = bufferedReader.readLine();

	    if (s == null) {
		done();
		return;
	    }

	    if (s.indexOf("Invalid callId") >= 0) {
		System.out.println(s);
		done();
		return;
	    }

	    if (s.indexOf("CallEnded") >= 0) {
		System.out.println(s);
		done();
		return;
	    }

	    String[] tokens = s.split(":");

	    if (tokens.length != 3) {
		System.out.println("Missing data:  " + s);
		done();
		return;
	    }

	    String pattern = "PacketsReceived=";

	    int ix = tokens[0].indexOf(pattern);

	    if (ix < 0) {
		System.out.println("Missing " + pattern + " " + s);
		done();
		return;
	    }

	    try {
		packetsReceived = Integer.parseInt(
		    tokens[0].substring(ix + pattern.length()));
	    } catch (NumberFormatException e) {
		System.out.println("Invalid number for received packets:  "
		    + s);
		done();
		return;
	    }
		
	    pattern = "MissingPackets=";

	    ix = tokens[1].indexOf(pattern);

	    if (ix < 0) {
		System.out.println("Missing " + pattern + " " + s);
		done();
		return;
	    }

	    try {
		missingPackets = Integer.parseInt(
		    tokens[1].substring(ix + pattern.length()));
	    } catch (NumberFormatException e) {
		System.out.println("Invalid number for missing packets:  "
		    + s);
		done();
		return;
	    }

	    pattern = "JitterBufferSize=";

	    ix = tokens[2].indexOf(pattern);

	    if (ix < 0) {
		System.out.println("Missing " + pattern + " " + s);
		done();
	    }

	    try {
		jitter = Integer.parseInt(
		    tokens[2].substring(ix + pattern.length()));
	    } catch (NumberFormatException e) {
		System.out.println("Invalid number for jitter:  " + s);
		done();
		return;
	    }
	} catch (IOException e) {
	    System.err.println("can't read socket! " 
		+ socket + " " + e.getMessage());
	    done();
	    return;
	}
    }

    public void quit() {
	done();
    }

    private void done() {
        receivedPacketsMonitor.quit();

        averageReceiveTimeMonitor.quit();

	missingPacketsMonitor.quit();

        jitterMonitor.quit();

	if (listener != null) {
	    listener.callMonitorDone();
	}
    }

    private int packetsReceived;
    private int missingPackets;
    private int jitter;
    private long time;

    public int getPacketsReceived() {
	return packetsReceived;
    }

    public int getMissingPackets() {
	return missingPackets;
    }

    public int getJitter() {
	return jitter;
    }

    public void setTime(long time) {
	synchronized (this) {
	    this.time = time;
	}
    }

    public long getTime() {
	return time;
    }

    class ReceivedPacketsMonitor implements DataUpdater {
        private PerfMon monitor;

        private int packetsReceived;

	public ReceivedPacketsMonitor(Point location) {
	    monitor = new PerfMon("Received Packets", this,
		location, 330, 110);
	}
	
	public void setVisible(boolean isVisible) {
	    monitor.setVisible(isVisible);
	}

	public int getData() {
	    readBridgePerformanceData();

	    int p = getPacketsReceived();

	    int n = p - packetsReceived;
	
	    packetsReceived = p;

	    averageReceiveTimeMonitor.setPacketsReceived(n);

	    return n;
	}

	public void windowClosed() {
	    quit();
	}

	public void quit() {
	    monitor.stop();
	}
    }

    class AverageReceiveTimeMonitor implements DataUpdater {
        private PerfMon monitor;

        private int packetsReceived;

	public AverageReceiveTimeMonitor(Point location) {
	    monitor = new PerfMon("Average Receive Time", this,
		location, 330, 110);
	}
	
	public void setVisible(boolean isVisible) {
	    monitor.setVisible(isVisible);
	}

	public void setPacketsReceived(int packetsReceived) {
	    this.packetsReceived = packetsReceived;
	}

	public int getData() {
	    long time = System.currentTimeMillis();

	    long elapsed = time - getTime();

	    if (packetsReceived == 0) {
		setTime(time);
		return 0;
	    }

	    int avg = (int) (elapsed / packetsReceived);

	    //System.out.println("elapsed " + elapsed + " packetsReceived " 
	    //    + packetsReceived + " avg " + avg);

	    setTime(time);
	    return avg;
	}

	public void windowClosed() {
	    quit();
	}

	public void quit() {
	    monitor.stop();
	}
    }

    class MissingPacketsMonitor implements DataUpdater {
        private PerfMon monitor;

        private int missingPackets;

	public MissingPacketsMonitor(Point location) {
	    monitor = new PerfMon("Missing Packets", this,
		location, 330, 110);
	}
	
	public void setVisible(boolean isVisible) {
	    monitor.setVisible(isVisible);
	}

	public int getData() {
	    int m = getMissingPackets();

	    int n = m - missingPackets;
	
	    missingPackets = m;

	    return n;
	}

	public void windowClosed() {
	    quit();
	}

	public void quit() {
	    monitor.stop();
	}
    }

    class JitterMonitor implements DataUpdater {
        private PerfMon monitor;

        private int jitter;

	public JitterMonitor(Point location) {
	    monitor = new PerfMon("Jitter", this,
		location, 330, 110);
	}
	
	public void setVisible(boolean isVisible) {
	    monitor.setVisible(isVisible);
	}

	public int getData() {
	    return getJitter();
	}

	public void windowClosed() {
	    quit();
	}

	public void quit() {
	    monitor.stop();
	}
    }

}
