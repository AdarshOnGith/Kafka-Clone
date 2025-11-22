import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * Minimal GUI Client for DMQ System
 * Wraps CLI commands for easy testing and visualization
 */
public class DMQGuiClient extends JFrame {
    
    // Try multiple possible locations for the CLI JAR
    private static final String[] CLI_JAR_PATHS = {
        "../target/mycli.jar",                           // Original location (if built separately)
        "../../dmq-client/target/mycli.jar",             // From GUI-Client to dmq-client
        System.getProperty("user.dir") + "/../target/mycli.jar"  // Relative to current directory
    };
    
    private static String CLI_JAR = null;
    
    static {
        // Find the first existing CLI JAR
        for (String path : CLI_JAR_PATHS) {
            File jarFile = new File(path);
            if (jarFile.exists()) {
                CLI_JAR = path;
                break;
            }
        }
        
        // Fallback to first path if none found
        if (CLI_JAR == null) {
            CLI_JAR = CLI_JAR_PATHS[0];
        }
    }
    
    // UI Components
    private JTabbedPane tabbedPane;
    private JTextArea outputArea;
    private JTextField metadataServiceUrl;
    private JCheckBox prettyModeCheckbox;
    private JButton getLeaderBtn;
    
    // Producer Tab
    private JTextField producerTopic;
    private JTextField producerKey;
    private JTextArea producerValue;
    private JTextField producerPartition;
    private JComboBox<String> producerAcks;
    private JButton produceSingleBtn;
    private JButton produceBatchBtn;
    
    // Consumer Tab
    private JTextField consumerTopic;
    private JTextField consumerPartition;
    private JTextField consumerOffset;
    private JTextField consumerMaxMessages;
    private JButton consumeBtn;
    
    // Topic Management Tab
    private JTextField topicName;
    private JTextField topicPartitions;
    private JTextField topicReplication;
    private JButton createTopicBtn;
    private JButton listTopicsBtn;
    private JButton describeTopicBtn;
    
    // Consumer Groups Tab
    private JTextField groupId;
    private JButton listGroupsBtn;
    private JButton describeGroupBtn;
    
    public DMQGuiClient() {
        setTitle("DMQ GUI Client - Enhanced");
        setSize(1200, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        initComponents();
        
        setVisible(true);
    }
    
    private void initComponents() {
        // Main layout
        setLayout(new BorderLayout(10, 10));
        
        // Top panel - Metadata Service URL and Controls
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(new EmptyBorder(10, 10, 5, 10));
        topPanel.add(new JLabel("Metadata Service URL:"));
        metadataServiceUrl = new JTextField("http://localhost:9091", 30);
        topPanel.add(metadataServiceUrl);
        
        // Pretty mode toggle
        prettyModeCheckbox = new JCheckBox("Pretty Mode", true);
        prettyModeCheckbox.setToolTipText("Show only essential output without decorations");
        topPanel.add(prettyModeCheckbox);
        
        // Get Leader button
        getLeaderBtn = new JButton("Get Raft Leader");
        getLeaderBtn.addActionListener(e -> getRaftLeader());
        topPanel.add(getLeaderBtn);
        
        add(topPanel, BorderLayout.NORTH);
        
        // Center - Tabbed Pane
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Producer", createProducerPanel());
        tabbedPane.addTab("Consumer", createConsumerPanel());
        tabbedPane.addTab("Topics", createTopicPanel());
        tabbedPane.addTab("Consumer Groups", createConsumerGroupsPanel());
        add(tabbedPane, BorderLayout.CENTER);
        
        // Bottom - Output Area (LARGER)
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new TitledBorder("Output"));
        outputArea = new JTextArea(20, 100);  // Increased from 10, 80
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setPreferredSize(new Dimension(1180, 350));  // Larger size
        bottomPanel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearBtn = new JButton("Clear Output");
        clearBtn.addActionListener(e -> outputArea.setText(""));
        controlPanel.add(clearBtn);
        bottomPanel.add(controlPanel, BorderLayout.SOUTH);
        
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createProducerPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Form Panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Topic
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Topic:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        producerTopic = new JTextField(20);
        formPanel.add(producerTopic, gbc);
        
        // Key
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Key:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        producerKey = new JTextField(20);
        formPanel.add(producerKey, gbc);
        
        // Value
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Value:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        producerValue = new JTextArea(5, 20);
        producerValue.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        formPanel.add(new JScrollPane(producerValue), gbc);
        
        // Partition
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0; gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(new JLabel("Partition (optional):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        producerPartition = new JTextField(20);
        producerPartition.setToolTipText("Leave empty for auto-selection");
        formPanel.add(producerPartition, gbc);
        
        // Acks
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Acks:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        producerAcks = new JComboBox<>(new String[]{"1 (Leader)", "0 (None)", "-1 (All ISR)"});
        formPanel.add(producerAcks, gbc);
        
        panel.add(formPanel, BorderLayout.CENTER);
        
        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        produceSingleBtn = new JButton("Produce Single Message");
        produceSingleBtn.addActionListener(e -> produceSingleMessage());
        buttonPanel.add(produceSingleBtn);
        
        produceBatchBtn = new JButton("Produce Batch (File)");
        produceBatchBtn.addActionListener(e -> produceBatchMessages());
        buttonPanel.add(produceBatchBtn);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createConsumerPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Form Panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Topic
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Topic:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        consumerTopic = new JTextField(20);
        formPanel.add(consumerTopic, gbc);
        
        // Partition
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Partition:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        consumerPartition = new JTextField("0", 20);
        formPanel.add(consumerPartition, gbc);
        
        // Offset
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Start Offset:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        consumerOffset = new JTextField("0", 20);
        formPanel.add(consumerOffset, gbc);
        
        // Max Messages
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Max Messages:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        consumerMaxMessages = new JTextField("10", 20);
        formPanel.add(consumerMaxMessages, gbc);
        
        panel.add(formPanel, BorderLayout.CENTER);
        
        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        consumeBtn = new JButton("Consume Messages");
        consumeBtn.addActionListener(e -> consumeMessages());
        buttonPanel.add(consumeBtn);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createTopicPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Form Panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Topic Name
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Topic Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        topicName = new JTextField(20);
        formPanel.add(topicName, gbc);
        
        // Partitions
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Partitions:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        topicPartitions = new JTextField("3", 20);
        formPanel.add(topicPartitions, gbc);
        
        // Replication Factor
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Replication Factor:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        topicReplication = new JTextField("2", 20);
        formPanel.add(topicReplication, gbc);
        
        panel.add(formPanel, BorderLayout.CENTER);
        
        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        createTopicBtn = new JButton("Create Topic");
        createTopicBtn.addActionListener(e -> createTopic());
        buttonPanel.add(createTopicBtn);
        
        listTopicsBtn = new JButton("List Topics");
        listTopicsBtn.addActionListener(e -> listTopics());
        buttonPanel.add(listTopicsBtn);
        
        describeTopicBtn = new JButton("Describe Topic");
        describeTopicBtn.addActionListener(e -> describeTopic());
        buttonPanel.add(describeTopicBtn);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createConsumerGroupsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Form Panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Group ID
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Group ID:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        groupId = new JTextField(20);
        formPanel.add(groupId, gbc);
        
        panel.add(formPanel, BorderLayout.CENTER);
        
        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        listGroupsBtn = new JButton("List All Consumer Groups");
        listGroupsBtn.addActionListener(e -> listConsumerGroups());
        buttonPanel.add(listGroupsBtn);
        
        describeGroupBtn = new JButton("Describe Consumer Group");
        describeGroupBtn.addActionListener(e -> describeConsumerGroup());
        buttonPanel.add(describeGroupBtn);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private void produceSingleMessage() {
        String topic = producerTopic.getText().trim();
        String key = producerKey.getText().trim();
        String value = producerValue.getText().trim();
        String partition = producerPartition.getText().trim();
        int acks = getSelectedAcks();
        
        if (topic.isEmpty() || value.isEmpty()) {
            showError("Topic and Value are required!");
            return;
        }
        
        StringBuilder cmd = new StringBuilder();
        cmd.append("produce --topic ").append(topic);
        
        if (!key.isEmpty()) {
            cmd.append(" --key \"").append(escapeQuotes(key)).append("\"");
        }
        
        cmd.append(" --value \"").append(escapeQuotes(value)).append("\"");
        
        if (!partition.isEmpty()) {
            cmd.append(" --partition ").append(partition);
        }
        
        cmd.append(" --acks ").append(acks);
        
        executeCliCommand(cmd.toString());
    }
    
    private void produceBatchMessages() {
        String topic = producerTopic.getText().trim();
        String partition = producerPartition.getText().trim();
        int acks = getSelectedAcks();
        
        if (topic.isEmpty()) {
            showError("Topic is required!");
            return;
        }
        
        // File chooser for batch file
        JFileChooser fileChooser = new JFileChooser(".");
        fileChooser.setDialogTitle("Select Batch Messages File");
        int result = fileChooser.showOpenDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            
            StringBuilder cmd = new StringBuilder();
            cmd.append("produce --topic ").append(topic);
            cmd.append(" --batch-file \"").append(file.getAbsolutePath()).append("\"");
            
            if (!partition.isEmpty()) {
                cmd.append(" --partition ").append(partition);
            }
            
            cmd.append(" --acks ").append(acks);
            
            executeCliCommand(cmd.toString());
        }
    }
    
    private void consumeMessages() {
        String topic = consumerTopic.getText().trim();
        String partition = consumerPartition.getText().trim();
        String offset = consumerOffset.getText().trim();
        String maxMessages = consumerMaxMessages.getText().trim();
        
        if (topic.isEmpty() || partition.isEmpty() || offset.isEmpty()) {
            showError("Topic, Partition, and Offset are required!");
            return;
        }
        
        StringBuilder cmd = new StringBuilder();
        cmd.append("consume --topic ").append(topic);
        cmd.append(" --partition ").append(partition);
        cmd.append(" --offset ").append(offset);
        cmd.append(" --max-messages ").append(maxMessages.isEmpty() ? "10" : maxMessages);
        
        executeCliCommand(cmd.toString());
    }
    
    private void createTopic() {
        String name = topicName.getText().trim();
        String partitions = topicPartitions.getText().trim();
        String replication = topicReplication.getText().trim();
        
        if (name.isEmpty() || partitions.isEmpty() || replication.isEmpty()) {
            showError("All fields are required!");
            return;
        }
        
        String cmd = String.format("create-topic --name %s --partitions %s --replication-factor %s",
                name, partitions, replication);
        
        executeCliCommand(cmd);
    }
    
    private void listTopics() {
        // Use CLI command
        executeCliCommand("list-topics");
    }
    
    private void describeTopic() {
        String name = topicName.getText().trim();
        
        if (name.isEmpty()) {
            showError("Topic name is required!");
            return;
        }
        
        // Use CLI command
        executeCliCommand("describe-topic --name " + name);
    }
    
    private void listConsumerGroups() {
        // Use CLI command
        executeCliCommand("list-groups");
    }
    
    private void describeConsumerGroup() {
        String group = groupId.getText().trim();
        
        if (group.isEmpty()) {
            showError("Group ID is required!");
            return;
        }
        
        // Use CLI command
        executeCliCommand("describe-group --group " + group);
    }
    
    private void getRaftLeader() {
        // Get metadata URL from text field
        String metadataUrl = metadataServiceUrl.getText().trim();
        if (metadataUrl.isEmpty()) {
            metadataUrl = "http://localhost:9091";
        }
        
        // Use CLI command with metadata URL
        executeCliCommand("get-leader --metadata-url " + metadataUrl);
    }
    
    private void executeCliCommand(String command) {
        displayCommandOutput("CLI Command: " + command, "");
        
        // Disable buttons during execution
        setButtonsEnabled(false);
        
        // Execute in background thread
        CompletableFuture.runAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("java", "-jar", CLI_JAR);
                
                // Add command arguments properly
                String[] args = parseCommandLine(command);
                for (String arg : args) {
                    pb.command().add(arg);
                }
                
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                // Read output
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
                );
                
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                
                int exitCode = process.waitFor();
                
                String result = output.toString();
                if (result.isEmpty()) {
                    result = "(No output)";
                }
                
                final String finalResult = result;
                final boolean success = (exitCode == 0);
                final int finalExitCode = exitCode;
                
                SwingUtilities.invokeLater(() -> {
                    displayResult(finalResult, success, finalExitCode);
                });
                
            } catch (Exception e) {
                final String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                SwingUtilities.invokeLater(() -> {
                    displayError("CLI Error: " + errorMsg);
                });
            } finally {
                SwingUtilities.invokeLater(() -> setButtonsEnabled(true));
            }
        });
    }
    
    private String[] parseCommandLine(String command) {
        // Parse command line respecting quotes
        java.util.List<String> args = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"([^\"]*)\"|([^\\s]+)").matcher(command);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                args.add(matcher.group(1)); // Quoted argument
            } else {
                args.add(matcher.group(2)); // Unquoted argument
            }
        }
        return args.toArray(new String[0]);
    }
    
    private void displayCommandOutput(String description, String command) {
        if (prettyModeCheckbox.isSelected()) {
            // Pretty mode - minimal output
            outputArea.append("\n> " + description + "\n");
        } else {
            // Full mode - with decorations
            outputArea.append("\n" + "=".repeat(80) + "\n");
            outputArea.append(description + "\n");
            if (!command.isEmpty()) {
                outputArea.append("Command: " + command + "\n");
            }
            outputArea.append("=".repeat(80) + "\n");
        }
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }
    
    private void displayResult(String result, boolean success, int code) {
        if (prettyModeCheckbox.isSelected()) {
            // Pretty mode - show only essential content
            String cleaned = cleanOutput(result);
            outputArea.append(cleaned + "\n");
        } else {
            // Full mode - show everything
            if (success) {
                outputArea.append("[OK] SUCCESS (Code: " + code + ")\n");
            } else {
                outputArea.append("[X] FAILED (Code: " + code + ")\n");
            }
            outputArea.append("-".repeat(80) + "\n");
            outputArea.append(result + "\n");
        }
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }
    
    private void displayError(String error) {
        if (prettyModeCheckbox.isSelected()) {
            outputArea.append("[X] ERROR: " + error + "\n");
        } else {
            outputArea.append("[X] ERROR\n");
            outputArea.append("-".repeat(80) + "\n");
            outputArea.append(error + "\n");
        }
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }
    
    private String cleanOutput(String output) {
        // Remove ANSI codes, emojis, and decorative elements for pretty mode
        String cleaned = output;
        
        // Remove ANSI escape codes
        cleaned = cleaned.replaceAll("\\u001B\\[[;\\d]*m", "");
        
        // Remove box drawing characters
        cleaned = cleaned.replaceAll("[═╔╚╗╝║]", "");
        
        // Remove unsupported Unicode characters that show as "?"
        cleaned = cleaned.replaceAll("[✓✗▶●ℹ⚠]", "");
        cleaned = cleaned.replaceAll("\\? ", "");
        
        // Remove leading/trailing whitespace from each line and filter logs
        String[] lines = cleaned.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            
            // Skip separator lines (3 or more consecutive - or =)
            if (trimmed.matches("^[-=]{3,}$")) {
                continue;
            }
            
            // Skip log lines (INFO, DEBUG, WARN, etc.)
            if (trimmed.matches("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+\\[.*\\]\\s+(INFO|DEBUG|WARN|ERROR|TRACE).*")) {
                continue;
            }
            
            // Skip common verbose messages
            if (trimmed.startsWith("Trying to load config") ||
                trimmed.startsWith("Loaded service configuration") ||
                trimmed.startsWith("Connected to controller")) {
                continue;
            }
            
            // Skip "Error: null" messages
            if (trimmed.equals("Error: null")) {
                continue;
            }
            
            if (!trimmed.isEmpty()) {
                result.append(trimmed).append("\n");
            }
        }
        
        return result.toString().trim();
    }
    
    private int getSelectedAcks() {
        String selected = (String) producerAcks.getSelectedItem();
        if (selected.startsWith("0")) return 0;
        if (selected.startsWith("-1")) return -1;
        return 1;
    }
    
    private void setButtonsEnabled(boolean enabled) {
        produceSingleBtn.setEnabled(enabled);
        produceBatchBtn.setEnabled(enabled);
        consumeBtn.setEnabled(enabled);
        createTopicBtn.setEnabled(enabled);
        listTopicsBtn.setEnabled(enabled);
        describeTopicBtn.setEnabled(enabled);
        listGroupsBtn.setEnabled(enabled);
        describeGroupBtn.setEnabled(enabled);
        getLeaderBtn.setEnabled(enabled);
    }
    
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
    
    private String escapeQuotes(String str) {
        return str.replace("\"", "\\\"");
    }
    
    public static void main(String[] args) {
        // Set look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Use default look and feel
        }
        
        SwingUtilities.invokeLater(() -> new DMQGuiClient());
    }
}
