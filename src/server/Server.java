package server;

import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final String dataPath = System.getProperty("user.dir") + File.separator + "src" + File.separator + "server" + File.separator + "data" + File.separator;
    private static final String fileIdentifiersFilepath = System.getProperty("user.dir") + File.separator + "src" + File.separator + "server" + File.separator + "fileIdentifiers.txt";

    private static Map<Integer, String> fileIdentifiers;
    private static ExecutorService executor;
    private static ServerSocket server;

    public static void main(String[] args) {
        System.out.println("Server started!");
        executor = Executors.newFixedThreadPool(10);

        fileIdentifiers = loadFileIdentifiers();

        String address = "127.0.0.1";
        int port = 23456;

        try {
            server = new ServerSocket(port, 50, InetAddress.getByName(address));
            while (true) {
                Socket socket = server.accept();
                ClientHandler clientHandler = new ClientHandler((socket));
                executor.submit(clientHandler);
            }
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }

    public static void shutdown(){
        executor.shutdown();
        try {
            server.close();
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }

    public static Map<Integer, String> loadFileIdentifiers(){
        Map<Integer, String> fileIdentifiers = new HashMap<>();
        File file = new File(fileIdentifiersFilepath);

        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))){
            String line = bufferedReader.readLine();

            while (line!=null){
                String[] lineSegments = line.split(":");
                String id = lineSegments[0].trim();
                String filename = lineSegments[1].trim();

                if (!id.equals("") && !filename.equals("")) {
                    fileIdentifiers.put(Integer.parseInt(id), filename);
                }

                line = bufferedReader.readLine();
            }
        }
        catch (IOException ignored){}

        fileIdentifiers = Collections.synchronizedMap(fileIdentifiers);
        return fileIdentifiers;
    }

    public static void saveFileIdentifiers(){
        File file = new File(fileIdentifiersFilepath);

        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))){
            for (var entry : fileIdentifiers.entrySet()){
                bufferedWriter.write(entry.getKey() + ":" + entry.getValue());
                bufferedWriter.newLine();
            }

            bufferedWriter.flush();
        }
        catch (IOException ignored){}
    }

    public static int createFile(String filename, byte[] fileContent) {
        Path filePath = Paths.get(dataPath + filename);

        try {
            Files.write(filePath, fileContent,StandardOpenOption.CREATE_NEW);

            return addFileIdentifierEntry(filename);
        }
        catch (IOException e){
            return -1;
        }
    }

    public static int addFileIdentifierEntry(String filename){
        int id = fileIdentifiers.size();
        while (fileIdentifiers.containsKey(id)){
            id++;
        }
        fileIdentifiers.put(id, filename);
        return id;
    }

    public static byte[] getFile(String getByNameOrID, String identifier) {
        try {
            if (Objects.equals(getByNameOrID, "BY_ID")){
                int id = Integer.parseInt(identifier);
                identifier = getFilenameFromID(id);
            }
            Path filePath = Paths.get(dataPath + identifier);

            return Files.readAllBytes(filePath);
        }
        catch (IOException e) {
            return null;
        }
    }

    public static String getFilenameFromID(int id){
        return fileIdentifiers.getOrDefault(id, null);
    }

    public static int getIDFromFilename(String filename){
        for (var entry: fileIdentifiers.entrySet()){
            if (Objects.equals(filename, entry.getValue())){
                return entry.getKey();
            }
        }
        return -1;
    }

    public static int deleteFile(String deleteByNameOrID, String identifier){
        if (Objects.equals(deleteByNameOrID, "BY_ID")){
            int id = Integer.parseInt(identifier);
            identifier = getFilenameFromID(id);
        }
        File file = new File(dataPath + identifier);
        if (file.delete()) {
            return deleteFileIdentifierEntry(getIDFromFilename(identifier))!=-1 ? 0 : -1;
        } else {
            return -1;
        }
    }

    public static int deleteFileIdentifierEntry(int id){
        if (fileIdentifiers.containsKey(id)){
            fileIdentifiers.remove(id);
            return 0;
        } else {
            return -1;
        }
    }

}

class ClientHandler implements Runnable {
    private final Socket socket;

    public ClientHandler(Socket socket){
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());

            /* Receive response from client */
            String action = input.readUTF();
            int responseCode;
            switch (action) {
                case ("EXIT") -> {
                    Server.saveFileIdentifiers();
                    Server.shutdown();
                    socket.close();
                    return;
                }
                case ("PUT") -> {
                    String filename = input.readUTF();

                    int length = input.readInt();
                    byte[] fileContent = new byte[length];
                    input.readFully(fileContent, 0, fileContent.length);

                    int fileID = Server.createFile(filename, fileContent);
                    if (fileID == -1) {
                        responseCode = 403;
                        output.writeInt(responseCode);
                    } else {
                        responseCode = 200;
                        output.writeInt(responseCode);
                        output.writeInt(fileID);
                    }

                }
                case ("GET") -> {
                    String getByNameOrID = input.readUTF();
                    String identifier = input.readUTF();
                    byte[] fileContent = Server.getFile(getByNameOrID, identifier);

                    if (fileContent == null) {
                        responseCode = 404;
                        output.writeInt(responseCode);
                    } else {
                        responseCode = 200;
                        output.writeInt(responseCode);
                        output.writeInt(fileContent.length);
                        output.write(fileContent);
                    }
                }
                case ("DELETE") -> {
                    String deleteByNameOrID = input.readUTF();
                    String identifier = input.readUTF();
                    responseCode = Server.deleteFile(deleteByNameOrID, identifier) == -1 ? 404 : 200;
                    output.writeInt(responseCode);
                }
            }
            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
