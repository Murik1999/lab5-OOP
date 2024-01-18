import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ChatApplication extends JFrame {
    private static final int SERVER_PORT = 11111;

    private String username;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private JTextArea chatArea;
    private JTextField messageField;
    private JComboBox<String> clientList;

    private List<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatApplication().setVisible(true));
    }

    public ChatApplication() {
        setTitle("Chat Application");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initializeComponents();
        setupServer();

        // Вводим имя пользователя
        username = JOptionPane.showInputDialog("Введите ваше имя:");
        connectToServer();
    }

    private void initializeComponents() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BorderLayout());

        messageField = new JTextField();
        inputPanel.add(messageField, BorderLayout.CENTER);

        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });
        inputPanel.add(sendButton, BorderLayout.EAST);

        clientList = new JComboBox<>();
        inputPanel.add(clientList, BorderLayout.NORTH);

        panel.add(inputPanel, BorderLayout.SOUTH);

        add(panel);
    }

    private void setupServer() {
        new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
                System.out.println("Сервер запущен на порту " + SERVER_PORT);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Новый клиент подключен");
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    clients.add(clientHandler);
                    new Thread(clientHandler).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println(username);

            new Thread(() -> {
                String message;
                try {
                    while ((message = in.readLine()) != null) {
                        if (message.startsWith("[CLIENTLIST]")) {
                            updateClientList(message.substring("[CLIENTLIST]".length()));
                        } else {
                            chatArea.append(message + "\n");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            // Запрос списка клиентов
            out.println("[GETCLIENTLIST]");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String message = messageField.getText();
        if (!message.isEmpty()) {
            String selectedClient = (String) clientList.getSelectedItem();
            if (selectedClient != null) {
                out.println("[PRIVATE]" + selectedClient + ":" + message);
            } else {
                out.println(username + ": " + message);
            }
            messageField.setText("");
        }
    }

    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter clientOut;

        public ClientHandler(Socket socket, ChatApplication chatApp) {
            this.clientSocket = socket;
            try {
                this.clientOut = new PrintWriter(clientSocket.getOutputStream(), true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String clientUsername = clientIn.readLine();
                System.out.println("Клиент " + clientUsername + " подключен");

                String message;
                while ((message = clientIn.readLine()) != null) {
                    if (message.startsWith("[PRIVATE]")) {
                        // Приватное сообщение
                        chatArea.append(message.substring("[PRIVATE]".length()) + "\n");
                    } else if (message.equals("[GETCLIENTLIST]")) {
                        // Запрос списка клиентов
                        sendClientList();
                    } else {
                        // Общее сообщение
                        broadcastMessage(clientUsername + ": " + message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                removeClient(this);
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void sendMessage(String message) {
            clientOut.println(message);
        }
    }

    private void broadcastMessage(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    private void removeClient(ClientHandler clientHandler) {
        System.out.println("Клиент отключен");
        clients.remove(clientHandler);
        sendClientList();
    }

    private void sendClientList() {
        StringBuilder clientListStr = new StringBuilder("[CLIENTLIST]");
        for (ClientHandler client : clients) {
            clientListStr.append(client.toString()).append(",");
        }
        broadcastMessage(clientListStr.toString());
    }

    private void updateClientList(String clientListStr) {
        String[] clientsArray = clientListStr.split(",");
        clientList.removeAllItems();
        for (String client : clientsArray) {
            if (!client.isEmpty()) {
                clientList.addItem(client);
            }
        }
    }
}

