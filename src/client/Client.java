package client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.TimeUnit;

public class Client {

    private static final String dataPath = System.getProperty("user.dir") + File.separator + "src" + File.separator + "client" + File.separator + "data" + File.separator;

    public static void main(String[] args) {
        String address = "127.0.0.1";
        int port = 23456;

        try {
            TimeUnit.SECONDS.sleep(1);
        }
        catch (InterruptedException ignored){}

        try (Socket socket = new Socket(InetAddress.getByName(address), port)) {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            Scanner scanner = new Scanner(System.in);

            /* Get input from user */
            int actionCode;
            String action;
            System.out.print("Enter action (1 - get a file, 2 - save a file, 3 - delete a file): ");
            String actionInput = scanner.nextLine();
            if (actionInput.equals("exit")) {
                // terminate program
                action = "EXIT";
                output.writeUTF(action);
                System.out.println("The request was sent.");
                return;
            } else {
                try {
                    actionCode = Integer.parseInt(actionInput);
                }
                catch (NumberFormatException e){
                    return;
                }

                /* Handle input & send request to server depending on type of action */
                switch (actionCode) {
                    case (1) -> {
                        action = "GET";

                        // get by file name or id
                        System.out.print("Do you want to get the file by name or by id (1 - name, 2 - id): ");
                        int getByNameOrID;
                        try {
                            getByNameOrID = Integer.parseInt(scanner.nextLine());
                        }
                        catch (NumberFormatException e) {
                            return;
                        }

                        if (getByNameOrID==1){
                            System.out.print("Enter name: ");
                            String filename = scanner.nextLine();

                            output.writeUTF(action);
                            output.writeUTF("BY_NAME");
                            output.writeUTF(filename);
                        } else if (getByNameOrID==2){
                            System.out.print("Enter id: ");
                            int id;
                            try {
                                id = Integer.parseInt(scanner.nextLine());
                            }
                            catch (NumberFormatException e){
                                return;
                            }

                            output.writeUTF(action);
                            output.writeUTF("BY_ID");
                            output.writeUTF(String.valueOf(id));

                        } else {
                            return;
                        }
                    }
                    case (2) -> {
                        action = "PUT";

                        // get file name
                        System.out.print("Enter name of the file: " );
                        String filename = scanner.nextLine();

                        // get name to save file as on server
                        System.out.print("Enter name of the file to be saved on server: ");
                        String serverFilename = scanner.nextLine();
                        if (Objects.equals(serverFilename, "")){
                            serverFilename = filename;
                        }

                        // get content of file
                        Path filePath = Paths.get(dataPath + filename);
                        byte[] fileContent = Files.readAllBytes(filePath);

                        // transmit file content
                        output.writeUTF(action);
                        output.writeUTF(serverFilename);
                        output.writeInt(fileContent.length);
                        output.write(fileContent);

                    }
                    case (3) -> {
                        action = "DELETE";

                        // get by file name or id
                        System.out.print("Do you want to delete the file by name or by id (1 - name, 2 - id): ");
                        int deleteByNameOrID;
                        try {
                            deleteByNameOrID = Integer.parseInt(scanner.nextLine());
                        }
                        catch (NumberFormatException e) {
                            return;
                        }

                        if (deleteByNameOrID==1){
                            System.out.print("Enter name: ");
                            String filename = scanner.nextLine();

                            output.writeUTF(action);
                            output.writeUTF("BY_NAME");
                            output.writeUTF(filename);

                        } else if (deleteByNameOrID==2){
                            System.out.print("Enter id: ");
                            int id;
                            try {
                                id = Integer.parseInt(scanner.nextLine());
                            }
                            catch (NumberFormatException e){
                                return;
                            }

                            output.writeUTF(action);
                            output.writeUTF("BY_ID");
                            output.writeUTF(String.valueOf(id));

                        } else {
                            return;
                        }

                    }
                }
                System.out.println("The request was sent.");
            }

            /* Receive response from server */
            int responseCode = input.readInt();
            String responseMsg = "";
            switch (responseCode) {
                case (200) -> {
                    switch (actionCode) {
                        case (1) -> {
                            // download file content
                            int length = input.readInt();
                            byte[] fileContent = new byte[length];
                            input.readFully(fileContent, 0, fileContent.length);
                            System.out.println("The file was downloaded!");

                            // save file to hard drive
                            System.out.print("Specify a name for it: ");
                            String savedFilename = scanner.nextLine();
                            Path filePath = Paths.get(dataPath + savedFilename);

                            try {
                                Files.write(filePath, fileContent, StandardOpenOption.CREATE_NEW);
                            }
                            catch (IOException e){
                                return;
                            }

                            responseMsg = "File saved on hard drive!";
                        }
                        case (2) -> {
                            int id = input.readInt();
                            responseMsg = "Response says that file is saved! ID = " + id;
                        }
                        case (3) -> responseMsg = "The response says that this file was deleted successfully!";
                    }
                }
                case (403) -> responseMsg = "The response says that creating the file was forbidden!";
                case (404) -> responseMsg = "The response says that this file is not found!";
            }

            System.out.println(responseMsg);
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
}
