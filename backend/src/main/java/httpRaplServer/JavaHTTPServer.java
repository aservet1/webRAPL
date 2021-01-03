package httpRaplServer;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import java.net.ServerSocket;
import java.net.Socket;

import java.util.Date;
import java.util.StringTokenizer;

import jRAPL.SyncEnergyMonitor;
import jRAPL.EnergyDiff;
import jRAPL.EnergyStats;

// Original code copied from SSaurel's Blog: 
// https://www.ssaurel.com/blog/create-a-simple-http-web-server-in-java
// Each Client Connection will be managed in a dedicated Thread
public class JavaHTTPServer implements Runnable { 

	// port to listen connection
	static final int PORT = 8080;

	// verbose mode
	static final boolean verbose = true;

	// Client Connection via Socket Class
	private Socket connect;
	private static SyncEnergyMonitor energyMonitor;

	public JavaHTTPServer(Socket c) { connect = c; }

	public static void main(String[] args) {
		execCmd("modprobe msr");
		energyMonitor = new SyncEnergyMonitor();
		energyMonitor.init();
		startServer();
		energyMonitor.dealloc();
	}

    /** Right now only used for 'sudo modprobe msr'.
     *   But can be used for any simple / non compound 
     *   (|&&>;)-like commands. Simple ones.
     */
    private static void execCmd(String command) {
		String s = null;

        try {
            Process p = Runtime.getRuntime().exec(command);
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            // read the output from the command
            ///System.out.println("Here is the standard output of the command:\n");
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
            }
            // read any errors from the attempted command
            ///System.out.println("Here is the standard error of the command (if any):\n");
            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
            }
            return;
        }
        catch (IOException e) {
            System.out.println("<<IOException in execCmd():");
            e.printStackTrace();
            System.exit(-1);
        }
	}
	
	private static void startServer() {
		try {
			ServerSocket serverConnect = new ServerSocket(PORT);
			System.out.println("Server started.\nListening for connections on port : " + PORT + " ...\n");

			// we listen until user halts server execution			
			while (true) {
				JavaHTTPServer myServer = new JavaHTTPServer(serverConnect.accept());
				
				if (verbose) System.out.println("Connecton opened. (" + new Date() + ")");
				
				// create dedicated thread to manage the client connection
				Thread thread = new Thread(myServer);
				thread.start();
			}	
			//serverConnect.close(); -- unreachable
		} catch (IOException e) {
			System.err.println("Server Connection error : " + e.getMessage());
		}
	}

	@Override
	public void run() {
		// we manage our particular client connection
		BufferedReader in = null;
		PrintWriter headerOut = null;
		BufferedOutputStream dataOut = null;

		try {
			String fileRequested = null;

			// we read characters from the client via input stream on the socket
			in = new BufferedReader(new InputStreamReader(connect.getInputStream()));
			// we get character output stream to client (for headers)
			headerOut = new PrintWriter(connect.getOutputStream());
			// get binary output stream to client (for requested data)
			dataOut = new BufferedOutputStream(connect.getOutputStream());

			// get first line of the request from the client
			String input = in.readLine();

			// we parse the request with a string tokenizer
			StringTokenizer parse = new StringTokenizer(input);
			String method = parse.nextToken().toUpperCase(); // we get the HTTP method of the client
			// we get file requested
			fileRequested = parse.nextToken().toLowerCase();

			// we support only GET and HEAD methods, we check
			if (!method.equals("GET")  &&  !method.equals("HEAD")) {
				if (verbose) System.out.println("501 Not Implemented : " + method + " method.");
				// we send HTTP Headers to client
				sendHTTPHeader(headerOut, "HTTP/1.1 501 Not Implemented", 0);
			} else {

				if (method.equals("GET")) { // GET method so we return content
					byte[] response; int len;
					switch (fileRequested) {
						case "/energy":
							response = energyMonitor.getObjectSample(1).toJSON().getBytes();
							len = response.length;
							if (verbose) System.out.println(new String(response));
							break;
						case "/energy10s":
							EnergyStats before, after;
							before = energyMonitor.getObjectSample(1);
							try { Thread.sleep(10000); } catch (Exception ex) { ex.printStackTrace(); }
							after = energyMonitor.getObjectSample(1);
							response = EnergyDiff.between(before, after).toJSON().getBytes();
							len = response.length;
							if (verbose) System.out.println(new String(response));
							break;
						default:
							response = "<h2>invalid page requested</h2>".getBytes();
							if (verbose) System.out.println(new String(response));
							len = response.length;
					}
					// send HTTP Headers
					sendHTTPHeader(headerOut, "HTTP/1.1 200 OK", len);				
					sendHTTPResponse(dataOut, response, len);
				}
			}
		} catch (IOException ioe) {
			System.err.println("Server error : " + ioe);
		} finally {
			try {
				in.close();
				headerOut.close();
				dataOut.close();
				connect.close(); // we close socket connection
			} catch (Exception e) {
				System.err.println("Error closing stream : " + e.getMessage());
			} 
			if (verbose) System.out.println("Connection closed.\n");
		}
	}

	private void sendHTTPHeader(PrintWriter headerOut, String firstLine, int fileLength)
	{
		headerOut.println(firstLine);
		headerOut.println("Server: Java HTTP Server for RAPL Energy Requests : 1.0");
		headerOut.println("Date: " + new Date());
		headerOut.println("Content-type: " + "something");
		headerOut.println("Content-length: " + fileLength);
		headerOut.println(); // blank line between headers and content, very important !
		headerOut.flush(); // flush character output stream buffer
	}

	private void sendHTTPResponse(BufferedOutputStream dataOut, byte[] response, int len) throws IOException
	{
		dataOut.write(response, 0, len);
		dataOut.flush();
	}
}