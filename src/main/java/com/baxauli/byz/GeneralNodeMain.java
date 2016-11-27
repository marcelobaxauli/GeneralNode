package com.baxauli.byz;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import javax.xml.bind.DatatypeConverter;

/**
 *
 * Nó representando um General Bizantino. <br>
 * A ideia é que este nó seja executado de maneira autônoma em seu próprio
 * processo e se comunique com os nós lieutenants por meio de mensagens de rede.
 *
 * A estratégia para resolução do problema dos Generais Bizantinos será
 * utilizando mensagens assinadas digitalmente.
 *
 * Para a geração de keypairs/assinatura digital, pode-se utilizar o programa
 * autonomo KeyGenerator também provido por essa aplicação.
 *
 * Funcionamento: O nó general assina sua mensagem com a sua private key e os
 * nós recipientes podem verificar, através da public key do general, se a
 * mensagem recebida realmente foi originada e assinada pelo general e se foi
 * recebida de forma intacta. Cada nó deve possuir as public keys de todos os
 * outros nós, porém a private key de cada nó só pode ser acessada por ele
 * próprio.
 *
 * @author Marcelo Baxauli <mlb122@hotmail.com>
 *
 */
public class GeneralNodeMain {

    private static final String NODES_URL_FILENAME = "url_nodes" + File.separator + "url_nodes.properties";
    private static final int NUMBER_OF_LIEUTENANT = 5; // tem que bater com o número de nós no arquivo

    private List<LieutenantAddress> lieutenantAddresses = new ArrayList<LieutenantAddress>();
    private final KeyFactory keyFactory;
    private PrivateKey privateKey = null;
    private HonestyState honestyState = HonestyState.HONEST; // padrão

    private Scanner scanner = new Scanner(System.in);

    public GeneralNodeMain(String privateKeyFileName) throws FileNotFoundException, IOException, InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException {

        if (privateKeyFileName == null || privateKeyFileName.isEmpty()) {
            throw new IllegalArgumentException("Invalid private key");
        }

        FileInputStream privateKeyFile = new FileInputStream(privateKeyFileName);
        byte[] privateKeyBytes = new byte[privateKeyFile.available()];
        privateKeyFile.read(privateKeyBytes);
        privateKeyFile.close();

        keyFactory = KeyFactory.getInstance("DSA");

        this.privateKey = this.keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));

        System.out.println("private key bytes: " + Arrays.toString(privateKeyBytes));

    }

    public static void main(String[] args) throws Exception {
        // ignorando exceptions...

        // Usuário passa a private key deste nó (nome e path do arquivo) 
        // por command line argument
        if (args.length < 1) {
            System.out.println("Error: Private key file is missing from arguments.");
            System.exit(1);
        }

        // args[0] é o filename da privatekey do general
        GeneralNodeMain generalNode = new GeneralNodeMain(args[0]);
        generalNode.init();
        generalNode.run();
    }

    public void init() throws Exception {
        loadNodesUrl();
    }

    private void loadNodesUrl() throws Exception {

        // Os nós lieutenant são previamente cadastrados em arquivo properties (url e porta).
        // Para facilitar o desenvolvimento e focar no problema dos Generais em sí.
        Properties urlsProperties = new Properties();
        InputStream in = GeneralNodeMain.class.getClassLoader().getResourceAsStream(NODES_URL_FILENAME);
        urlsProperties.load(in);

        String lieutenant;
        String url;
        String port;
        for (int i = 1; i <= NUMBER_OF_LIEUTENANT; i++) {

            lieutenant = urlsProperties.getProperty("lieutenant" + i);
            url = lieutenant.split(":")[0];
            port = lieutenant.split(":")[1];

            this.lieutenantAddresses.add(new LieutenantAddress(url, port));
        }

    }

    private void run() throws Exception {
        // Sobe o nó General e espera comando do usuario

        String option = "6";
        do {
            System.out.println(String.format("==== General node is up and is %s!", honestyState));
            System.out.println("==== Select an option:");
            System.out.println("== 1. Test nodes connectivity");
            System.out.println("== 2. Send attack message");
            System.out.println("== 3. Send retreat message");
            System.out.println("== 4. Turn honest");
            System.out.println("== 5. Turn dishonest");
            System.out.println("== 6. Exit");
            System.out.print("== Option: ");
            option = scanner.nextLine();

            if (option.equals("1")) {
                reachOutNodes();
            } else if (option.equals("2")) {
                sendOrder("attack");
            } else if (option.equals("3")) {
                sendOrder("retreat");
            } else if (option.equals("4")) {
                this.honestyState = HonestyState.HONEST;
            } else if (option.equals("5")) {
                this.honestyState = HonestyState.DISHONEST;
            } else if (option.equals("6")) {
                System.out.print("\nBye.");
            } else {
                System.out.println(String.format("'%s' is not a valid option.", option));
                System.out.println("Try again.");
            }

            System.out.println();

        } while (!option.equals("6"));

    }

    private void reachOutNodes() throws IOException {

        // Verifica se os nós estão up
        
        System.out.println();

        Socket testSocket = null;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        Exception err = null;
        boolean successTest = false;
        for (LieutenantAddress lieutenantAddress : lieutenantAddresses) {

            try {
                testSocket = new Socket(lieutenantAddress.getUrl(), lieutenantAddress.getPort());
                testSocket.setSoTimeout(3000); // timeout de 3 segundos pra não bloquear indefinidamente

                outputStream = testSocket.getOutputStream();
                inputStream = testSocket.getInputStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                PrintWriter writer = new PrintWriter(outputStream);

                writer.println("test");
                writer.flush();

                String line = reader.readLine();

                if (line.equals("ack")) {
                    System.out.printf("%s is up\n", lieutenantAddress);
                    successTest = true;
                }

            } catch (Exception e) {

                err = e;

            } finally {

                if (!successTest) {
                    if (err != null) {
                        System.out.printf("%s is down [%s]\n", lieutenantAddress, err.getMessage());
                    } else {
                        System.out.printf("%s is down\n", lieutenantAddress);
                    }
                }

                successTest = false;
                err = null;

                if (testSocket != null) {
                    testSocket.close();
                }

            }

        }

    }

    private void sendOrder(String order) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, SignatureException, IOException {

        if (this.honestyState == HonestyState.HONEST) {

            // Gera a mensagem para a ordem correta
            sendHonest(order);

        } else if (this.honestyState == HonestyState.DISHONEST) {

            // Gera duas mensagens: uma pra ordem de attack e outra pra ordem de retreat
            // E manda essas assinaturas alternadamente para cada nó.
            sendDishonest(order);

        }

    }

    private void sendHonest(String order) throws IOException, SignatureException, InvalidKeyException, NoSuchAlgorithmException {

        // Assina a mensagem
        String message = newSignedMessage(order);

        System.out.println("Message: " + message);

        Socket socket = null;
        OutputStream outputStream = null;
        Exception err = null;
        boolean successSend = false;
        for (LieutenantAddress lieutenantAddress : lieutenantAddresses) {

            try {
                socket = new Socket(lieutenantAddress.getUrl(), lieutenantAddress.getPort());
                socket.setSoTimeout(3000); // timeout de 3 segundos pra não bloquear indefinidamente

                outputStream = socket.getOutputStream();

                PrintWriter writer = new PrintWriter(outputStream);

                // enviando mensagem
                writer.println(message);
                writer.flush();

                successSend = true;

            } catch (Exception e) {

                err = e;

            } finally {

                if (!successSend) {
                    if (err != null) {
                        System.out.printf("%s is down [%s]\n", lieutenantAddress, err.getMessage());
                    } else {
                        System.out.printf("%s is down\n", lieutenantAddress);
                    }
                }

                successSend = false;
                err = null;

                if (socket != null) {
                    socket.close();
                }

            }

        }

    }

    private void sendDishonest(String order) throws IOException, SignatureException, InvalidKeyException, NoSuchAlgorithmException {

        // Gera duas mensagems: de attack e de retreat. E envia aleatóriamente para os nós Lieutenants
        
        // Cria a mensagem de attack
        String attackMessage = newSignedMessage("attack");

        // Cria a mensagem de retreat
        String retreatMessage = newSignedMessage("retreat");

        System.out.println("Attack message: " + attackMessage);
        System.out.println("Retreat message: " + retreatMessage);

        Socket socket = null;
        OutputStream outputStream = null;
        Exception err = null;
        boolean successSend = false;
        for (int i = 0; i < lieutenantAddresses.size(); i++) {

            LieutenantAddress lieutenantAddress = lieutenantAddresses.get(i);
            
            try {
                socket = new Socket(lieutenantAddress.getUrl(), lieutenantAddress.getPort());
                socket.setSoTimeout(3000); // timeout de 3 segundos pra não bloquear indefinidamente

                outputStream = socket.getOutputStream();

                PrintWriter writer = new PrintWriter(outputStream);

                // enviando mensagem (alternando entre attack e retreat)
                if (i % 2 == 0) {
                    writer.println(attackMessage);                
                } else {
                    writer.println(retreatMessage);                      
                }

                writer.flush();

                successSend = true;

            } catch (Exception e) {

                err = e;

            } finally {

                if (!successSend) {
                    if (err != null) {
                        System.out.printf("%s is down [%s]\n", lieutenantAddress, err.getMessage());
                    } else {
                        System.out.printf("%s is down\n", lieutenantAddress);
                    }
                }

                successSend = false;
                err = null;

                if (socket != null) {
                    socket.close();
                }

            }

        }

    }

    private String newSignedMessage(String order) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
    
        // Cria e assina a mensagem. O assinatura tem um caráter aleatório> para cada
        // assinatura um novo 'hash' é gerado, mesmo se for com a mesma entrada. Isso é essencial
        // para o algoritmo, uma vez que preserva a propriedade de dificuldade de criação e reprodução
        // de mensagens falsas.
        
        Signature signatureHelper = Signature.getInstance("DSA");
        signatureHelper.initSign(this.privateKey);

        signatureHelper.update(order.getBytes());
        byte[] signature = signatureHelper.sign();

        System.out.println("signature: " + Arrays.toString(signature));

        // passa pra Base64 pra concatenar ao final da própria mensagem como texto.
        String signatureBase64 = DatatypeConverter.printBase64Binary(signature);

        String message = String.format("%s:general:%s", order, signatureBase64);

        return message;

    }

}
