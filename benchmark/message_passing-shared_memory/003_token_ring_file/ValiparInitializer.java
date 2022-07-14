import java.nio.file.*;
import java.nio.charset.Charset;
import java.io.*;
import java.util.List;
import java.nio.ByteBuffer;

public class ValiparInitializer {
	public static Path FILE_PATH;
	private static String PORT = "port";
	private static String SEPARATOR = "_";

	static {
		 FILE_PATH = Paths.get("/tmp/.server" + System.getenv("VALIPAR_EXECUTION_ID"));
	}

	/*
	 * This method synchronize the begin of the clients.
	 * It can be used to asure that clients send messages
	 * only after the server socket is opened.
	 *
	 * Use:
	 * <Code>
	 * DatagramSocket socket = new DatagramSocket(9999);
	 * ValiparInitializer.notifyClients();
	 */
	public static void notifyClients() throws IOException {
        Files.createFile(FILE_PATH);
	}

	public static void notifyClients(int pid) throws IOException {
		notifyClients(String.valueOf(pid));
	}

	public static void notifyClients(String pos) throws IOException {
		Path path = Paths.get(FILE_PATH.toString() + SEPARATOR + pos);
        Files.createFile(path);
	}

	/*
	 * This method force the wait of the client for the
	 * server socket.
	 */
	public static void waitServer() throws IOException {
		while (!Files.exists(FILE_PATH));
	}

	public static void waitServer(String pos) throws IOException {
		Path path = Paths.get(FILE_PATH.toString() + SEPARATOR + pos);
		while (!Files.exists(path));
	}

	public static void clean() throws IOException {
		Files.deleteIfExists(FILE_PATH);
	}

	public static void clean(String pos) throws IOException {
		Path path = Paths.get(FILE_PATH.toString() + SEPARATOR + pos);
		Files.deleteIfExists(path);
	}

	public static void publishPort(int pid, int port) {
		Path path = Paths.get(FILE_PATH.toString() + SEPARATOR + pid + SEPARATOR + PORT);
		 try {
            PrintWriter out = new PrintWriter(Files.newOutputStream(path), true);
            out.println(port);
        } catch (IOException ex) {
            throw new RuntimeException("VALIPAR: " + path + " could not be openned", ex);
        }
	}

	public static int getPort(int pidTarget) {
		Path path = Paths.get(FILE_PATH.toString() + SEPARATOR + pidTarget + SEPARATOR + PORT);
		List<String> lines; 
		try {
			lines = Files.readAllLines(path, Charset.defaultCharset());
		} catch (IOException ex) {
			throw new RuntimeException("VALIPAR: " + path + " could not be openned", ex);
		}
		return Integer.parseInt(lines.get(0).trim());
	}
	
    public static ByteBuffer getBuffer(byte[] sendMessage) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(128);
        buffer.put(sendMessage);
        buffer.flip();
        return buffer;
    }
}