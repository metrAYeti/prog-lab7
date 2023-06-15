package server;

import common.exceptions.ClosingSocketException;
import common.exceptions.ConnectionErrorException;
import common.exceptions.OpeningServerSocketException;
import common.utility.Outputer;
import server.utility.CommandManager;
import server.utility.ConnectionHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Runs the server.
 */
public class Server {
    private int port;
    private ServerSocket serverSocket;
    private CommandManager commandManager;
    private boolean isStopped;
    private ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    private Semaphore semaphore;

    public Server(int port, int maxClients, CommandManager commandManager) {
        this.port = port;
        this.commandManager = commandManager;
        this.semaphore = new Semaphore(maxClients);
    }

    /**
     * Begins server operation.
     */
    public void run() {
        try {
            openServerSocket();
            while (!isStopped()) {
                try {
                    acquireConnection();
                    if (isStopped()) throw new ConnectionErrorException();
                    Socket clientSocket = connectToClient();
                    cachedThreadPool.submit(new ConnectionHandler(this, clientSocket, commandManager));
                } catch (ConnectionErrorException exception) {
                    if (!isStopped()) {
                        Outputer.printerror("An error occurred while connecting to the client!");
                        App.logger.error("Произошла ошибка при соединении с клиентом!");
                    } else break;
                }
            }
            cachedThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            Outputer.println("The server operation is completed.");
        } catch (OpeningServerSocketException exception) {
            Outputer.printerror("The server cannot be started!");
            App.logger.fatal("Сервер не может быть запущен!");
        } catch (InterruptedException e) {
            Outputer.printerror("An error occurred when shutting down already connected clients!");
        }
    }

    /**
     * Acquire connection.
     */
    public void acquireConnection() {
        try {
            semaphore.acquire();
            App.logger.info("Permission for a new connection has been received.");
        } catch (InterruptedException exception) {
            Outputer.printerror("An error occurred while getting permission for a new connection!");
            App.logger.error("Произошла ошибка при получении разрешения на новое соединение!");
        }
    }

    /**
     * Release connection.
     */
    public void releaseConnection() {
        semaphore.release();
        App.logger.info("A connection break has been registered.");
    }

    /**
     * Finishes server operation.
     */
    public synchronized void stop() {
        try {
            App.logger.info("Завершение работы сервера...");
            if (serverSocket == null) throw new ClosingSocketException();
            isStopped = true;
            cachedThreadPool.shutdown();
            serverSocket.close();
            Outputer.println("Завершение работы с уже подключенными клиентами...");
            App.logger.info("Работа сервера завершена.");
        } catch (ClosingSocketException exception) {
            Outputer.printerror("Невозможно завершить работу еще не запущенного сервера!");
            App.logger.error("Невозможно завершить работу еще не запущенного сервера!");
        } catch (IOException exception) {
            Outputer.printerror("Произошла ошибка при завершении работы сервера!");
            Outputer.println("Завершение работы с уже подключенными клиентами...");
            App.logger.error("Произошла ошибка при завершении работы сервера!");
        }
    }

    /**
     * Checked stops of server.
     *
     * @return Status of server stop.
     */
    private synchronized boolean isStopped() {
        return isStopped;
    }

    /**
     * Open server socket.
     */
    private void openServerSocket() throws OpeningServerSocketException {
        try {
            App.logger.info("Starting the server...");
            serverSocket = new ServerSocket(port);
            App.logger.info("The server is running.");
        } catch (IllegalArgumentException exception) {
            Outputer.printerror("Port '" + port + "' is beyond the limits of possible values!");
            App.logger.fatal("port '" + port + "' находится за пределами возможных значений!");
            throw new OpeningServerSocketException();
        } catch (IOException exception) {
            Outputer.printerror("An error occurred while trying to use the port '" + port + "'!");
            App.logger.fatal("Произошла ошибка при попытке использовать порт '" + port + "'!");
            throw new OpeningServerSocketException();
        }
    }

    /**
     * Connecting to client.
     */
    private Socket connectToClient() throws ConnectionErrorException {
        try {
            Outputer.println("Listening to the port '" + port + "'...");
            App.logger.info("Прослушивание порта '" + port + "'...");
            Socket clientSocket = serverSocket.accept();
            Outputer.println("The connection with the client is established.");
            App.logger.info("Соединение с клиентом установлено.");
            return clientSocket;
        } catch (IOException exception) {
            throw new ConnectionErrorException();
        }
    }
}
