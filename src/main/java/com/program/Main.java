package com.program;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;
import java.util.Map;

public class Main {
    private JFrame frame;
    private JTextField urlField;
    private JTextField requestCountField;
    private JTextArea logArea;
    private JButton startButton, stopButton;
    private JComboBox<String> methodSelector;
    private JProgressBar progressBar;
    private ExecutorService executor;
    private ConcurrentHashMap<Integer, Integer> responseCounts = new ConcurrentHashMap<>();
    private ConcurrentLinkedQueue<Long> responseTimes = new ConcurrentLinkedQueue<>();
    private int totalRequests;
    private int completedRequests;
    private volatile boolean isRunning = false;

    public Main() {
        frame = new JFrame("BOBDOS");
        frame.setSize(400, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(6, 2));

        panel.add(new JLabel("URL:"));
        urlField = new JTextField("http://example.com");
        panel.add(urlField);

        panel.add(new JLabel("Method:"));
        methodSelector = new JComboBox<>(new String[]{"GET", "POST", "PUT", "DELETE"});
        panel.add(methodSelector);

        panel.add(new JLabel("Number of requests:"));
        requestCountField = new JTextField("100");
        panel.add(requestCountField);

        startButton = new JButton("START");
        panel.add(startButton);

        stopButton = new JButton("STOP");
        stopButton.setEnabled(false);
        panel.add(stopButton);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        panel.add(progressBar);

        logArea = new JTextArea();
        logArea.setEditable(false);

        frame.add(panel, BorderLayout.NORTH);
        frame.add(new JScrollPane(logArea), BorderLayout.CENTER);

        startButton.addActionListener(new StartButtonListener());
        stopButton.addActionListener(e -> stopTest());

        frame.setVisible(true);
    }

    private class StartButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String urlString = urlField.getText();
            String method = (String) methodSelector.getSelectedItem();
            try {
                totalRequests = Integer.parseInt(requestCountField.getText());
            } catch (NumberFormatException ex) {
                logArea.append("Enter a valid number\n");
                return;
            }

            isRunning = true;
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            executor = Executors.newFixedThreadPool(10);
            responseCounts.clear();
            responseTimes.clear();
            completedRequests = 0;
            logArea.append("Launching...\n");
            progressBar.setValue(0);

            for (int i = 0; i < totalRequests; i++) {
                executor.execute(() -> sendRequest(urlString, method));
            }

            executor.shutdown();
            new Thread(() -> {
                try {
                    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                    SwingUtilities.invokeLater(() -> {
                        logArea.append("Completed.\n");
                        displayAverageResponseTime();
                        startButton.setEnabled(true);
                        stopButton.setEnabled(false);
                    });
                } catch (InterruptedException ignored) {}
            }).start();
        }
    }

    private void sendRequest(String urlString, String method) {
        if (!isRunning) return;
        long startTime = System.currentTimeMillis();
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            if (method.equals("POST") || method.equals("PUT")) {
                conn.setDoOutput(true);
                conn.getOutputStream().write("".getBytes());
            }
            int responseCode = conn.getResponseCode();

            long endTime = System.currentTimeMillis();
            responseCounts.merge(responseCode, 1, Integer::sum);
            responseTimes.add(endTime - startTime);

            synchronized (this) {
                completedRequests++;
                int progress = (int) ((completedRequests / (double) totalRequests) * 100);
                SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
            }
            updateLog();
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> logArea.append("Request error: " + ex.getMessage() + "\n"));
        }
    }

    private void updateLog() {
        SwingUtilities.invokeLater(() -> {
            logArea.setText("Responses:\n");
            for (Map.Entry<Integer, Integer> entry : responseCounts.entrySet()) {
                logArea.append("Code " + entry.getKey() + ": " + entry.getValue() + " times\n");
            }
        });
    }

    private void displayAverageResponseTime() {
        long totalTime = responseTimes.stream().mapToLong(Long::longValue).sum();
        long avgTime = responseTimes.isEmpty() ? 0 : totalTime / responseTimes.size();
        logArea.append("Average response time: " + avgTime + " ms\n");
    }

    private void stopTest() {
        isRunning = false;
        if (executor != null) {
            executor.shutdownNow();
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        logArea.append("Stopped.\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::new);
    }
}
