import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {
    private static Map<String, ClientHandler> activeClients = new HashMap<>();
    private static Map<String, List<String>> chatRooms = new HashMap<>();


//    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>();
    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String clientUserName;
    private String currentRoom;

    public ClientHandler(Socket socket){
        try{
            this.socket = socket;
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.clientUserName = bufferedReader.readLine();
//            clientHandlers.add(this);
            activeClients.put(clientUserName, this);
            logUserEntry();
            broadcastMessage("SERVER: " + clientUserName + " has entered the chat!");
        }catch (IOException e) {
            closeEverything(socket, bufferedReader, bufferedWriter);
        }
    }

    @Override
    public void run() {
        String messageFromClient;

        while (socket.isConnected()) {
            try {
                messageFromClient = bufferedReader.readLine();
                if (messageFromClient.startsWith("/join")) {
                    handleJoinRoom(messageFromClient);
                } else if (messageFromClient.startsWith("/history")) {
                    sendChatHistory();
                } else {
                    saveMessageToHistory(messageFromClient);
                    broadcastMessage(messageFromClient);
                }
            } catch (IOException e) {
                closeEverything(socket, bufferedReader, bufferedWriter);
                break;
            }
        }
    }

    private void handleJoinRoom(String command) throws IOException {
        String[] parts = command.split(" ", 2);
        if (parts.length < 2) {
            bufferedWriter.write("SERVER: Invalid room join command. Use /join <room_name>\n");
            bufferedWriter.newLine();
            bufferedWriter.flush();
            return;
        }
        String roomName = parts[1];
        currentRoom = roomName;
        chatRooms.putIfAbsent(roomName, new ArrayList<>());
        chatRooms.get(roomName).add(clientUserName);
        broadcastMessage("SERVER: " + clientUserName + " joined the room: " + roomName);
    }

    private void saveMessageToHistory(String message) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("chat_history.txt", true))) {
            writer.write(clientUserName + ": " + message);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendChatHistory() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader("chat_history.txt"))) {
            String line;
            bufferedWriter.write("--- Chat History ---\n");
            while ((line = reader.readLine()) != null) {
                bufferedWriter.write(line + "\n");
            }
            bufferedWriter.newLine();
            bufferedWriter.flush();
        } catch (IOException e) {
            bufferedWriter.write("SERVER: Error reading chat history.\n");
            bufferedWriter.newLine();
            bufferedWriter.flush();
        }
    }

    private void logUserEntry() {
        System.out.println("User " + clientUserName + " has connected.");
    }

    public void broadcastMessage(String messageToSend){
        for (ClientHandler clientHandler : activeClients.values()) {
            try {
                if (currentRoom == null || currentRoom.equals(clientHandler.currentRoom)) {
                    clientHandler.bufferedWriter.write(messageToSend + "\n");
                    clientHandler.bufferedWriter.newLine();
                    clientHandler.bufferedWriter.flush();
                }
            } catch (IOException e) {
                closeEverything(socket, bufferedReader, bufferedWriter);
            }
        }
    }

//    public void removeClientHandle(){
//        clientHandlers.remove(this);
//        broadcastMessage("SERVER: " + clientUserName + " has left the chat!");
//    }

    public void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter){
        activeClients.remove(clientUserName);
        chatRooms.getOrDefault(currentRoom, new ArrayList<>()).remove(clientUserName);
        broadcastMessage("SERVER: " + clientUserName + " has left the chat!");
        try {
            if (bufferedReader != null) bufferedReader.close();
            if (bufferedWriter != null) bufferedWriter.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
